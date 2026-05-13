package ru.sashil.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.model.AccountingDocument;
import ru.sashil.model.LedgerEntry;
import ru.sashil.model.Order;
import ru.sashil.repository.AccountingDocumentRepository;
import ru.sashil.repository.LedgerEntryRepository;
import ru.sashil.repository.OrderRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingExternalSystem {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountingDocumentRepository accountingDocumentRepository;
    private final OrderRepository orderRepository;
    private final AccountingPdfService accountingPdfService;
    private final MinioDocumentStorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public String processPayload(String payload) {
        try {
            log.info("Processing accounting data: {}", payload);
            JsonNode node = objectMapper.readTree(payload);
            String orderNumber = node.get("orderNumber").asText();
            double total = node.get("total").asDouble();
            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found for accounting: " + orderNumber));

            LedgerEntry entry1 = LedgerEntry.builder()
                    .date(LocalDateTime.now())
                    .orderNumber(orderNumber)
                    .accountDebit("62")
                    .accountCredit("90")
                    .amount(total)
                    .build();
            ledgerEntryRepository.save(entry1);

            LedgerEntry entry2 = LedgerEntry.builder()
                    .date(LocalDateTime.now())
                    .orderNumber(orderNumber)
                    .accountDebit("90")
                    .accountCredit("41")
                    .amount(total)
                    .build();
            ledgerEntryRepository.save(entry2);

            saveDocument(order, "SHIPPING_DOCUMENT", "Документ на отгрузку", "shipping-document", accountingPdfService.renderShippingPdf(order));
            saveDocument(order, "WAYBILL", "Товарная накладная", "waybill", accountingPdfService.renderWaybillPdf(order));

            log.info("Accounting records created for order: {}", orderNumber);
            return "ACK: Processed";
        } catch (Exception e) {
            log.error("Failed to parse payload: {}", payload, e);
            return "ERROR: " + e.getMessage();
        }
    }

    private void saveDocument(Order order, String docType, String title, String filePrefix, byte[] pdfBytes) {
        LocalDateTime now = LocalDateTime.now();
        String objectKey = "documents/orders/" + order.getOrderNumber() + "/" + filePrefix + ".pdf";
        String fileName = filePrefix + "-" + order.getOrderNumber() + ".pdf";
        MinioDocumentStorageService.StoredDocument storedDocument = storageService.store(objectKey, "application/pdf", pdfBytes);

        AccountingDocument document = accountingDocumentRepository.findByOrderNumberAndDocType(order.getOrderNumber(), docType)
            .orElseGet(AccountingDocument::new);
        document.setOrderNumber(order.getOrderNumber());
        document.setDocType(docType);
        document.setTitle(title);
        document.setFileName(fileName);
        document.setStorageBucket(storedDocument.bucket());
        document.setObjectKey(storedDocument.objectKey());
        document.setContentType("application/pdf");
        document.setFileSize(storedDocument.size());
        document.setContent(title + " по заказу " + order.getOrderNumber());
        document.setCreatedAt(now);
        accountingDocumentRepository.save(document);
    }
}

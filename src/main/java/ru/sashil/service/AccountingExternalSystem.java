package ru.sashil.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.model.AccountingDocument;
import ru.sashil.model.LedgerEntry;
import ru.sashil.repository.AccountingDocumentRepository;
import ru.sashil.repository.LedgerEntryRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingExternalSystem {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountingDocumentRepository accountingDocumentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public String processPayload(String payload) {
        try {
            log.info("Processing accounting data: {}", payload);
            JsonNode node = objectMapper.readTree(payload);
            String orderNumber = node.get("orderNumber").asText();
            double total = node.get("total").asDouble();

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

            AccountingDocument doc = AccountingDocument.builder()
                    .orderNumber(orderNumber)
                    .docType("Invoice")
                    .content("Invoice for order " + orderNumber + " total " + total)
                    .createdAt(LocalDateTime.now())
                    .build();
            accountingDocumentRepository.save(doc);

            log.info("Accounting records created for order: {}", orderNumber);
            return "ACK: Processed";
        } catch (Exception e) {
            log.error("Failed to parse payload: {}", payload, e);
            return "ERROR: " + e.getMessage();
        }
    }
}

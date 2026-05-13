package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sashil.model.AccountingDocument;
import ru.sashil.service.AccountingDocumentFileService;

@RestController
@RequestMapping("/accounting/documents")
@RequiredArgsConstructor
public class AccountingDocumentController {

    private final AccountingDocumentFileService accountingDocumentFileService;

    @GetMapping("/{id}/view")
    public ResponseEntity<byte[]> view(@PathVariable Long id) {
        return buildResponse(id, false);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return buildResponse(id, true);
    }

    private ResponseEntity<byte[]> buildResponse(Long id, boolean attachment) {
        AccountingDocument document = accountingDocumentFileService.getDocument(id);
        byte[] content = accountingDocumentFileService.loadContent(document);
        String fileName = document.getFileName() != null ? document.getFileName() : document.getDocType() + "-" + document.getOrderNumber() + ".pdf";
        String contentType = document.getContentType() != null ? document.getContentType() : MediaType.APPLICATION_PDF_VALUE;

        ContentDisposition disposition = attachment
            ? ContentDisposition.attachment().filename(fileName).build()
            : ContentDisposition.inline().filename(fileName).build();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(MediaType.parseMediaType(contentType))
            .contentLength(content.length)
            .body(content);
    }
}

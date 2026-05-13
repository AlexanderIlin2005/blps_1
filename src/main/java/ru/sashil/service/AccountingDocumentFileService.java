package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sashil.model.AccountingDocument;
import ru.sashil.repository.AccountingDocumentRepository;

@Service
@RequiredArgsConstructor
public class AccountingDocumentFileService {

    private final AccountingDocumentRepository accountingDocumentRepository;
    private final MinioDocumentStorageService storageService;

    public AccountingDocument getDocument(Long id) {
        return accountingDocumentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Accounting document not found: " + id));
    }

    public byte[] loadContent(AccountingDocument document) {
        if (document.getStorageBucket() == null || document.getObjectKey() == null) {
            throw new RuntimeException("Document " + document.getId() + " is not backed by MinIO");
        }
        return storageService.load(document.getStorageBucket(), document.getObjectKey());
    }
}

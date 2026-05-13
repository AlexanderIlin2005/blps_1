package ru.sashil.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.model.AccountingDocument;

import java.util.List;
import java.util.Optional;

public interface AccountingDocumentRepository extends JpaRepository<AccountingDocument, Long> {
    List<AccountingDocument> findByOrderNumberOrderByCreatedAtDesc(String orderNumber);
    Optional<AccountingDocument> findByOrderNumberAndDocType(String orderNumber, String docType);
}

package ru.sashil.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.model.AccountingDocument;

public interface AccountingDocumentRepository extends JpaRepository<AccountingDocument, Long> {
}

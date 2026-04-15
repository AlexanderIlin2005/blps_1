package ru.sashil.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.model.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
}

package ru.sashil.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sashil.model.Privilege;
import java.util.Optional;

public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {
    Optional<Privilege> findByName(String name);
}

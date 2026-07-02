package hu.deposoft.webshop.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);
}

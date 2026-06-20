package com.alradno.banking.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
    List<AuditRecord> findTop100ByCorrelationIdOrderByOccurredAtDesc(String correlationId);
}

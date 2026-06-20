package com.alradno.banking.audit;

import com.alradno.banking.audit.AuditIngestService.DirectAuditRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audits")
public class AuditController {
    private final AuditRecordRepository records;
    private final AuditIngestService ingest;

    public AuditController(AuditRecordRepository records, AuditIngestService ingest) {
        this.records = records;
        this.ingest = ingest;
    }

    @GetMapping
    public List<AuditRecord> list(@RequestParam(required = false) String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return records.findTop100ByCorrelationIdOrderByOccurredAtDesc(correlationId);
        }
        return records.findAll().stream().limit(100).toList();
    }

    @PostMapping("/internal")
    public AuditRecord direct(@RequestBody DirectAuditRequest request) {
        return ingest.ingestDirect(request);
    }
}

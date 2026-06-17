package com.pfe.case_management.controller;

import com.pfe.case_management.dto.InterventionAuditPageDto;
import com.pfe.case_management.dto.InterventionAuditSummaryDto;
import com.pfe.case_management.service.InterventionAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Admin "Interventions Auditing" — case-centric operational audit cockpit for field
 * interventions. Read-only; distinct from Stock Audits.
 */
@RestController
@RequestMapping("/api/v1/admin/intervention-audits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InterventionAuditController {

    private final InterventionAuditService auditService;

    /**
     * Paginated intervention audits, sorted by last activity (newest first) by default.
     *
     * @param search      case reference / technician / STT / ZM / customer (contains, case-insensitive)
     * @param module      ACT | PLT | RES
     * @param status      current {@code CaseStatus} name
     * @param eventType   a {@code CaseTimelineEventType} the intervention must contain
     * @param slaStatus   ON_TIME | WARNING | BREACHED (or a raw SlaStatus name)
     * @param proofStatus UPLOADED | MISSING
     * @param governorate governorate filter (contains, case-insensitive)
     * @param technician  technician id or name
     * @param sort        finished | created | reference | (default: last activity)
     */
    @GetMapping
    public ResponseEntity<InterventionAuditPageDto> getAudits(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String slaStatus,
            @RequestParam(required = false) String proofStatus,
            @RequestParam(required = false) String governorate,
            @RequestParam(required = false) String technician,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ResponseEntity.ok(auditService.getAudits(
                search, module, status, eventType, slaStatus, proofStatus,
                governorate, technician, from, to, sort, page, size));
    }

    @GetMapping("/summary")
    public ResponseEntity<InterventionAuditSummaryDto> getSummary() {
        return ResponseEntity.ok(auditService.getSummary());
    }
}

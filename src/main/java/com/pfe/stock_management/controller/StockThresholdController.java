package com.pfe.stock_management.controller;

import com.pfe.stock_management.dto.CaseLimitResponse;
import com.pfe.stock_management.dto.StockThresholdRequest;
import com.pfe.stock_management.dto.StockThresholdResponse;
import com.pfe.stock_management.service.StockThresholdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock-thresholds")
@RequiredArgsConstructor
public class StockThresholdController {

    private final StockThresholdService thresholdService;

    /** ZM fetches all his configured thresholds. */
    @GetMapping("/my")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<List<StockThresholdResponse>> getMyThresholds() {
        return ResponseEntity.ok(thresholdService.getMyThresholds());
    }

    /**
     * Backend/internal use: returns enabled thresholds for a given ZM.
     * Accessible by ADMIN for auditing.
     */
    @GetMapping("/effective/{zmId}")
    @PreAuthorize("hasAnyRole('ZM','ADMIN')")
    public ResponseEntity<List<StockThresholdResponse>> getEffective(@PathVariable String zmId) {
        return ResponseEntity.ok(thresholdService.getEffectiveThresholds(zmId));
    }

    /** ZM creates or updates threshold for an equipment item (upsert by zmId+itemId). */
    @PostMapping
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<StockThresholdResponse> createOrUpdate(
            @Valid @RequestBody StockThresholdRequest request) {
        return ResponseEntity.ok(thresholdService.createOrUpdate(request));
    }

    /** ZM updates an existing threshold by id. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<StockThresholdResponse> update(
            @PathVariable String id,
            @Valid @RequestBody StockThresholdRequest request) {
        return ResponseEntity.ok(thresholdService.update(id, request));
    }

    /** ZM toggles alerting on/off for a specific threshold. */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<StockThresholdResponse> toggle(@PathVariable String id) {
        return ResponseEntity.ok(thresholdService.toggle(id));
    }

    /**
     * TECH/STT: returns per-item case usage limits configured by the ZM responsible for this case.
     * Used by the frontend to detect over-limit usage proactively before work submission.
     */
    @GetMapping("/effective-case-limits/{caseId}")
    @PreAuthorize("hasAnyRole('TECH','STT')")
    public ResponseEntity<List<CaseLimitResponse>> getEffectiveCaseLimits(@PathVariable String caseId) {
        return ResponseEntity.ok(thresholdService.getEffectiveCaseLimits(caseId));
    }
}

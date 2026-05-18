package com.pfe.stock_management.controller;

import com.pfe.stock_management.dto.ConfirmDpmRequest;
import com.pfe.stock_management.dto.CreateDpmRequest;
import com.pfe.stock_management.dto.ExternalRejectionRequest;
import com.pfe.stock_management.dto.RecordExternalApprovalRequest;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.service.DpmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/dpms")
@RequiredArgsConstructor
public class DpmController {

    private final DpmService dpmService;

    @PostMapping
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<DpmRequest> createDpm(@Valid @RequestBody CreateDpmRequest request) {
        return ResponseEntity.ok(dpmService.createDpm(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DpmRequest> getById(@PathVariable String id) {
        return ResponseEntity.ok(dpmService.getById(id));
    }

    /**
     * ZM records external Team Stock approval and schedules the pickup RDV.
     * WAITING_TEAM_STOCK_APPROVAL → RDV_SCHEDULED
     */
    @PatchMapping("/{id}/external-approval-and-rdv")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<DpmRequest> recordExternalApprovalAndRdv(
            @PathVariable String id,
            @Valid @RequestBody RecordExternalApprovalRequest request
    ) {
        return ResponseEntity.ok(dpmService.recordExternalApprovalAndRdv(id, request));
    }

    /**
     * ZM records that the external Team Stock rejected the request.
     * WAITING_TEAM_STOCK_APPROVAL → TEAM_STOCK_REJECTED
     */
    @PatchMapping("/{id}/external-rejection")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<DpmRequest> recordExternalRejection(
            @PathVariable String id,
            @RequestBody ExternalRejectionRequest request
    ) {
        return ResponseEntity.ok(dpmService.recordExternalRejection(id, request));
    }

    /**
     * STT confirms they will attend the scheduled RDV.
     * RDV_SCHEDULED → RDV_CONFIRMED_BY_STT
     */
    @PostMapping("/{id}/confirm-rdv")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<DpmRequest> confirmRdv(@PathVariable String id) {
        return ResponseEntity.ok(dpmService.confirmRdv(id));
    }

    /**
     * STT confirms material reception with per-item quantities.
     * RDV_CONFIRMED_BY_STT → MATERIAL_RECEIVED  (new flow)
     * PENDING_STT_CONFIRMATION → FULLY/PARTIALLY_CONFIRMED  (legacy flow)
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<DpmRequest> confirmDpm(
            @PathVariable String id,
            @Valid @RequestBody ConfirmDpmRequest request
    ) {
        return ResponseEntity.ok(dpmService.confirmDpm(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<DpmRequest> cancelDpm(@PathVariable String id) {
        return ResponseEntity.ok(dpmService.cancelDpm(id));
    }
}

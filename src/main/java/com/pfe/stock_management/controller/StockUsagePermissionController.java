package com.pfe.stock_management.controller;

import com.pfe.stock_management.dto.CreatePermissionRequest;
import com.pfe.stock_management.dto.PermissionRequestResponse;
import com.pfe.stock_management.dto.RejectPermissionRequest;
import com.pfe.stock_management.dto.ReviewPermissionRequest;
import com.pfe.stock_management.service.StockUsagePermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock-usage-permissions")
@RequiredArgsConstructor
public class StockUsagePermissionController {

    private final StockUsagePermissionService permissionService;

    /** TECH creates an over-limit usage permission request. */
    @PostMapping
    @PreAuthorize("hasAnyRole('TECH','STT')")
    public ResponseEntity<PermissionRequestResponse> create(
            @Valid @RequestBody CreatePermissionRequest request) {
        return ResponseEntity.ok(permissionService.createRequest(request));
    }

    /** STT sees all pending requests assigned to him. */
    @GetMapping("/my-pending")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<List<PermissionRequestResponse>> myPending() {
        return ResponseEntity.ok(permissionService.getMyPendingRequests());
    }

    /** TECH/STT/ZM can see requests related to a case (role access enforced). */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('TECH','STT','ZM','ADMIN')")
    public ResponseEntity<List<PermissionRequestResponse>> byCase(@PathVariable String caseId) {
        return ResponseEntity.ok(permissionService.getByCase(caseId));
    }

    /** TECH sees his own requests. */
    @GetMapping("/tech/my-requests")
    @PreAuthorize("hasAnyRole('TECH','STT')")
    public ResponseEntity<List<PermissionRequestResponse>> myRequests() {
        return ResponseEntity.ok(permissionService.getMyRequests());
    }

    /** STT approves (full or partial amount). */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<PermissionRequestResponse> approve(
            @PathVariable String id,
            @RequestBody ReviewPermissionRequest request) {
        return ResponseEntity.ok(permissionService.approve(id, request));
    }

    /** STT rejects with required reason. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<PermissionRequestResponse> reject(
            @PathVariable String id,
            @Valid @RequestBody RejectPermissionRequest request) {
        return ResponseEntity.ok(permissionService.reject(id, request));
    }
}

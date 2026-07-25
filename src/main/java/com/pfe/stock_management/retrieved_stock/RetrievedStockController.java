package com.pfe.stock_management.retrieved_stock;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The technician Retrieved-Stock → ZM collection workflow (PLT/RES). Declaration itself happens
 * through {@code CaseController}'s {@code /api/v1/cases/{id}/res-recovery} endpoint (unchanged URL,
 * now backed by {@link RetrievedStockService#recordRetrieval}); this controller covers reading the
 * technician/STT buckets, the second-phase proof upload, and the STT's direct collection confirmation
 * (the responsible STT collects; the ZM/Admin only track read-only).
 */
@RestController
@RequestMapping("/api/v1/stock/retrieved-stock")
@RequiredArgsConstructor
public class RetrievedStockController {

    private final RetrievedStockService retrievedStockService;
    private final UserRepository userRepository;

    // ── Technician ─────────────────────────────────────────────────────────────

    /** Technician's own pending-collection bucket ("Retrieved Stock awaiting ZM collection"). */
    @GetMapping("/tech/mine")
    @PreAuthorize("hasRole('TECH')")
    public List<RetrievedStockItemDto> getMine() {
        return retrievedStockService.getMine(currentUser().getId());
    }

    /** Second-phase proof photo upload for one already-declared item (owner-only). */
    @PostMapping("/{itemId}/proof")
    @PreAuthorize("hasRole('TECH')")
    public RetrievedStockItemDto uploadProof(
            @PathVariable String itemId,
            @RequestParam("file") MultipartFile file
    ) {
        return retrievedStockService.uploadProof(itemId, currentUser(), file);
    }

    // ── STT (the responsible collector) ─────────────────────────────────────────

    /** STT's pending-collection queue, grouped by technician (only their own technicians' items). */
    @GetMapping("/stt/pending")
    @PreAuthorize("hasRole('STT')")
    public List<TechnicianRetrievedStockGroupDto> getPendingForStt() {
        return retrievedStockService.getPendingForSttGroupedByTechnician(currentUser().getId());
    }

    /** STT Collected Stock — physically collected, not distributable, awaiting Material Return. */
    @GetMapping("/stt/collected")
    @PreAuthorize("hasRole('STT')")
    public List<RetrievedStockItemDto> getCollectedForStt() {
        return retrievedStockService.getCollectedForStt(currentUser().getId());
    }

    /** Collected items eligible to be added to a Material Return Request. */
    @GetMapping("/stt/return-eligible")
    @PreAuthorize("hasRole('STT')")
    public List<RetrievedStockItemDto> getReturnEligibleForStt() {
        return retrievedStockService.getReturnEligibleForStt(currentUser().getId());
    }

    /** The one atomic multi-select action: STT confirms physical collection of one or more items. */
    @PostMapping("/stt/confirm-collection")
    @PreAuthorize("hasRole('STT')")
    public ConfirmCollectionResponse confirmCollectionByStt(@Valid @RequestBody ConfirmCollectionRequest request) {
        return retrievedStockService.confirmCollectionByStt(currentUser(), request);
    }

    // ── ZM (read-only tracking — no collection action) ──────────────────────────

    /** ZM read-only tracking of retrieved & collected stock across their territory. */
    @GetMapping("/zm/tracking")
    @PreAuthorize("hasRole('ZM')")
    public List<RetrievedStockItemDto> getTrackingForZm() {
        return retrievedStockService.getTrackingForZm(currentUser().getId());
    }

    // ── Shared ─────────────────────────────────────────────────────────────────

    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('STT', 'ZM', 'ADMIN', 'TECH')")
    public List<RetrievedStockItemDto> getByCase(@PathVariable String caseId) {
        return retrievedStockService.getByCase(caseId);
    }

    // ── Admin ──────────────────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RetrievedStockItemDto> getAll() {
        return retrievedStockService.getAll();
    }

    private AppUser currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}

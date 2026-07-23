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
 * technician/ZM buckets, the second-phase proof upload, and the ZM's direct collection confirmation.
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

    // ── ZM ─────────────────────────────────────────────────────────────────────

    /** ZM's pending-collection queue, grouped by technician. */
    @GetMapping("/zm/pending")
    @PreAuthorize("hasRole('ZM')")
    public List<TechnicianRetrievedStockGroupDto> getPendingForZm() {
        return retrievedStockService.getPendingForZmGroupedByTechnician(currentUser().getId());
    }

    /** ZM's own collected-stock bucket (post-collection). */
    @GetMapping("/zm/collected")
    @PreAuthorize("hasRole('ZM')")
    public List<RetrievedStockItemDto> getCollectedForZm() {
        return retrievedStockService.getCollectedForZm(currentUser().getId());
    }

    /** The one atomic multi-select action: confirm physical collection of one or more items. */
    @PostMapping("/zm/confirm-collection")
    @PreAuthorize("hasRole('ZM')")
    public ConfirmCollectionResponse confirmCollection(@Valid @RequestBody ConfirmCollectionRequest request) {
        return retrievedStockService.confirmCollection(currentUser(), request);
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

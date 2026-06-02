package com.pfe.stock_management.material_return;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/returns")
@RequiredArgsConstructor
public class MaterialReturnController {

    private final MaterialReturnService returnService;

    // ── STT endpoints ──────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<MaterialReturnResponseDto> createReturn(
            @Valid @RequestBody CreateMaterialReturnDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.createReturn(request));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<List<MaterialReturnResponseDto>> getMyReturns() {
        return ResponseEntity.ok(returnService.getMyReturns());
    }

    // ── ZM endpoints ───────────────────────────────────────────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<List<MaterialReturnResponseDto>> getPending() {
        return ResponseEntity.ok(returnService.getPendingForZm());
    }

    @GetMapping("/team")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<List<MaterialReturnResponseDto>> getAllForZm() {
        return ResponseEntity.ok(returnService.getAllForZm());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<MaterialReturnResponseDto> approve(
            @PathVariable String id,
            @RequestBody(required = false) ReviewMaterialReturnDto body) {
        return ResponseEntity.ok(returnService.approveReturn(id, body));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<MaterialReturnResponseDto> reject(
            @PathVariable String id,
            @RequestBody ReviewMaterialReturnDto body) {
        return ResponseEntity.ok(returnService.rejectReturn(id, body));
    }

    // ── Shared endpoints ───────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STT', 'ZM', 'ADMIN')")
    public ResponseEntity<MaterialReturnResponseDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(returnService.getById(id));
    }

    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('STT', 'ZM', 'ADMIN')")
    public ResponseEntity<List<MaterialReturnResponseDto>> getByCase(@PathVariable String caseId) {
        return ResponseEntity.ok(returnService.getReturnsByCase(caseId));
    }

    // ── ADMIN endpoint ─────────────────────────────────────────────────────────

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ZM', 'ADMIN')")
    public ResponseEntity<List<MaterialReturnResponseDto>> getAll() {
        return ResponseEntity.ok(returnService.getAllReturns());
    }
}

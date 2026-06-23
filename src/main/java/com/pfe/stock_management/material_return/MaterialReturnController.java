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

    /** Returns ZMs with canCreateDpm=true that an STT can target for a return */
    @GetMapping("/eligible-zms")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<List<EligibleZmDto>> getEligibleZms() {
        return ResponseEntity.ok(returnService.getEligibleZms());
    }

    /** STT submits a new material return request */
    @PostMapping
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<MaterialReturnResponseDto> createReturn(
            @Valid @RequestBody CreateMaterialReturnDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.createReturn(request));
    }

    /** STT lists their own returns */
    @GetMapping({"/my", "/stt/my-returns"})
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<List<MaterialReturnResponseDto>> getMyReturns() {
        return ResponseEntity.ok(returnService.getMyReturns());
    }

    /** STT accepts or rejects a ZM-proposed handover schedule (single endpoint) */
    @PostMapping("/{id}/schedule-response")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<MaterialReturnResponseDto> scheduleResponse(
            @PathVariable String id,
            @RequestBody SttScheduleResponseDto body) {
        return ResponseEntity.ok(returnService.respondToSchedule(id, body));
    }

    /** STT explicitly accepts a proposed schedule — convenience alias for schedule-response */
    @PostMapping("/{id}/schedule/accept")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<MaterialReturnResponseDto> acceptSchedule(
            @PathVariable String id,
            @RequestBody(required = false) SttScheduleResponseDto body) {
        SttScheduleResponseDto req = new SttScheduleResponseDto();
        req.setAccepted(true);
        if (body != null && body.getComment() != null) req.setComment(body.getComment());
        return ResponseEntity.ok(returnService.respondToSchedule(id, req));
    }

    /** STT requests a new handover slot — convenience alias for schedule-response */
    @PostMapping("/{id}/schedule/reject")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<MaterialReturnResponseDto> rejectSchedule(
            @PathVariable String id,
            @RequestBody SttScheduleResponseDto body) {
        body.setAccepted(false);
        return ResponseEntity.ok(returnService.respondToSchedule(id, body));
    }

    // ── ZM endpoints ───────────────────────────────────────────────────────────

    /** ZM lists returns needing action (PENDING_ZM_REVIEW + RESCHEDULE_REQUESTED) */
    @GetMapping({"/pending", "/zm/pending"})
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<List<MaterialReturnResponseDto>> getPending() {
        return ResponseEntity.ok(returnService.getPendingForZm());
    }

    /** ZM lists all returns in their scope */
    @GetMapping({"/team", "/zm/my-returns"})
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<List<MaterialReturnResponseDto>> getAllForZm() {
        return ResponseEntity.ok(returnService.getAllForZm());
    }

    /** ZM proposes a handover date/time for a return */
    @PostMapping("/{id}/propose-handover")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<MaterialReturnResponseDto> proposeHandover(
            @PathVariable String id,
            @Valid @RequestBody ProposeHandoverDto body) {
        return ResponseEntity.ok(returnService.proposeHandover(id, body));
    }

    /** ZM rejects the return request itself */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<MaterialReturnResponseDto> reject(
            @PathVariable String id,
            @RequestBody ReviewMaterialReturnDto body) {
        return ResponseEntity.ok(returnService.rejectReturn(id, body));
    }

    /** ZM confirms physical receipt of returned material */
    @PostMapping("/{id}/mark-handed-over")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<MaterialReturnResponseDto> markHandedOver(
            @PathVariable String id,
            @RequestBody(required = false) MarkHandedOverDto body) {
        return ResponseEntity.ok(returnService.markHandedOver(id, body != null ? body : new MarkHandedOverDto()));
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

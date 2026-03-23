package com.pfe.Controller;

import com.pfe.Dto.SendTechInvitationRequest;
import com.pfe.Dto.TechInvitationValidationResponse;
import com.pfe.Dto.ZmTechDecisionRequest;
import com.pfe.Entity.TechInvitation;
import com.pfe.Service.TechInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tech-invitations")
public class TechInvitationController {

    private final TechInvitationService service;

    public TechInvitationController(TechInvitationService service) {
        this.service = service;
    }

    // =============================
    // PUBLIC
    // =============================

    @GetMapping("/validate")
    public ResponseEntity<TechInvitationValidationResponse> validateToken(
            @RequestParam String token
    ) {
        return ResponseEntity.ok(service.validateInvitationToken(token));
    }

    // =============================
    // STT
    // =============================

    @PostMapping("/stt/send")
    public ResponseEntity<String> sendInvitation(
            @Valid @RequestBody SendTechInvitationRequest request,
            Authentication authentication
    ) {
        service.sendInvitation(request.email(), authentication.getName());
        return ResponseEntity.ok("Invitation sent successfully");
    }

    @GetMapping("/stt/my-invitations")
    public ResponseEntity<List<TechInvitation>> getMyInvitations(Authentication authentication) {
        return ResponseEntity.ok(service.getSttInvitations(authentication.getName()));
    }

    // =============================
    // ZM
    // =============================

    @GetMapping("/zm/pending")
    public ResponseEntity<List<TechInvitation>> getPendingZmApprovals(Authentication authentication) {
        return ResponseEntity.ok(service.getPendingZmApprovals(authentication.getName()));
    }

    @PostMapping("/zm/{invitationId}/decision")
    public ResponseEntity<String> decideByZm(
            @PathVariable String invitationId,
            @Valid @RequestBody ZmTechDecisionRequest request,
            Authentication authentication
    ) {
        service.decideByZm(authentication.getName(), invitationId, request.approve());
        return ResponseEntity.ok(
                Boolean.TRUE.equals(request.approve())
                        ? "Technician approved successfully"
                        : "Technician rejected successfully"
        );
    }
}
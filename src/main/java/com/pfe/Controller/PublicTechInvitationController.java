package com.pfe.Controller;

import com.pfe.Dto.TechInvitationValidationResponse;
import com.pfe.Service.TechInvitationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
public class PublicTechInvitationController {

    private final TechInvitationService techInvitationService;

    public PublicTechInvitationController(TechInvitationService techInvitationService) {
        this.techInvitationService = techInvitationService;
    }

    @GetMapping("/tech-invitation/{token}")
    public ResponseEntity<TechInvitationValidationResponse> validateTechInvitation(
            @PathVariable String token
    ) {
        TechInvitationValidationResponse response =
                techInvitationService.validateInvitationToken(token);

        return ResponseEntity.ok(response);
    }
}
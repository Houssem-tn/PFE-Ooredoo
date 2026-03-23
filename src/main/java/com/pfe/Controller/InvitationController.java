package com.pfe.Controller;

import com.pfe.Service.TechInvitationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stt/invitations")
public class InvitationController {

    private final TechInvitationService service;

    public InvitationController(TechInvitationService service) {
        this.service = service;
    }

    @PostMapping
    public void invite(@RequestParam String email, Authentication auth) {
        service.sendInvitation(email, auth.getName());
    }
}
package com.pfe.Controller;

import com.pfe.Dto.*;
import com.pfe.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(service.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(service.login(req));
    }

    @PostMapping("/register-tech-by-invitation")
    public ResponseEntity<UserDTO> registerTechByInvitation(@Valid @RequestBody TechRegisterRequest req) {
        return ResponseEntity.ok(service.registerTechInvitation(req));
    }
}
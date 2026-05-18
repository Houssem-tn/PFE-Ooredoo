package com.pfe.MainPackage.Controller;

import com.pfe.MainPackage.Dto.*;
import com.pfe.MainPackage.Dto.*;
import com.pfe.MainPackage.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/register-profile-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> registerProfilePhoto(
            @RequestParam("email") String email,
            @RequestParam("photo") MultipartFile photo
    ) {
        service.uploadRegisterProfilePhoto(email, photo);
        return ResponseEntity.ok().build();
    }
}
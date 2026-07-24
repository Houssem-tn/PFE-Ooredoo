package com.pfe.MainPackage.Controller;

import com.pfe.MainPackage.Dto.*;
import com.pfe.MainPackage.Service.AuthService;
import com.pfe.password_reset.dto.ForgotPasswordRequest;
import com.pfe.password_reset.dto.GenericMessageResponse;
import com.pfe.password_reset.dto.ResetPasswordRequest;
import com.pfe.password_reset.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * Identical for existing/unknown/disabled/unapproved accounts — the response
     * must never let a caller distinguish these cases.
     */
    private static final String FORGOT_PASSWORD_GENERIC_MESSAGE =
            "If an account exists for this email, a password-reset link has been sent.";

    private static final String RESET_PASSWORD_SUCCESS_MESSAGE =
            "Your password has been reset successfully.";

    private final AuthService service;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService service, PasswordResetService passwordResetService) {
        this.service = service;
        this.passwordResetService = passwordResetService;
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

    /**
     * Always returns HTTP 200 with the same generic body — for existing,
     * unknown, disabled and unapproved accounts alike — so the endpoint never
     * discloses whether an account exists. The client IP (resolved via the
     * framework's forwarded-headers handling, not a raw Host header) and a
     * truncated User-Agent are passed through for rate limiting and safe audit.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<GenericMessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req,
            HttpServletRequest httpRequest
    ) {
        passwordResetService.forgotPassword(
                req.email(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        return ResponseEntity.ok(new GenericMessageResponse(FORGOT_PASSWORD_GENERIC_MESSAGE));
    }

    /**
     * Applies a new password from a valid reset token. Does NOT authenticate the
     * user afterwards — the client sends the user to the login page. Invalid /
     * expired / used / revoked / unknown tokens all surface the same generic
     * "invalid or expired reset link" error (mapped to 400 by the global handler).
     */
    @PostMapping("/reset-password")
    public ResponseEntity<GenericMessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req,
            HttpServletRequest httpRequest
    ) {
        passwordResetService.resetPassword(req, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(new GenericMessageResponse(RESET_PASSWORD_SUCCESS_MESSAGE));
    }
}

package com.pfe.Service;

import com.pfe.Dto.*;
import com.pfe.Entity.*;
import com.pfe.Repository.UserRepository;
import com.pfe.Security.JwtService;
import com.pfe.mapper.UserMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class AuthService {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final NotificationEventService notificationEventService;

    private static final Set<UserRole> ALLOWED_PUBLIC_REQUESTED_ROLES =
            EnumSet.of(UserRole.ROLE_TECH, UserRole.ROLE_STT, UserRole.ROLE_ZM);

    public AuthService(UserRepository repo,
                       PasswordEncoder encoder,
                       AuthenticationManager authManager,
                       JwtService jwtService,
                       UserMapper userMapper,
                       NotificationEventService notificationEventService) {
        this.repo = repo;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.notificationEventService = notificationEventService;
    }

    public UserDTO register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        String firstName = req.firstName().trim();
        String lastName = req.lastName().trim();
        String fullName = firstName + " " + lastName;
        String phone = (req.phone() != null && !req.phone().isBlank()) ? req.phone().trim() : null;

        if (repo.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already used");
        }

        UserRole requested = (req.role() != null) ? req.role() : UserRole.ROLE_TECH;

        if (!ALLOWED_PUBLIC_REQUESTED_ROLES.contains(requested)) {
            throw new IllegalArgumentException("Role not allowed for public registration");
        }

        String assignedSttId = null;
        String assignedSttEmail = null;

        if (requested == UserRole.ROLE_TECH) {
            if (req.sttId() == null || req.sttId().isBlank()) {
                throw new IllegalArgumentException("STT selection is required for TECH registration");
            }

            AppUser selectedStt = repo.findByIdAndRoleAndStatusAndEnabled(
                    req.sttId().trim(),
                    UserRole.ROLE_STT,
                    AccountStatus.APPROVED,
                    true
            ).orElseThrow(() -> new IllegalArgumentException("Selected STT is invalid"));

            assignedSttId = selectedStt.getId();
            assignedSttEmail = selectedStt.getEmail();
        }

        AppUser user = AppUser.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .password(encoder.encode(req.password()))
                .role(null)
                .roleRequested(requested)
                .assignedSttId(assignedSttId)
                .assignedSttEmail(assignedSttEmail)
                .status(AccountStatus.PENDING)
                .enabled(false)
                .build();

        AppUser savedUser = repo.save(user);

        notificationEventService.onUserRegistered(savedUser);

        return userMapper.toDto(savedUser);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();

        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, req.password())
            );
        } catch (DisabledException e) {
            throw new IllegalStateException("Account pending approval");
        } catch (BadCredentialsException e) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        AppUser user = repo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (user.getStatus() != AccountStatus.APPROVED || !user.isEnabled() || user.getRole() == null) {
            throw new IllegalStateException("Account pending approval");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(token, "Bearer", user.getRole(), userMapper.toDto(user));
    }
}
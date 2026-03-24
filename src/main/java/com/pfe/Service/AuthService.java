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
import com.pfe.Repository.TechInvitationRepository;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
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
    private final TechInvitationRepository invitationRepo;
    private final FileStorageService fileStorageService;

    private static final Set<UserRole> ALLOWED_PUBLIC_REQUESTED_ROLES =
            EnumSet.of(UserRole.ROLE_STT, UserRole.ROLE_ZM);

    public AuthService(UserRepository repo,
                       PasswordEncoder encoder,
                       AuthenticationManager authManager,
                       JwtService jwtService,
                       UserMapper userMapper,
                       NotificationEventService notificationEventService,
                       TechInvitationRepository invitationRepo,
                       FileStorageService fileStorageService) {
        this.repo = repo;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.notificationEventService = notificationEventService;
        this.invitationRepo = invitationRepo;
        this.fileStorageService = fileStorageService;
    }

    public UserDTO register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        String firstName = req.firstName().trim();
        String lastName = req.lastName().trim();
        String fullName = firstName + " " + lastName;
        String phone = (req.phone() != null && !req.phone().isBlank())
                ? req.phone().trim()
                : null;

        if (repo.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already used");
        }

        UserRole requested = (req.role() != null) ? req.role() : null;

        if (requested == null) {
            throw new IllegalArgumentException("Role is required");
        }

        if (!ALLOWED_PUBLIC_REQUESTED_ROLES.contains(requested)) {
            throw new IllegalArgumentException("Role not allowed for public registration");
        }

        AppUser user = AppUser.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .password(encoder.encode(req.password()))
                .role(null)
                .roleRequested(requested)
                .assignedSttId(null)
                .assignedSttEmail(null)
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

        if (user.getStatus() != AccountStatus.APPROVED) {
            throw new IllegalStateException("Account pending approval");
        }
        if (!user.isEnabled()) {
            throw new IllegalStateException("Account disabled");
        }
        if (user.getRole() == null) {
            throw new IllegalStateException("Account role not assigned");
        }

        user.setLastLoginAt(Instant.now());
        AppUser savedUser = repo.save(user);

        String token = jwtService.generateToken(savedUser.getEmail(), savedUser.getRole());
        return new AuthResponse(token, "Bearer", savedUser.getRole(), userMapper.toDto(savedUser));
    }

    public UserDTO registerTechInvitation(TechRegisterRequest req) {
        String token = req.token().trim();
        String firstName = req.firstName().trim();
        String lastName = req.lastName().trim();
        String fullName = firstName + " " + lastName;
        String phone = (req.phone() != null && !req.phone().isBlank()) ? req.phone().trim() : null;

        TechInvitation invitation = invitationRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation"));

        if (invitation.isUsed()) {
            throw new IllegalStateException("Invitation already used");
        }

        if (invitation.getExpiresAt() == null || invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(TechInvitationStatus.EXPIRED);
            invitationRepo.save(invitation);
            throw new IllegalStateException("Invitation expired");
        }

        if (invitation.getStatus() != TechInvitationStatus.PENDING_REGISTRATION) {
            throw new IllegalStateException("Invitation is not valid for registration");
        }

        String invitedEmail = invitation.getEmail().trim().toLowerCase();

        if (repo.existsByEmail(invitedEmail)) {
            throw new IllegalArgumentException("Email already used");
        }

        AppUser stt = repo.findById(invitation.getSttId())
                .orElseThrow(() -> new IllegalStateException("Assigned STT not found"));

        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new IllegalStateException("Assigned user is not an STT");
        }

        if (stt.getStatus() != AccountStatus.APPROVED || !stt.isEnabled()) {
            throw new IllegalStateException("Assigned STT is not active");
        }

        if (stt.getAssignedZmId() == null || stt.getAssignedZmId().isBlank()) {
            throw new IllegalStateException("Assigned STT is not linked to any ZM");
        }

        if (stt.getAssignedZmEmail() == null || stt.getAssignedZmEmail().isBlank()) {
            throw new IllegalStateException("Assigned STT has no responsible ZM");
        }

        AppUser user = AppUser.builder()
                .fullName(fullName)
                .email(invitedEmail)
                .phone(phone)
                .password(encoder.encode(req.password()))
                .role(null)
                .roleRequested(UserRole.ROLE_TECH)
                .assignedSttId(stt.getId())
                .assignedSttEmail(stt.getEmail())
                .assignedZmId(stt.getAssignedZmId())
                .assignedZmEmail(stt.getAssignedZmEmail())
                .status(AccountStatus.PENDING)
                .enabled(false)
                .build();

        AppUser saved = repo.save(user);

        invitation.setUsed(true);
        invitation.setRegisteredTechId(saved.getId());
        invitation.setRegisteredAt(Instant.now());
        invitation.setStatus(TechInvitationStatus.REGISTERED_PENDING_ZM_APPROVAL);

        invitationRepo.save(invitation);

        notificationEventService.onUserRegistered(saved);

        return userMapper.toDto(saved);
    }

    public void uploadRegisterProfilePhoto(String email, MultipartFile photo) {
        try {
            String normalizedEmail = email == null ? "" : email.trim().toLowerCase();

            if (normalizedEmail.isBlank()) {
                throw new IllegalArgumentException("Email is required");
            }

            if (photo == null || photo.isEmpty()) {
                throw new IllegalArgumentException("Photo is required");
            }

            AppUser user = repo.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            FileMetadata metadata = fileStorageService.upload(
                    photo,
                    FileEntityType.USER,
                    user.getId(),
                    FileRole.PROFILE_IMAGE,
                    normalizedEmail
            );

            user.setProfileImageFileId(metadata.getId());
            user.setProfileImageUrl(fileStorageService.buildViewUrl(metadata.getId()));
            user.setProfileThumbnailUrl(fileStorageService.buildViewUrl(metadata.getId()));

            repo.save(user);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
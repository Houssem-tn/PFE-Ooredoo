package com.pfe.Service;

import com.pfe.Dto.TechInvitationValidationResponse;
import com.pfe.Entity.*;
import com.pfe.Repository.TechInvitationRepository;
import com.pfe.Repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TechInvitationService {

    private final TechInvitationRepository repo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    public TechInvitationService(
            TechInvitationRepository repo,
            UserRepository userRepo,
            EmailService emailService
    ) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.emailService = emailService;
    }

    // =============================
    // 🔹 STT SEND INVITATION
    // =============================
    public void sendInvitation(String email, String sttEmail) {
        String normalizedEmail = email.toLowerCase().trim();

        AppUser stt = userRepo.findByEmail(sttEmail)
                .orElseThrow(() -> new IllegalArgumentException("STT not found"));

        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new IllegalStateException("Only STT can invite technicians");
        }

        if (stt.getAssignedZmId() == null || stt.getAssignedZmId().isBlank()) {
            throw new IllegalStateException("STT is not assigned to a ZM");
        }

        userRepo.findByEmail(normalizedEmail).ifPresent(user -> {
            throw new IllegalStateException("A user with this email already exists");
        });

        Optional<TechInvitation> existingInvitation =
                repo.findFirstByEmailAndUsedFalseAndExpiresAtAfter(normalizedEmail, Instant.now());

        if (existingInvitation.isPresent()) {
            throw new IllegalStateException("An active invitation already exists for this email");
        }

        AppUser zm = userRepo.findById(stt.getAssignedZmId())
                .orElseThrow(() -> new IllegalStateException("Assigned ZM not found"));

        Instant now = Instant.now();
        String token = UUID.randomUUID().toString();

        TechInvitation invitation = TechInvitation.builder()
                .email(normalizedEmail)
                .sttId(stt.getId())
                .sttEmail(stt.getEmail())
                .sttName(stt.getFullName())
                .zmId(zm.getId())
                .zmEmail(zm.getEmail())
                .zmName(zm.getFullName())
                .token(token)
                .used(false)
                .createdAt(now)
                .expiresAt(now.plus(7, ChronoUnit.DAYS))
                .status(TechInvitationStatus.PENDING_REGISTRATION)
                .build();

        repo.save(invitation);

        String link = "http://localhost:4200/register-tech?token=" + token;

        emailService.send(
                normalizedEmail,
                "Technician invitation",
                "You have been invited by STT " + stt.getFullName() +
                        ". Click here to register:\n\n" + link
        );
    }

    // =============================
    // 🔹 VALIDATE TOKEN
    // =============================
    public TechInvitationValidationResponse validateInvitationToken(String token) {

        if (token == null || token.isBlank()) {
            return TechInvitationValidationResponse.builder()
                    .valid(false)
                    .expired(false)
                    .used(false)
                    .message("Invitation token is missing")
                    .build();
        }

        Optional<TechInvitation> optionalInvitation = repo.findByToken(token);

        if (optionalInvitation.isEmpty()) {
            return TechInvitationValidationResponse.builder()
                    .valid(false)
                    .expired(false)
                    .used(false)
                    .message("Invalid invitation token")
                    .build();
        }

        TechInvitation invitation = optionalInvitation.get();

        boolean isUsed = invitation.isUsed();
        boolean isExpired = invitation.getExpiresAt() != null
                && invitation.getExpiresAt().isBefore(Instant.now());

        if (isUsed) {
            return TechInvitationValidationResponse.builder()
                    .valid(false)
                    .expired(false)
                    .used(true)
                    .message("Invitation token has already been used")
                    .build();
        }

        if (isExpired) {
            if (invitation.getStatus() == TechInvitationStatus.PENDING_REGISTRATION) {
                invitation.setStatus(TechInvitationStatus.EXPIRED);
                repo.save(invitation);
            }

            return TechInvitationValidationResponse.builder()
                    .valid(false)
                    .expired(true)
                    .used(false)
                    .message("Invitation token has expired")
                    .build();
        }

        if (invitation.getStatus() != TechInvitationStatus.PENDING_REGISTRATION) {
            return TechInvitationValidationResponse.builder()
                    .valid(false)
                    .expired(false)
                    .used(invitation.isUsed())
                    .message("Invitation is no longer valid for registration")
                    .build();
        }

        return TechInvitationValidationResponse.builder()
                .valid(true)
                .expired(false)
                .used(false)
                .message("Invitation token is valid")
                .email(invitation.getEmail())
                .sttId(invitation.getSttId())
                .sttName(invitation.getSttName())
                .build();
    }

    // =============================
    // 🔹 STT VIEW HIS INVITATIONS
    // =============================
    public List<TechInvitation> getSttInvitations(String sttEmail) {
        AppUser stt = userRepo.findByEmail(sttEmail)
                .orElseThrow(() -> new IllegalArgumentException("STT not found"));

        return repo.findBySttIdOrderByCreatedAtDesc(stt.getId());
    }

    // =============================
    // 🔹 ZM VIEW PENDING APPROVALS
    // =============================
    public List<TechInvitation> getPendingZmApprovals(String zmEmail) {
        AppUser zm = userRepo.findByEmail(zmEmail)
                .orElseThrow(() -> new IllegalArgumentException("ZM not found"));

        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new IllegalStateException("Only ZM can view approvals");
        }

        return repo.findByZmIdAndStatusOrderByCreatedAtDesc(
                zm.getId(),
                TechInvitationStatus.REGISTERED_PENDING_ZM_APPROVAL
        );
    }

    // =============================
    // 🔹 ZM APPROVE / REJECT
    // =============================
    public void decideByZm(String zmEmail, String invitationId, boolean approve) {

        AppUser zm = userRepo.findByEmail(zmEmail)
                .orElseThrow(() -> new IllegalArgumentException("ZM not found"));

        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new IllegalStateException("Only ZM can approve/reject");
        }

        TechInvitation invitation = repo.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!zm.getId().equals(invitation.getZmId())) {
            throw new IllegalStateException("Not your scope");
        }

        if (invitation.getStatus() != TechInvitationStatus.REGISTERED_PENDING_ZM_APPROVAL) {
            throw new IllegalStateException("Invalid state for approval");
        }

        if (invitation.getRegisteredTechId() == null) {
            throw new IllegalStateException("No registered technician found");
        }

        AppUser tech = userRepo.findById(invitation.getRegisteredTechId())
                .orElseThrow(() -> new IllegalStateException("Tech not found"));

        if (approve) {
            if (tech.getRoleRequested() != UserRole.ROLE_TECH) {
                throw new IllegalStateException("Registered user is not requesting TECH role");
            }

            tech.setRole(tech.getRoleRequested()); // or tech.setRole(tech.getRoleRequested());
            tech.setStatus(AccountStatus.APPROVED);
            tech.setEnabled(true);
            tech.setValidatedAt(Instant.now());
            tech.setValidatedBy(zm.getEmail());

            invitation.setStatus(TechInvitationStatus.APPROVED_BY_ZM);
        } else {
            tech.setRole(null);
            tech.setStatus(AccountStatus.REJECTED);
            tech.setEnabled(false);
            tech.setValidatedAt(Instant.now());
            tech.setValidatedBy(zm.getEmail());

            invitation.setStatus(TechInvitationStatus.REJECTED_BY_ZM);
        }

        userRepo.save(tech);

        invitation.setZmDecisionAt(Instant.now());
        repo.save(invitation);
    }
}
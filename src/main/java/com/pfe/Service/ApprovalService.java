package com.pfe.Service;

import com.pfe.Entity.*;
import com.pfe.Repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ApprovalService {

    private final UserRepository userRepo;

    public ApprovalService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public List<AppUser> listMyPending(String approverEmail, UserRole approverRole) {
        if (approverRole == UserRole.ROLE_ADMIN) {
            return userRepo.findByStatus(AccountStatus.PENDING);
        }

        if (approverRole == UserRole.ROLE_ZM) {
            return userRepo.findByStatusAndRoleRequested(AccountStatus.PENDING, UserRole.ROLE_STT);
        }

        if (approverRole == UserRole.ROLE_STT) {
            AppUser approver = loadApprovedUserByEmail(approverEmail);

            return userRepo.findByStatusAndRoleRequestedAndAssignedSttId(
                    AccountStatus.PENDING,
                    UserRole.ROLE_TECH,
                    approver.getId()
            );
        }

        throw new AccessDeniedException("You are not allowed to view approvals");
    }

    public AppUser approve(String userId, String approverEmail, UserRole approverRole) {
        AppUser u = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (u.getStatus() != AccountStatus.PENDING) {
            throw new IllegalStateException("User is not pending");
        }

        UserRole requested = u.getRoleRequested();
        if (requested == null) {
            throw new IllegalStateException("User has no requested role");
        }

        if (!isAllowed(approverEmail, approverRole, u)) {
            throw new AccessDeniedException("Not allowed to approve role: " + requested);
        }

        u.setRole(requested);
        u.setStatus(AccountStatus.APPROVED);
        u.setEnabled(true);
        u.setValidatedAt(Instant.now());
        u.setValidatedBy(approverEmail);
        u.setRejectionReason(null);

        return userRepo.save(u);
    }

    public AppUser reject(String userId, String approverEmail, UserRole approverRole, String reason) {
        AppUser u = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (u.getStatus() != AccountStatus.PENDING) {
            throw new IllegalStateException("User is not pending");
        }

        UserRole requested = u.getRoleRequested();
        if (requested == null) {
            throw new IllegalStateException("User has no requested role");
        }

        if (!isAllowed(approverEmail, approverRole, u)) {
            throw new AccessDeniedException("Not allowed to reject role: " + requested);
        }

        u.setStatus(AccountStatus.REJECTED);
        u.setEnabled(false);
        u.setValidatedAt(Instant.now());
        u.setValidatedBy(approverEmail);
        u.setRejectionReason(reason);

        return userRepo.save(u);
    }

    private boolean isAllowed(String approverEmail, UserRole approverRole, AppUser targetUser) {
        if (approverRole == UserRole.ROLE_ADMIN) {
            return true;
        }

        if (approverRole == UserRole.ROLE_ZM) {
            return targetUser.getRoleRequested() == UserRole.ROLE_STT;
        }

        if (approverRole == UserRole.ROLE_STT) {
            AppUser approver = loadApprovedUserByEmail(approverEmail);

            return targetUser.getRoleRequested() == UserRole.ROLE_TECH
                    && targetUser.getAssignedSttId() != null
                    && targetUser.getAssignedSttId().equals(approver.getId());
        }

        return false;
    }

    private AppUser loadApprovedUserByEmail(String email) {
        AppUser user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Approver not found"));

        if (user.getStatus() != AccountStatus.APPROVED || !user.isEnabled() || user.getRole() == null) {
            throw new AccessDeniedException("Approver account is not active");
        }

        return user;
    }
}
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

    /**
     * ✅ Recommended:
     * - ADMIN sees all pending
     * - ZM sees only pending STT registrations
     * - STT sees only pending TECH registrations
     */
    public List<AppUser> listMyPending(UserRole approverRole) {
        if (approverRole == UserRole.ROLE_ADMIN) {
            return userRepo.findByStatus(AccountStatus.PENDING);
        }
        if (approverRole == UserRole.ROLE_ZM) {
            return userRepo.findByStatusAndRoleRequested(AccountStatus.PENDING, UserRole.ROLE_STT);
        }
        if (approverRole == UserRole.ROLE_STT) {
            return userRepo.findByStatusAndRoleRequested(AccountStatus.PENDING, UserRole.ROLE_TECH);
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

        if (!isAllowed(approverRole, requested)) {
            throw new AccessDeniedException("Not allowed to approve role: " + requested);
        }

        // ✅ Approve: set final role and enable login
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

        if (!isAllowed(approverRole, requested)) {
            throw new AccessDeniedException("Not allowed to reject role: " + requested);
        }

        // ✅ Reject: keep disabled
        u.setStatus(AccountStatus.REJECTED);
        u.setEnabled(false);
        u.setValidatedAt(Instant.now());
        u.setValidatedBy(approverEmail);
        u.setRejectionReason(reason);

        return userRepo.save(u);
    }

    /**
     * ✅ Approval chain:
     * - ADMIN approves الجميع
     * - ZM approves STT
     * - STT approves TECH
     */
    private boolean isAllowed(UserRole approverRole, UserRole requestedRole) {
        if (approverRole == UserRole.ROLE_ADMIN) return true;
        if (approverRole == UserRole.ROLE_ZM) return requestedRole == UserRole.ROLE_STT;
        if (approverRole == UserRole.ROLE_STT) return requestedRole == UserRole.ROLE_TECH;
        return false;
    }
}
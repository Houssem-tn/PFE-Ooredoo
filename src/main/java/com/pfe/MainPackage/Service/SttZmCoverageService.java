package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.support.CoverageMatchingSupport;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the STT ↔ ZM relationship from shared governorate + module coverage
 * (the replacement for the removed one-to-one {@code assignedZm} link).
 *
 * <p>An STT is "owned" by a ZM when that ZM's effective territory overlaps the
 * governorates the STT is approved to cover ({@code coveredGovernorates}). A ZM
 * only appears here once it (or the admin) has actually granted the STT coverage
 * inside its territory — i.e. "at least one ZM has approved the STT".
 */
@Service
public class SttZmCoverageService {

    private final UserRepository userRepository;

    public SttZmCoverageService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * All approved+enabled ZMs whose effective territory overlaps the STT's approved coverage,
     * ordered by name. Empty when no ZM has approved any of the STT's governorates yet.
     */
    public List<AppUser> findApprovingZmsForStt(AppUser stt) {
        if (stt == null) {
            return List.of();
        }

        List<String> sttCoverage = CoverageMatchingSupport.safeCovered(stt);
        if (sttCoverage.isEmpty()) {
            return List.of();
        }

        return userRepository.findByRoleOrderByFullNameAsc(UserRole.ROLE_ZM).stream()
                .filter(zm -> zm.getStatus() == AccountStatus.APPROVED && zm.isEnabled())
                .filter(zm -> CoverageMatchingSupport.hasOverlap(
                        CoverageMatchingSupport.effectiveZmGovernorates(zm), sttCoverage))
                .toList();
    }

    /**
     * The primary ZM used to anchor a technician invitation. Prefers a ZM that has
     * explicitly approved coverage for this STT; otherwise falls back to any ZM whose
     * territory overlaps the STT's approved coverage (e.g. admin-assigned coverage).
     */
    public Optional<AppUser> findPrimaryZmForStt(AppUser stt) {
        List<AppUser> approvingZms = findApprovingZmsForStt(stt);
        if (approvingZms.isEmpty()) {
            return Optional.empty();
        }

        return approvingZms.stream()
                .filter(zm -> hasExplicitApproval(stt, zm.getId()))
                .findFirst()
                .or(() -> approvingZms.stream().findFirst());
    }

    private boolean hasExplicitApproval(AppUser stt, String zmId) {
        return stt.getCoverageApprovedByZm() != null
                && zmId != null
                && stt.getCoverageApprovedByZm().getOrDefault(zmId, List.of()).stream()
                        .anyMatch(governorate -> governorate != null && !governorate.isBlank());
    }
}

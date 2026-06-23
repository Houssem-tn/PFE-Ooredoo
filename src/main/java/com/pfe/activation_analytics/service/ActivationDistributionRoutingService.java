package com.pfe.activation_analytics.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.support.TunisiaGovernorateSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivationDistributionRoutingService {

    private static final List<CaseStatus> ACTIVE_CASE_STATUSES = EnumSet.complementOf(EnumSet.of(CaseStatus.CLOSED))
            .stream()
            .toList();

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;

    public RoutingDecision resolveZmForGovernorate(String governorate) {
        if (!ActivationAnalyticsSupport.hasText(governorate)) {
            return RoutingDecision.noGovernorate();
        }

        String normalizedGovernorate = TunisiaGovernorateSupport.canonicalize(governorate);
        if (!ActivationAnalyticsSupport.hasText(normalizedGovernorate)) {
            return RoutingDecision.noGovernorate();
        }

        List<AppUser> candidates = userRepository.findByRoleOrderByFullNameAsc(UserRole.ROLE_ZM)
                .stream()
                .filter(user -> user.getRole() == UserRole.ROLE_ZM)
                .filter(user -> user.getStatus() == AccountStatus.APPROVED)
                .filter(AppUser::isEnabled)
                .filter(user -> TunisiaGovernorateSupport.containsEquivalent(user.getAssignedGovernorates(), normalizedGovernorate))
                .toList();

        if (candidates.isEmpty()) {
            return RoutingDecision.unassigned(normalizedGovernorate);
        }

        if (candidates.size() == 1) {
            AppUser chosen = candidates.get(0);
            return new RoutingDecision(normalizedGovernorate, chosen, countActiveWorkload(chosen.getId()), 1);
        }

        AppUser chosen = candidates.stream()
                .min(Comparator
                        .comparingLong((AppUser user) -> countActiveWorkload(user.getId()))
                        .thenComparing(user -> displayName(user), String.CASE_INSENSITIVE_ORDER))
                .orElse(candidates.get(0));

        return new RoutingDecision(normalizedGovernorate, chosen, countActiveWorkload(chosen.getId()), candidates.size());
    }

    private long countActiveWorkload(String zmId) {
        if (!ActivationAnalyticsSupport.hasText(zmId)) {
            return Long.MAX_VALUE;
        }
        return caseRepository.countByTypeAndZmIdAndStatusIn(CaseType.ACTIVATION, zmId, ACTIVE_CASE_STATUSES);
    }

    private String displayName(AppUser user) {
        if (user == null) {
            return "";
        }
        if (ActivationAnalyticsSupport.hasText(user.getFullName())) {
            return user.getFullName().trim();
        }
        return user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase();
    }

    public record RoutingDecision(
            String governorate,
            AppUser zm,
            long activeWorkload,
            int candidateCount
    ) {
        public static RoutingDecision noGovernorate() {
            return new RoutingDecision(null, null, 0L, 0);
        }

        public static RoutingDecision unassigned(String governorate) {
            return new RoutingDecision(governorate, null, 0L, 0);
        }

        public boolean assigned() {
            return zm != null;
        }
    }
}

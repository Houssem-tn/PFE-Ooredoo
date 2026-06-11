package com.pfe.case_management.service;

import com.pfe.MainPackage.Entity.CaseModuleType;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.case_management.dto.PerformanceSummaryResponse;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.SlaTrackingInfo;
import com.pfe.case_management.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CaseAnalyticsService {

    private static final Set<CaseStatus> TECH_ACTIVE_STATUSES = EnumSet.of(
            CaseStatus.ASSIGNED_TO_TECH,
            CaseStatus.NOT_REACHABLE,
            CaseStatus.RDV_SET
    );

    private static final Set<CaseStatus> STT_ACTIVE_STATUSES = EnumSet.of(
            CaseStatus.ASSIGNED_TO_STT,
            CaseStatus.ASSIGNED_TO_TECH,
            CaseStatus.NOT_REACHABLE,
            CaseStatus.RDV_SET,
            CaseStatus.WORK_DONE,
            CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> ZM_ACTIVE_STATUSES = EnumSet.of(
            CaseStatus.ASSIGNED_TO_ZM,
            CaseStatus.ASSIGNED_TO_STT,
            CaseStatus.ASSIGNED_TO_TECH,
            CaseStatus.NOT_REACHABLE,
            CaseStatus.RDV_SET,
            CaseStatus.WORK_DONE,
            CaseStatus.REJECTED_BY_ZM
    );

    private static final String RES_IMPORT_SOURCE = "RES_TODO_IMPORT";

    private final CaseRepository caseRepository;
    private final SlaTrackingService slaTrackingService;

    public PerformanceSummaryResponse getMyPerformance(CaseType type) {
        return getMyPerformance(type, null);
    }

    public PerformanceSummaryResponse getMyPerformance(CaseType type, CaseModuleType caseModule) {
        UserRole role = currentUserRole();
        String email = currentUserEmail();
        CaseType effectiveType = type != null ? type : CaseType.ACTIVATION;

        return summarize(
                null,
                role.name(),
                null,
                email,
                loadScope(role, email, effectiveType, caseModule),
                role
        );
    }

    public List<PerformanceSummaryResponse> getTechPerformanceForMyScope(CaseType type) {
        return getTechPerformanceForMyScope(type, null);
    }

    public List<PerformanceSummaryResponse> getTechPerformanceForMyScope(CaseType type, CaseModuleType caseModule) {
        UserRole role = currentUserRole();
        String email = currentUserEmail();
        CaseType effectiveType = type != null ? type : CaseType.ACTIVATION;

        List<CaseEntity> scope = loadScope(role, email, effectiveType, caseModule);

        Map<String, List<CaseEntity>> grouped = new HashMap<>();
        for (CaseEntity c : scope) {
            if (isBlank(c.getTechId()) && isBlank(c.getTechEmail())) {
                continue;
            }

            String key = !isBlank(c.getTechId()) ? c.getTechId() : c.getTechEmail().trim().toLowerCase();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(c);
        }

        List<PerformanceSummaryResponse> results = new ArrayList<>();
        for (List<CaseEntity> techCases : grouped.values()) {
            CaseEntity sample = techCases.get(0);
            results.add(summarize(
                    sample.getTechId(),
                    UserRole.ROLE_TECH.name(),
                    sample.getTechName(),
                    sample.getTechEmail(),
                    techCases,
                    UserRole.ROLE_TECH
            ));
        }

        results.sort(Comparator.comparingLong(PerformanceSummaryResponse::assignedCases).reversed());
        return results;
    }

    public PerformanceSummaryResponse getPerformanceForUser(
            String userId,
            String userName,
            String userEmail,
            UserRole role
    ) {
        if (role == null || isBlank(userEmail) || !supportsPerformance(role)) {
            return new PerformanceSummaryResponse(
                    userId,
                    blankToNull(userName),
                    blankToNull(userEmail),
                    role != null ? role.name() : null,
                    0L,
                    0L,
                    0L,
                    0d,
                    0L,
                    0L,
                    0L,
                    0d,
                    0d,
                    0d,
                    0L,
                    0L
            );
        }

        return summarize(
                userId,
                role.name(),
                userName,
                userEmail,
                loadScope(role, userEmail, CaseType.ACTIVATION, null),
                role
        );
    }

    private PerformanceSummaryResponse summarize(
            String userId,
            String role,
            String userName,
            String userEmail,
            List<CaseEntity> cases,
            UserRole scopeRole
    ) {
        long assignedCases = cases.size();
        Instant now = Instant.now();

        long rdvOnTime = cases.stream().filter(c -> hasSlaStatus(c, SlaStatus.COMPLETED_ON_TIME)).count();
        long rdvLate = cases.stream().filter(c -> hasSlaStatus(c, SlaStatus.COMPLETED_LATE)).count();
        long completedRdv = rdvOnTime + rdvLate;
        double rdvOnTimeRate = completedRdv == 0
            ? 0d
            : round2(((double) rdvOnTime * 100d) / (double) completedRdv);

        long warnings = cases.stream().filter(c -> c.getSlaTracking() != null && c.getSlaTracking().getWarningTriggeredAt() != null).count();
        long breaches = cases.stream().filter(this::isBreached).count();
        long escalations = cases.stream().filter(c -> c.getSlaTracking() != null && c.getSlaTracking().getEscalationTriggeredAt() != null).count();

        double averageResponseMinutes = cases.stream()
                .map(CaseEntity::getSlaTracking)
                .filter(sla -> sla != null && sla.getResponseDurationSeconds() != null)
                .mapToLong(SlaTrackingInfo::getResponseDurationSeconds)
                .average()
                .orElse(0d) / 60d;

        double averageOverdueMinutes = cases.stream()
            .map(CaseEntity::getSlaTracking)
            .mapToDouble(sla -> overdueMinutes(sla, now))
            .filter(value -> value > 0d)
            .average()
            .orElse(0d);

        long completedWorkCount = cases.stream().filter(this::isWorkCompleted).count();
        double workCompletionProgress = assignedCases == 0
                ? 0d
                : round2(((double) completedWorkCount * 100d) / (double) assignedCases);

        long activeTasksCount = cases.stream().filter(c -> isActive(c, scopeRole)).count();

        return new PerformanceSummaryResponse(
                userId,
                blankToNull(userName),
                blankToNull(userEmail),
                role,
                assignedCases,
                rdvOnTime,
                rdvLate,
                rdvOnTimeRate,
                warnings,
                breaches,
                escalations,
                round2(averageResponseMinutes),
                round2(averageOverdueMinutes),
                workCompletionProgress,
                activeTasksCount,
                completedWorkCount
        );
    }

    private List<CaseEntity> loadScope(UserRole role, String email, CaseType type, CaseModuleType caseModule) {
        List<CaseEntity> scope = switch (role) {
            case ROLE_TECH -> caseRepository.findByTypeAndTechEmailIgnoreCase(type, email);
            case ROLE_STT -> caseRepository.findByTypeAndSttEmailIgnoreCase(type, email);
            case ROLE_ZM -> caseRepository.findByTypeAndZmEmailIgnoreCase(type, email);
            case ROLE_ADMIN -> caseRepository.findByType(type);
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role for analytics");
        };

        scope = applyModuleScope(type, caseModule, scope);
        refreshSlaForRead(scope);
        return scope;
    }

    /**
     * Mirrors CaseService board scoping so RES performance is RES-only and complaint performance
     * excludes RES. {@code caseModule == RES} → only RES; non-RES module (PLT) → exclude RES;
     * null → no filter (combined / non-complaint scopes).
     */
    private List<CaseEntity> applyModuleScope(CaseType type, CaseModuleType caseModule, List<CaseEntity> cases) {
        if (type != CaseType.PLAINTE || caseModule == null) {
            return cases;
        }

        List<CaseEntity> filtered = new ArrayList<>(cases.size());
        for (CaseEntity c : cases) {
            boolean isRes = isResModuleCase(c);
            if (caseModule == CaseModuleType.RES ? isRes : !isRes) {
                filtered.add(c);
            }
        }
        return filtered;
    }

    private boolean isResModuleCase(CaseEntity caseEntity) {
        return caseEntity != null
                && (caseEntity.getCaseModule() == CaseModuleType.RES
                || RES_IMPORT_SOURCE.equals(caseEntity.getSource()));
    }

    private boolean supportsPerformance(UserRole role) {
        return role == UserRole.ROLE_TECH
                || role == UserRole.ROLE_STT
                || role == UserRole.ROLE_ZM
                || role == UserRole.ROLE_ADMIN;
    }

    private double overdueMinutes(SlaTrackingInfo sla, Instant now) {
        if (sla == null || sla.getDeadlineAt() == null) {
            return 0d;
        }

        if (sla.getStatus() == SlaStatus.BREACHED && sla.getBreachedAt() != null) {
            return Duration.between(sla.getBreachedAt(), now).toSeconds() / 60d;
        }

        if (sla.getStatus() == SlaStatus.COMPLETED_LATE && sla.getRdvSetAt() != null) {
            return Duration.between(sla.getDeadlineAt(), sla.getRdvSetAt()).toSeconds() / 60d;
        }

        return 0d;
    }

    private void refreshSlaForRead(List<CaseEntity> cases) {
        Instant now = Instant.now();
        for (CaseEntity c : cases) {
            slaTrackingService.refreshLiveState(c, now);
        }
    }

    private boolean hasSlaStatus(CaseEntity caseEntity, SlaStatus status) {
        return caseEntity.getSlaTracking() != null
                && caseEntity.getSlaTracking().getStatus() == status;
    }

    private boolean isBreached(CaseEntity caseEntity) {
        SlaTrackingInfo sla = caseEntity.getSlaTracking();
        if (sla == null) {
            return false;
        }

        return sla.getStatus() == SlaStatus.BREACHED
                || sla.getStatus() == SlaStatus.COMPLETED_LATE
                || sla.getBreachedAt() != null;
    }

    private boolean isWorkCompleted(CaseEntity caseEntity) {
        return caseEntity.getStatus() == CaseStatus.WORK_DONE
                || caseEntity.getStatus() == CaseStatus.VALIDATED_BY_ZM
                || caseEntity.getStatus() == CaseStatus.CLOSED;
    }

    private boolean isActive(CaseEntity caseEntity, UserRole role) {
        return switch (role) {
            case ROLE_TECH -> TECH_ACTIVE_STATUSES.contains(caseEntity.getStatus());
            case ROLE_STT -> STT_ACTIVE_STATUSES.contains(caseEntity.getStatus());
            case ROLE_ZM -> ZM_ACTIVE_STATUSES.contains(caseEntity.getStatus());
            case ROLE_ADMIN -> caseEntity.getStatus() != CaseStatus.CLOSED;
            default -> false;
        };
    }

    private UserRole currentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || auth.getAuthorities().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        String authority = auth.getAuthorities().iterator().next().getAuthority();
        return UserRole.valueOf(authority);
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        return auth.getName().trim().toLowerCase();
    }

    private String blankToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }
}

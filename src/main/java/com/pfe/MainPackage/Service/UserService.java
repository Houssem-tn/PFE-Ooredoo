package com.pfe.MainPackage.Service;


import com.pfe.MainPackage.Dto.*;
import com.pfe.MainPackage.Entity.*;
import com.pfe.MainPackage.Repository.TechInvitationRepository;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.case_management.dto.PerformanceSummaryResponse;
import com.pfe.case_management.service.CaseAnalyticsService;
import com.pfe.exception.NotFoundException;
import com.pfe.MainPackage.mapper.UserMapper;
import com.pfe.support.CoverageMatchingSupport;
import com.pfe.support.TunisiaGovernorateSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repo;
    private final UserMapper mapper;
    private final PasswordEncoder encoder;
    private final FileStorageService fileStorageService;
    private final TechInvitationRepository techInvitationRepository;
    private final CaseAnalyticsService caseAnalyticsService;

    public UserService(
            UserRepository repo,
            UserMapper mapper,
            PasswordEncoder encoder,
            FileStorageService fileStorageService,
            TechInvitationRepository techInvitationRepository,
            CaseAnalyticsService caseAnalyticsService
    ) {
        this.repo = repo;
        this.mapper = mapper;
        this.encoder = encoder;
        this.fileStorageService = fileStorageService;
        this.techInvitationRepository = techInvitationRepository;
        this.caseAnalyticsService = caseAnalyticsService;
    }

    public List<UserDTO> findAll() {
        return repo.findAll().stream().map(mapper::toDto).toList();
    }

    public UserDTO findById(String id) {
        return mapper.toDto(getEntity(id));
    }

    public UserAuditDetailsDTO findAuditDetails(String id) {
        AppUser user = getEntity(id);
        UserRole effectiveRole = resolveAuditRole(user);

        TechInvitation invitation = resolveInvitation(user, effectiveRole);
        UserAuditActorDTO validator = resolveActorByEmail(user.getValidatedBy());
        UserAuditActorDTO assignedStt = resolveActor(user.getAssignedSttId(), user.getAssignedSttEmail(), null);
        UserAuditActorDTO assignedZm = resolveActor(user.getAssignedZmId(), user.getAssignedZmEmail(), null);
        UserAuditActorDTO invitedBy = invitation != null
                ? resolveActor(invitation.getSttId(), invitation.getSttEmail(), invitation.getSttName())
                : null;
        UserAuditActorDTO invitationZm = invitation != null
                ? resolveActor(invitation.getZmId(), invitation.getZmEmail(), invitation.getZmName())
                : null;

        return new UserAuditDetailsDTO(
                mapper.toDto(user),
                validator,
                assignedStt,
                assignedZm,
                invitedBy,
                invitationZm,
                toInvitationDto(invitation),
                buildHierarchy(user, effectiveRole),
                buildRoleProfile(user),
                buildPerformance(user, effectiveRole)
        );
    }

    public UserDTO findMe(String email) {
        AppUser user = repo.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
        return mapper.toDto(user);
    }

    /**
     * Self-service profile update for the authenticated user. The caller is
     * resolved from the security context (email), never from a client-supplied
     * id. ADMIN accounts are read-only and rejected with 403. Only the safe
     * fields (full name, phone) are ever touched here — email/username, role,
     * account status and permissions are left untouched by design.
     */
    public UserDTO updateMe(String email, UpdateMyProfileRequest req) {
        AppUser user = repo.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new NotFoundException("User not found: " + email));

        if (user.getRole() == UserRole.ROLE_ADMIN) {
            throw new AccessDeniedException("Admin profile is read-only and cannot be modified");
        }

        String normalizedFullName = req.fullName().trim().replaceAll("\\s+", " ");
        user.setFullName(normalizedFullName);

        String normalizedPhone = (req.phone() != null && !req.phone().isBlank())
                ? req.phone().trim()
                : null;
        user.setPhone(normalizedPhone);

        AppUser saved = repo.save(user);

        // Audit trail: profile self-update.
        log.info("AUDIT PROFILE_UPDATED userId={} role={} at={}",
                saved.getId(), saved.getRole(), Instant.now());

        return mapper.toDto(saved);
    }

    public UserDTO create(UserCreateRequest req) {
        String email = req.email().toLowerCase();
        if (repo.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already used");
        }

        AppUser u = AppUser.builder()
                .fullName(req.fullName())
                .email(email)
                .password(encoder.encode(req.password()))
                .role(req.role())
                .enabled(req.enabled() == null || req.enabled())
                .build();

        return mapper.toDto(repo.save(u));
    }

    public UserDTO update(String id, UserUpdateRequest req) {
        AppUser u = getEntity(id);

        if (req.fullName() != null) u.setFullName(req.fullName());

        if (req.email() != null) {
            String newEmail = req.email().toLowerCase();
            if (!newEmail.equals(u.getEmail()) && repo.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("Email already used");
            }
            u.setEmail(newEmail);
        }

        if (req.role() != null) u.setRole(req.role());
        if (req.enabled() != null) u.setEnabled(req.enabled());
        if (req.canCreateDpm() != null) u.setCanCreateDpm(req.canCreateDpm());

        if (req.password() != null && !req.password().isBlank()) {
            u.setPassword(encoder.encode(req.password()));
        }

        return mapper.toDto(repo.save(u));
    }

    public List<PublicSttOptionDTO> findPublicStts() {
        return repo.findByRoleAndStatusAndEnabled(
                        UserRole.ROLE_STT,
                        AccountStatus.APPROVED,
                        true
                ).stream()
                .map(u -> new PublicSttOptionDTO(
                        u.getId(),
                        u.getFullName(),
                        u.getEmail()
                ))
                .toList();
    }

    public List<UserAuditItemDTO> findAuditUsersByRole(UserRole role) {
        if (role != UserRole.ROLE_ZM && role != UserRole.ROLE_STT && role != UserRole.ROLE_TECH) {
            throw new IllegalArgumentException("Audit is allowed only for ZM, STT, and TECH");
        }

        return repo.findByRoleOrderByFullNameAsc(role).stream()
                .map(u -> new UserAuditItemDTO(
                        u.getId(),
                        u.getFullName(),
                        u.getEmail(),
                        u.getRole(),
                        u.getStatus(),
                        u.isEnabled(),
                        u.getCreatedAt(),
                        u.getLastLoginAt()
                ))
                .toList();
    }

    public List<UserDTO> findMyStts(String zmEmail) {
        AppUser zm = repo.findByEmail(zmEmail.toLowerCase())
                .orElseThrow(() -> new NotFoundException("ZM not found: " + zmEmail));

        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new AccessDeniedException("Current user is not a ZM");
        }

        // Build effective ZM territory: legacy activation governorates + all module-specific
        // governorates (PLT, RES …). This ensures ZMs who only hold PLT/complaint coverage
        // (no activation assignedGovernorates) still see the STTs relevant to their territory.
        List<String> governorates = buildEffectiveZmGovernorates(zm);

        return repo.findByRoleOrderByFullNameAsc(UserRole.ROLE_STT)
                .stream()
                .filter(stt -> stt.getStatus() == AccountStatus.APPROVED)
                .filter(AppUser::isEnabled)
                .filter(stt -> hasGovernorateOverlap(governorates, stt.getCoveredGovernorates()))
                .map(mapper::toDto)
                .toList();
    }

    /**
     * Returns IDs of all approved+enabled STTs whose covered governorates overlap
     * with the given ZM's effective territory. This is the single source of truth
     * for "which STTs does this ZM own" — used by both the team view and stock scoping.
     */
    public List<String> findResponsibleSttIds(AppUser zm) {
        List<String> governorates = buildEffectiveZmGovernorates(zm);
        return repo.findByRoleOrderByFullNameAsc(UserRole.ROLE_STT)
                .stream()
                .filter(stt -> stt.getStatus() == AccountStatus.APPROVED)
                .filter(AppUser::isEnabled)
                .filter(stt -> hasGovernorateOverlap(governorates, stt.getCoveredGovernorates()))
                .map(AppUser::getId)
                .toList();
    }

    private List<String> buildEffectiveZmGovernorates(AppUser zm) {
        LinkedHashSet<String> combined = new LinkedHashSet<>(safeGovernorates(zm.getAssignedGovernorates()));
        Map<String, List<String>> coverage = zm.getCoverageByModule();
        if (coverage != null) {
            for (List<String> moduleGovs : coverage.values()) {
                combined.addAll(safeGovernorates(moduleGovs));
            }
        }
        return List.copyOf(combined);
    }

    public List<SttCoverageRequestResponse> findMySttCoverageRequests(String zmEmail) {
        AppUser zm = repo.findByEmail(zmEmail.toLowerCase())
                .orElseThrow(() -> new NotFoundException("ZM not found: " + zmEmail));

        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new AccessDeniedException("Current user is not a ZM");
        }

        // Pending = STT-declared governorates inside this ZM's *effective* territory
        // (assignedGovernorates + every coverageByModule entry) that this ZM has not yet
        // decided. Filtering is per-ZM, so ZM X approving Sousse/ACT never removes the
        // same STT from ZM Y's pending list for Sousse/PLT.
        return repo.findByRoleOrderByFullNameAsc(UserRole.ROLE_STT).stream()
                .filter(stt -> stt.getStatus() == AccountStatus.APPROVED)
                .filter(AppUser::isEnabled)
                .map(stt -> new SttCoverageRequestResponse(mapper.toDto(stt), pendingCoverageFor(zm, stt)))
                .filter(response -> !response.pending().isEmpty())
                .toList();
    }

    private List<SttCoverageRequestResponse.PendingGovernorateCoverage> pendingCoverageFor(AppUser zm, AppUser stt) {
        return CoverageMatchingSupport.pendingGovernoratesForZm(zm, stt).stream()
                .map(governorate -> new SttCoverageRequestResponse.PendingGovernorateCoverage(
                        governorate,
                        CoverageMatchingSupport.modulesForGovernorate(zm, governorate)))
                .toList();
    }

    public List<UserDTO> findMyTechsByStt(String zmEmail, String sttId) {
        AppUser zm = repo.findByEmail(zmEmail.toLowerCase())
                .orElseThrow(() -> new NotFoundException("ZM not found: " + zmEmail));

        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new AccessDeniedException("Current user is not a ZM");
        }

        AppUser stt = repo.findById(sttId)
                .orElseThrow(() -> new NotFoundException("STT not found: " + sttId));

        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new IllegalArgumentException("Selected user is not an STT");
        }

        // Use full effective territory (assignedGovernorates + all coverageByModule entries)
        // so ZMs whose coverage is stored only in module-specific fields still see their STTs.
        if (!hasGovernorateOverlap(buildEffectiveZmGovernorates(zm), stt.getCoveredGovernorates())) {
            throw new AccessDeniedException("This STT does not cover the current ZM territory");
        }

        return repo.findByRoleAndAssignedSttIdOrderByCreatedAtDesc(UserRole.ROLE_TECH, sttId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    public List<UserDTO> findMyTechs(String sttEmail) {
        AppUser stt = repo.findByEmail(sttEmail.toLowerCase())
                .orElseThrow(() -> new NotFoundException("STT not found: " + sttEmail));

        return repo.findByRoleAndAssignedSttIdOrderByCreatedAtDesc(UserRole.ROLE_TECH, stt.getId())
                .stream()
                .filter(u -> u.getStatus() == AccountStatus.APPROVED && u.isEnabled())
                .map(mapper::toDto)
                .toList();
    }

    public UserDTO requestCoverageAreas(String sttEmail, List<String> requestedGovernorates) {
        AppUser stt = repo.findByEmail(sttEmail.toLowerCase())
                .orElseThrow(() -> new NotFoundException("STT not found: " + sttEmail));

        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new AccessDeniedException("Current user is not an STT");
        }

        if (stt.getStatus() != AccountStatus.APPROVED || !stt.isEnabled()) {
            throw new AccessDeniedException("Your account is not active");
        }

        List<String> normalizedSelection = normalizeGovernoratesAllowEmpty(requestedGovernorates);
        List<String> currentCoverage = safeGovernorates(stt.getCoveredGovernorates());
        List<String> pendingRequest = normalizedSelection.stream()
                .filter(governorate -> !currentCoverage.contains(governorate))
                .toList();

        ensureGovernoratesHaveOwningZm(pendingRequest);

        stt.setRequestedGovernorates(pendingRequest);
        // Re-requesting a governorate re-opens it for any ZM that previously declined it,
        // so the pending confirmation surfaces again for that ZM.
        reopenDeclinedGovernorates(stt, pendingRequest);
        return mapper.toDto(repo.save(stt));
    }

    /**
     * A ZM approves some of the governorates the STT declared inside the ZM's territory.
     * The decision is recorded per-ZM so it stays independent from every other ZM, while
     * {@code coveredGovernorates} (the union used by stock scoping / the team view) grows.
     */
    public UserDTO validateCoverageAreas(String zmEmail, String sttId, List<String> approvedGovernorates) {
        AppUser zm = loadDecidingZm(zmEmail);
        AppUser stt = loadSttForDecision(sttId);

        List<String> normalizedGovernorates = normalizeGovernoratesRequireSelection(approvedGovernorates);
        ensureGovernoratesPendingForZm(zm, stt, normalizedGovernorates);

        LinkedHashSet<String> updatedCoverage = new LinkedHashSet<>(safeGovernorates(stt.getCoveredGovernorates()));
        updatedCoverage.addAll(normalizedGovernorates);
        stt.setCoveredGovernorates(orderGovernorates(updatedCoverage));

        // Drop the approved zones from the shared "still pending for everyone" pool; the
        // STT-declared coverage now lives in coveredGovernorates, keeping them visible to
        // other ZMs who have not decided yet.
        stt.setRequestedGovernorates(
                safeGovernorates(stt.getRequestedGovernorates()).stream()
                        .filter(governorate -> !normalizedGovernorates.contains(governorate))
                        .toList()
        );

        stt.setCoverageApprovedByZm(mergeZmDecision(stt.getCoverageApprovedByZm(), zm.getId(), normalizedGovernorates));
        stt.setCoverageDeclinedByZm(removeZmDecision(stt.getCoverageDeclinedByZm(), zm.getId(), normalizedGovernorates));

        return mapper.toDto(repo.save(stt));
    }

    /**
     * A ZM declines some pending governorates for the STT. Only this ZM's view changes:
     * the STT's global {@code coveredGovernorates} and every other ZM's pending list are
     * left untouched.
     */
    public UserDTO declineCoverageAreas(String zmEmail, String sttId, List<String> declinedGovernorates) {
        AppUser zm = loadDecidingZm(zmEmail);
        AppUser stt = loadSttForDecision(sttId);

        List<String> normalizedGovernorates = normalizeGovernoratesRequireSelection(declinedGovernorates);
        ensureGovernoratesPendingForZm(zm, stt, normalizedGovernorates);

        stt.setCoverageDeclinedByZm(mergeZmDecision(stt.getCoverageDeclinedByZm(), zm.getId(), normalizedGovernorates));

        return mapper.toDto(repo.save(stt));
    }

    private AppUser loadDecidingZm(String zmEmail) {
        AppUser zm = repo.findByEmail(zmEmail.toLowerCase())
                .orElseThrow(() -> new NotFoundException("ZM not found: " + zmEmail));

        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new AccessDeniedException("Current user is not a ZM");
        }

        if (zm.getStatus() != AccountStatus.APPROVED || !zm.isEnabled()) {
            throw new AccessDeniedException("Your account is not active");
        }

        return zm;
    }

    private AppUser loadSttForDecision(String sttId) {
        AppUser stt = repo.findById(sttId)
                .orElseThrow(() -> new NotFoundException("STT not found: " + sttId));

        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new IllegalArgumentException("Selected user is not an STT");
        }

        return stt;
    }

    private void ensureGovernoratesPendingForZm(AppUser zm, AppUser stt, List<String> governorates) {
        List<String> territory = CoverageMatchingSupport.effectiveZmGovernorates(zm);
        List<String> pending = CoverageMatchingSupport.pendingGovernoratesForZm(zm, stt);

        for (String governorate : governorates) {
            if (!territory.contains(governorate)) {
                throw new AccessDeniedException("Governorate " + governorate + " is outside your territory");
            }

            if (!pending.contains(governorate)) {
                throw new IllegalArgumentException("Governorate " + governorate + " is not pending your decision for this STT");
            }
        }
    }

    private Map<String, List<String>> mergeZmDecision(Map<String, List<String>> decisions, String zmId, List<String> governorates) {
        Map<String, List<String>> result = decisions != null ? new LinkedHashMap<>(decisions) : new LinkedHashMap<>();
        LinkedHashSet<String> merged = new LinkedHashSet<>(safeGovernorates(result.get(zmId)));
        merged.addAll(governorates);
        result.put(zmId, orderGovernorates(merged));
        return result;
    }

    private Map<String, List<String>> removeZmDecision(Map<String, List<String>> decisions, String zmId, List<String> governorates) {
        if (decisions == null || decisions.isEmpty() || !decisions.containsKey(zmId)) {
            return decisions;
        }

        Map<String, List<String>> result = new LinkedHashMap<>(decisions);
        List<String> remaining = safeGovernorates(result.get(zmId)).stream()
                .filter(governorate -> !governorates.contains(governorate))
                .toList();

        if (remaining.isEmpty()) {
            result.remove(zmId);
        } else {
            result.put(zmId, remaining);
        }
        return result;
    }

    private void reopenDeclinedGovernorates(AppUser stt, List<String> governorates) {
        Map<String, List<String>> declined = stt.getCoverageDeclinedByZm();
        if (declined == null || declined.isEmpty() || governorates.isEmpty()) {
            return;
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : declined.entrySet()) {
            List<String> remaining = safeGovernorates(entry.getValue()).stream()
                    .filter(governorate -> !governorates.contains(governorate))
                    .toList();
            if (!remaining.isEmpty()) {
                result.put(entry.getKey(), remaining);
            }
        }
        stt.setCoverageDeclinedByZm(result);
    }

    public UserDTO uploadProfileImage(String userId, MultipartFile file, Authentication authentication) {
        AppUser targetUser = getEntity(userId);

        String currentEmail = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        boolean isOwner = targetUser.getEmail() != null
                && targetUser.getEmail().equalsIgnoreCase(currentEmail);

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You are not allowed to update this profile image");
        }

        if (targetUser.getProfileImageFileId() != null && !targetUser.getProfileImageFileId().isBlank()) {
            fileStorageService.softDelete(targetUser.getProfileImageFileId());
        }

        FileMetadata savedFile = fileStorageService.upload(
                file,
                FileEntityType.USER,
                targetUser.getId(),
                FileRole.PROFILE_IMAGE,
                currentEmail
        );

        String imageUrl = fileStorageService.buildViewUrl(savedFile.getId());

        targetUser.setProfileImageFileId(savedFile.getId());
        targetUser.setProfileImageUrl(imageUrl);
        targetUser.setProfileThumbnailUrl(imageUrl);

        return mapper.toDto(repo.save(targetUser));
    }

    public UserDTO updateCoverageImportPermission(String userId, boolean canImportCoverage) {
        AppUser user = getEntity(userId);

        if (user.getRole() != UserRole.ROLE_ZM) {
            throw new IllegalArgumentException("Coverage import permission can only be granted to users with role ZM");
        }

        user.setCanImportCoverage(canImportCoverage);
        return mapper.toDto(repo.save(user));
    }

    public void delete(String id) {
        AppUser u = getEntity(id);
        repo.delete(u);
    }

    private UserRole resolveAuditRole(AppUser user) {
        if (user == null) {
            return null;
        }

        return user.getRole() != null ? user.getRole() : user.getRoleRequested();
    }

    private TechInvitation resolveInvitation(AppUser user, UserRole effectiveRole) {
        if (user == null || effectiveRole != UserRole.ROLE_TECH) {
            return null;
        }

        if (hasText(user.getId())) {
            TechInvitation byRegisteredTech = techInvitationRepository
                    .findFirstByRegisteredTechIdOrderByCreatedAtDesc(user.getId())
                    .orElse(null);

            if (byRegisteredTech != null) {
                return byRegisteredTech;
            }
        }

        if (hasText(user.getEmail())) {
            return techInvitationRepository
                    .findFirstByEmailOrderByCreatedAtDesc(user.getEmail().trim().toLowerCase())
                    .orElse(null);
        }

        return null;
    }

    private UserHierarchyAuditDTO buildHierarchy(AppUser user, UserRole effectiveRole) {
        if (user == null || !hasText(user.getId()) || effectiveRole == null) {
            return new UserHierarchyAuditDTO(0L, 0L, 0L, List.of());
        }

        return switch (effectiveRole) {
            case ROLE_STT -> {
                List<AppUser> techs = repo.findByRoleAndAssignedSttIdOrderByCreatedAtDesc(
                        UserRole.ROLE_TECH,
                        user.getId()
                );

                yield new UserHierarchyAuditDTO(
                        techs.size(),
                        0L,
                        techs.size(),
                        techs.stream().map(this::toActor).toList()
                );
            }
            case ROLE_ZM -> {
                List<AppUser> stts = repo.findByRoleOrderByFullNameAsc(UserRole.ROLE_STT).stream()
                        .filter(candidate -> hasGovernorateOverlap(user.getAssignedGovernorates(), candidate.getCoveredGovernorates()))
                        .toList();

                Set<String> sttIds = stts.stream()
                        .map(AppUser::getId)
                        .filter(this::hasText)
                        .collect(LinkedHashSet::new, Set::add, Set::addAll);

                List<AppUser> techs = sttIds.stream()
                        .flatMap(sttId -> repo.findByRoleAndAssignedSttIdOrderByCreatedAtDesc(UserRole.ROLE_TECH, sttId).stream())
                        .toList();

                yield new UserHierarchyAuditDTO(
                        stts.size(),
                        stts.size(),
                        techs.size(),
                        stts.stream().map(this::toActor).toList()
                );
            }
            default -> new UserHierarchyAuditDTO(0L, 0L, 0L, List.of());
        };
    }

    private UserRoleProfileDTO buildRoleProfile(AppUser user) {
        if (user == null) {
            return null;
        }

        CrmBoutiqueProfile crm = user.getCrmBoutiqueProfile();
        SttProfile stt = user.getSttProfile();
        TechnicianProfile tech = user.getTechnicianProfile();
        ZmProfile zm = user.getZmProfile();

        return new UserRoleProfileDTO(
                crm != null ? blankToNull(crm.getBoutiqueName()) : null,
                crm != null ? blankToNull(crm.getBoutiqueCode()) : null,
                crm != null ? blankToNull(crm.getAddress()) : null,
                stt != null ? blankToNull(stt.getServiceCenter()) : null,
                stt != null ? blankToNull(stt.getDepartment()) : null,
                tech != null ? blankToNull(tech.getMatricule()) : null,
                tech != null ? blankToNull(tech.getSkillSet()) : null,
                tech != null ? blankToNull(tech.getRegion()) : null,
                tech != null ? tech.isAvailable() : null,
                zm != null ? blankToNull(zm.getZoneName()) : null,
                zm != null ? blankToNull(zm.getZoneCode()) : null,
                zm != null ? blankToNull(zm.getCoverage()) : null
        );
    }

    private PerformanceSummaryResponse buildPerformance(AppUser user, UserRole effectiveRole) {
        if (user == null) {
            return null;
        }

        return caseAnalyticsService.getPerformanceForUser(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                effectiveRole
        );
    }

    private UserInvitationAuditDTO toInvitationDto(TechInvitation invitation) {
        if (invitation == null) {
            return null;
        }

        return new UserInvitationAuditDTO(
                invitation.getId(),
                invitation.getStatus(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getRegisteredAt(),
                invitation.getZmDecisionAt(),
                invitation.isUsed()
        );
    }

    private UserAuditActorDTO resolveActorByEmail(String email) {
        return resolveActor(null, email, null);
    }

    private UserAuditActorDTO resolveActor(String userId, String email, String fallbackName) {
        AppUser candidate = null;

        if (hasText(userId)) {
            candidate = repo.findById(userId).orElse(null);
        }

        if (candidate == null && hasText(email)) {
            candidate = repo.findByEmail(email.trim().toLowerCase()).orElse(null);
        }

        if (candidate != null) {
            return toActor(candidate);
        }

        if (!hasText(email) && !hasText(fallbackName)) {
            return null;
        }

        return new UserAuditActorDTO(
                blankToNull(userId),
                blankToNull(fallbackName),
                normalizeEmail(email),
                null,
                null,
                null,
                null,
                null
        );
    }

    private UserAuditActorDTO toActor(AppUser user) {
        if (user == null) {
            return null;
        }

        UserDTO dto = mapper.toDto(user);

        return new UserAuditActorDTO(
                dto.id(),
                dto.fullName(),
                dto.email(),
                dto.role() != null ? dto.role() : dto.roleRequested(),
                dto.status(),
                dto.enabled(),
                dto.profileImageUrl(),
                dto.profileThumbnailUrl()
        );
    }

    private AppUser getEntity(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeEmail(String email) {
        return hasText(email) ? email.trim().toLowerCase() : null;
    }

    private boolean hasGovernorateOverlap(List<String> left, List<String> right) {
        Set<String> leftSet = safeGovernorates(left).stream()
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        if (leftSet.isEmpty()) {
            return false;
        }

        return safeGovernorates(right).stream()
                .anyMatch(leftSet::contains);
    }

    private List<String> safeGovernorates(List<String> governorates) {
        return TunisiaGovernorateSupport.normalizeStoredValues(governorates);
    }

    private List<String> normalizeGovernoratesAllowEmpty(List<String> governorates) {
        if (governorates == null || governorates.isEmpty()) {
            return List.of();
        }

        return TunisiaGovernorateSupport.normalizeSelection(governorates);
    }

    private List<String> normalizeGovernoratesRequireSelection(List<String> governorates) {
        List<String> normalizedGovernorates = normalizeGovernoratesAllowEmpty(governorates);
        if (normalizedGovernorates.isEmpty()) {
            throw new IllegalArgumentException("At least one governorate must be selected");
        }
        return normalizedGovernorates;
    }

    private List<String> orderGovernorates(Collection<String> governorates) {
        if (governorates == null || governorates.isEmpty()) {
            return List.of();
        }

        return TunisiaGovernorateSupport.normalizeSelection(governorates);
    }

    private void ensureGovernoratesHaveOwningZm(Collection<String> governorates) {
        if (governorates == null || governorates.isEmpty()) {
            return;
        }

        Map<String, String> ownersByGovernorate = new LinkedHashMap<>();
        for (AppUser zm : repo.findByRoleOrderByFullNameAsc(UserRole.ROLE_ZM)) {
            if (zm.getStatus() != AccountStatus.APPROVED || !zm.isEnabled()) {
                continue;
            }

            for (String governorate : safeGovernorates(zm.getAssignedGovernorates())) {
                if (!governorates.contains(governorate)) {
                    continue;
                }

                String previousOwner = ownersByGovernorate.putIfAbsent(governorate, displayName(zm));
                if (previousOwner != null && !previousOwner.equals(displayName(zm))) {
                    throw new IllegalStateException("Governorate " + governorate + " is assigned to multiple active ZMs");
                }
            }
        }

        List<String> missingOwners = governorates.stream()
                .filter(governorate -> !ownersByGovernorate.containsKey(governorate))
                .toList();

        if (!missingOwners.isEmpty()) {
            throw new IllegalStateException(
                    "No active ZM currently covers: " + String.join(", ", missingOwners)
            );
        }
    }

    private String displayName(AppUser user) {
        if (user == null) {
            return "unknown";
        }

        if (hasText(user.getFullName())) {
            return user.getFullName().trim();
        }

        if (hasText(user.getEmail())) {
            return user.getEmail().trim().toLowerCase();
        }

        return "unknown";
    }
}

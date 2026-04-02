package com.pfe.Service;

import com.pfe.Dto.*;
import com.pfe.Entity.*;
import com.pfe.Repository.TechInvitationRepository;
import com.pfe.Repository.UserRepository;
import com.pfe.case_management.dto.PerformanceSummaryResponse;
import com.pfe.case_management.service.CaseAnalyticsService;
import com.pfe.exception.NotFoundException;
import com.pfe.mapper.UserMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class UserService {

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

        return repo.findByRoleAndAssignedZmIdOrderByFullNameAsc(UserRole.ROLE_STT, zm.getId())
                .stream()
                .map(mapper::toDto)
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

        if (stt.getAssignedZmId() == null || !stt.getAssignedZmId().equals(zm.getId())) {
            throw new AccessDeniedException("This STT is not assigned to the current ZM");
        }

        return repo.findByRoleAndAssignedSttIdOrderByCreatedAtDesc(UserRole.ROLE_TECH, sttId)
                .stream()
                .map(mapper::toDto)
                .toList();
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
                List<AppUser> stts = repo.findByRoleAndAssignedZmIdOrderByFullNameAsc(
                        UserRole.ROLE_STT,
                        user.getId()
                );
                List<AppUser> techs = repo.findByRoleAndAssignedZmIdOrderByFullNameAsc(
                        UserRole.ROLE_TECH,
                        user.getId()
                );

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
}

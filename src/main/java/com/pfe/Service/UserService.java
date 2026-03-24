package com.pfe.Service;

import com.pfe.Dto.*;
import com.pfe.Entity.*;
import com.pfe.Repository.UserRepository;
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

    public UserService(
            UserRepository repo,
            UserMapper mapper,
            PasswordEncoder encoder,
            FileStorageService fileStorageService
    ) {
        this.repo = repo;
        this.mapper = mapper;
        this.encoder = encoder;
        this.fileStorageService = fileStorageService;
    }

    public List<UserDTO> findAll() {
        return repo.findAll().stream().map(mapper::toDto).toList();
    }

    public UserDTO findById(String id) {
        return mapper.toDto(getEntity(id));
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

    private AppUser getEntity(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
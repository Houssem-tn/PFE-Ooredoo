package com.pfe.coverage.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.coverage.dto.CoverageLayerResponse;
import com.pfe.coverage.dto.CoverageLayerSummaryResponse;
import com.pfe.coverage.entity.CoverageLayer;
import com.pfe.coverage.enums.CoverageLayerStatus;
import com.pfe.coverage.enums.CoverageLayerType;
import com.pfe.coverage.enums.CoverageTechnology;
import com.pfe.coverage.repository.CoverageLayerRepository;
import com.pfe.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class CoverageLayerService {

    private final CoverageLayerRepository repository;
    private final UserRepository userRepository;
    private final CoverageKmlConversionService conversionService;

    public CoverageLayerService(
            CoverageLayerRepository repository,
            UserRepository userRepository,
            CoverageKmlConversionService conversionService
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.conversionService = conversionService;
    }

    public CoverageLayerResponse upload(
            MultipartFile file,
            String name,
            CoverageTechnology technology,
            CoverageLayerType layerType,
            String governorate,
            String color,
            String callerEmail
    ) {
        AppUser caller = resolveUser(callerEmail);
        checkUploadPermission(caller);

        String geoJson;
        try {
            geoJson = conversionService.convert(file);
        } catch (IllegalArgumentException e) {
            throw e; // re-throw as-is — already a readable 400 message
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse KML/KMZ file: " + e.getMessage());
        }

        CoverageLayer layer = CoverageLayer.builder()
                .name(name != null ? name.trim() : file.getOriginalFilename())
                .technology(technology)
                .layerType(layerType)
                .governorate(governorate)
                .color(color)
                .sourceFileName(file.getOriginalFilename())
                .geoJson(geoJson)
                .uploadedByUserId(caller.getId())
                .uploadedByEmail(caller.getEmail())
                .uploadedByRole(caller.getRole().name())
                .status(CoverageLayerStatus.ACTIVE)
                .build();

        return toResponse(repository.save(layer));
    }

    public List<CoverageLayerSummaryResponse> findAll(String callerEmail) {
        AppUser caller = resolveUser(callerEmail);

        if (caller.getRole() == UserRole.ROLE_ADMIN) {
            return repository.findAll().stream().map(this::toSummary).toList();
        }

        // ZM: return only active layers
        // TODO: add governorate-scoped filtering when territorial model is extended to coverage layers
        return repository.findByStatus(CoverageLayerStatus.ACTIVE)
                .stream().map(this::toSummary).toList();
    }

    public CoverageLayerResponse findById(String id, String callerEmail) {
        resolveUser(callerEmail); // validates caller exists and is authenticated
        return toResponse(resolveLayer(id));
    }

    public void delete(String id, String callerEmail) {
        AppUser caller = resolveUser(callerEmail);
        if (caller.getRole() != UserRole.ROLE_ADMIN) {
            throw new AccessDeniedException("Only ADMIN can permanently delete coverage layers. Use archive instead.");
        }
        repository.delete(resolveLayer(id));
    }

    public CoverageLayerSummaryResponse archive(String id, String callerEmail) {
        AppUser caller = resolveUser(callerEmail);
        CoverageLayer layer = resolveLayer(id);

        if (caller.getRole() == UserRole.ROLE_ADMIN) {
            return doArchive(layer);
        }

        // An authorized ZM can archive only their own uploaded layers
        if (caller.getRole() == UserRole.ROLE_ZM
                && caller.isCanImportCoverage()
                && caller.getId().equals(layer.getUploadedByUserId())) {
            return doArchive(layer);
        }

        throw new AccessDeniedException("You are not authorized to archive this coverage layer.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void checkUploadPermission(AppUser user) {
        if (user.getRole() == UserRole.ROLE_ADMIN) return;
        if (user.getRole() == UserRole.ROLE_ZM && user.isCanImportCoverage()) return;
        throw new AccessDeniedException(
                "You do not have permission to import coverage files. " +
                "Ask your ADMIN to grant you the import permission.");
    }

    private CoverageLayerSummaryResponse doArchive(CoverageLayer layer) {
        layer.setStatus(CoverageLayerStatus.ARCHIVED);
        return toSummary(repository.save(layer));
    }

    private AppUser resolveUser(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }

    private CoverageLayer resolveLayer(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Coverage layer not found: " + id));
    }

    private CoverageLayerResponse toResponse(CoverageLayer l) {
        return new CoverageLayerResponse(
                l.getId(), l.getName(), l.getTechnology(), l.getLayerType(),
                l.getGovernorate(), l.getSourceFileName(), l.getColor(),
                l.getGeoJson(), l.getUploadedByEmail(), l.getUploadedByRole(),
                l.getStatus(), l.getCreatedAt(), l.getUpdatedAt()
        );
    }

    private CoverageLayerSummaryResponse toSummary(CoverageLayer l) {
        return new CoverageLayerSummaryResponse(
                l.getId(), l.getName(), l.getTechnology(), l.getLayerType(),
                l.getGovernorate(), l.getSourceFileName(), l.getColor(),
                l.getUploadedByEmail(), l.getStatus(), l.getCreatedAt()
        );
    }
}

package com.pfe.MainPackage.Controller;

import com.pfe.MainPackage.Dto.*;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    public record CoverageImportPermissionRequest(boolean canImportCoverage) {}

    public record CoverageRequestUpdateRequest(
            List<@NotBlank(message = "Governorate names cannot be blank") String> requestedGovernorates
    ) {
    }

    public record CoverageValidationRequest(
            @NotEmpty(message = "approvedGovernorates is required")
            List<@NotBlank(message = "Governorate names cannot be blank") String> approvedGovernorates
    ) {
    }

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<UserDTO> all() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public UserDTO one(@PathVariable String id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/audit-details")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public UserAuditDetailsDTO auditDetails(@PathVariable String id) {
        return service.findAuditDetails(id);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserDTO me(Authentication authentication) {
        return service.findMe(authentication.getName());
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserDTO updateMe(Authentication authentication,
                            @Valid @RequestBody UpdateMyProfileRequest req) {
        return service.updateMe(authentication.getName(), req);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public UserDTO create(@Valid @RequestBody UserCreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public UserDTO update(@PathVariable String id, @Valid @RequestBody UserUpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @GetMapping("/audit/by-role/{role}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<UserAuditItemDTO> auditByRole(@PathVariable UserRole role) {
        return service.findAuditUsersByRole(role);
    }

    @GetMapping("/me/stts")
    @PreAuthorize("hasAuthority('ROLE_ZM')")
    public List<UserDTO> myStts(Authentication authentication) {
        return service.findMyStts(authentication.getName());
    }

    @GetMapping("/me/stts/coverage-requests")
    @PreAuthorize("hasAuthority('ROLE_ZM')")
    public List<UserDTO> mySttCoverageRequests(Authentication authentication) {
        return service.findMySttCoverageRequests(authentication.getName());
    }

    @GetMapping("/me/stts/{sttId}/techs")
    @PreAuthorize("hasAuthority('ROLE_ZM')")
    public List<UserDTO> mySttTechs(@PathVariable String sttId, Authentication authentication) {
        return service.findMyTechsByStt(authentication.getName(), sttId);
    }

    @GetMapping("/me/techs")
    @PreAuthorize("hasAuthority('ROLE_STT')")
    public List<UserDTO> myTechs(Authentication authentication) {
        return service.findMyTechs(authentication.getName());
    }

    @PutMapping("/me/coverage-request")
    @PreAuthorize("hasAuthority('ROLE_STT')")
    public UserDTO requestCoverage(Authentication authentication,
                                   @Valid @RequestBody CoverageRequestUpdateRequest req) {
        return service.requestCoverageAreas(authentication.getName(), req.requestedGovernorates());
    }

    @PutMapping("/me/stts/{sttId}/coverage-validation")
    @PreAuthorize("hasAuthority('ROLE_ZM')")
    public UserDTO validateCoverage(@PathVariable String sttId,
                                    Authentication authentication,
                                    @Valid @RequestBody CoverageValidationRequest req) {
        return service.validateCoverageAreas(authentication.getName(), sttId, req.approvedGovernorates());
    }

    @PatchMapping("/{userId}/coverage-import-permission")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public UserDTO updateCoverageImportPermission(
            @PathVariable String userId,
            @RequestBody CoverageImportPermissionRequest req
    ) {
        return service.updateCoverageImportPermission(userId, req.canImportCoverage());
    }

    @PostMapping(value = "/{id}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ZM','ROLE_STT','ROLE_TECH')")
    public UserDTO uploadProfileImage(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return service.uploadProfileImage(id, file, authentication);
    }
}

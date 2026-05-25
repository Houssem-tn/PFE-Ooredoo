package com.pfe.coverage.controller;

import com.pfe.coverage.dto.CoverageLayerResponse;
import com.pfe.coverage.dto.CoverageLayerSummaryResponse;
import com.pfe.coverage.enums.CoverageLayerType;
import com.pfe.coverage.enums.CoverageTechnology;
import com.pfe.coverage.service.CoverageLayerService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coverage-layers")
public class CoverageLayerController {

    private final CoverageLayerService service;

    public CoverageLayerController(CoverageLayerService service) {
        this.service = service;
    }

    /**
     * Upload a KMZ or KML file. Converts to GeoJSON on the backend.
     * Requires ADMIN or ZM with canImportCoverage=true (enforced in service).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public CoverageLayerResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("technology") CoverageTechnology technology,
            @RequestParam("layerType") CoverageLayerType layerType,
            @RequestParam(value = "governorate", required = false) String governorate,
            @RequestParam(value = "color", required = false) String color,
            Authentication authentication
    ) {
        return service.upload(file, name, technology, layerType, governorate, color,
                authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public List<CoverageLayerSummaryResponse> all(Authentication authentication) {
        return service.findAll(authentication.getName());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public CoverageLayerResponse one(@PathVariable String id, Authentication authentication) {
        return service.findById(id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void delete(@PathVariable String id, Authentication authentication) {
        service.delete(id, authentication.getName());
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public CoverageLayerSummaryResponse archive(@PathVariable String id, Authentication authentication) {
        return service.archive(id, authentication.getName());
    }
}

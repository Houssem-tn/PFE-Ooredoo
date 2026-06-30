package com.pfe.coverage.controller;

import com.pfe.coverage.dto.CoverageAnalysisResultResponse;
import com.pfe.coverage.dto.CoverageAnalysisSummaryResponse;
import com.pfe.coverage.dto.CoverageAnalyticsPointsResponse;
import com.pfe.coverage.dto.PointCheckRequest;
import com.pfe.coverage.dto.PointCheckResponse;
import com.pfe.coverage.service.CoverageAnalysisService;
import com.pfe.coverage.service.CoverageAnalyticsPointService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/coverage-analysis")
public class CoverageAnalysisController {

    private final CoverageAnalysisService service;
    private final CoverageAnalyticsPointService analyticsPointService;

    public CoverageAnalysisController(
            CoverageAnalysisService service,
            CoverageAnalyticsPointService analyticsPointService
    ) {
        this.service = service;
        this.analyticsPointService = analyticsPointService;
    }

    /**
     * Runs the coverage analysis for the caller's scope.
     * Accepts an optional { "governorate": "..." } JSON body for filtering.
     */
    @PostMapping("/run")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public CoverageAnalysisSummaryResponse run(
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication
    ) {
        String governorate = (body != null) ? body.get("governorate") : null;
        return service.runAnalysis(authentication.getName(), governorate);
    }

    /** Returns a summary computed from all saved analysis results. */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public CoverageAnalysisSummaryResponse summary(Authentication authentication) {
        return service.getSummary(authentication.getName());
    }

    /** Returns all stored analysis results for the table view. */
    @GetMapping("/results")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public List<CoverageAnalysisResultResponse> results(Authentication authentication) {
        return service.getAllResults(authentication.getName());
    }

    /** Returns the stored result for a single case. */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public CoverageAnalysisResultResponse caseResult(
            @PathVariable String caseId,
            Authentication authentication
    ) {
        return service.getCaseResult(caseId, authentication.getName());
    }

    /** Returns all cases outside every known coverage layer. */
    @GetMapping("/outside-coverage")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public List<CoverageAnalysisResultResponse> outsideCoverage(Authentication authentication) {
        return service.getOutsideCoverage(authentication.getName());
    }

    /**
     * Returns map markers sourced directly from the latest imported Merged Operations
     * file (merged-analytics), filtered by {@code source} (default MES = activation).
     *
     * Only rows with valid coordinates are returned. Supply all four bounding-box
     * params to restrict results to the current viewport; {@code limit} caps the
     * number of markers (server enforces a hard ceiling) to avoid loading huge studies.
     */
    @GetMapping("/analytics-points")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public CoverageAnalyticsPointsResponse analyticsPoints(
            @RequestParam(defaultValue = "MES") String source,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double maxLng,
            @RequestParam(defaultValue = "5000") int limit
    ) {
        return analyticsPointService.getPoints(source, minLat, minLng, maxLat, maxLng, limit);
    }

    /**
     * Checks whether a single coordinate falls inside any active coverage layer.
     * No caseId required — useful for ad-hoc coordinate lookup from the map UI.
     */
    @PostMapping("/check-point")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public PointCheckResponse checkPoint(@RequestBody PointCheckRequest request) {
        if (request.latitude() == null || request.longitude() == null) {
            throw new IllegalArgumentException("latitude and longitude are required.");
        }
        return service.checkPoint(request.latitude(), request.longitude());
    }
}

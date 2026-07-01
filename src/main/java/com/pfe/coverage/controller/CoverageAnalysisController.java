package com.pfe.coverage.controller;

import com.pfe.coverage.dto.CoverageAnalysisResultResponse;
import com.pfe.coverage.dto.CoverageAnalysisSummaryResponse;
import com.pfe.coverage.dto.CoverageAnalyticsGeoJson;
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
     * file (merged-analytics), filtered by {@code source} (default MES = activation),
     * as a GeoJSON {@code FeatureCollection} for the Network Coverage map.
     *
     * <p>Viewport loading: pass {@code bbox=minLng,minLat,maxLng,maxLat} (MapLibre bounds
     * order) to restrict results to the visible map bounds — only points inside are
     * returned, capped server-side. {@code zoom} is accepted for future server-side tuning;
     * optional {@code governorate} / {@code delegation} narrow the result further.</p>
     */
    @GetMapping("/analytics-points")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public CoverageAnalyticsGeoJson.FeatureCollection analyticsPoints(
            @RequestParam(defaultValue = "MES") String source,
            @RequestParam(required = false) String bbox,
            @RequestParam(required = false) Integer zoom,
            @RequestParam(required = false) String governorate,
            @RequestParam(required = false) String delegation,
            @RequestParam(defaultValue = "12000") int limit
    ) {
        Double minLng = null, minLat = null, maxLng = null, maxLat = null;
        if (bbox != null && !bbox.isBlank()) {
            String[] parts = bbox.split(",");
            if (parts.length == 4) {
                try {
                    minLng = Double.parseDouble(parts[0].trim());
                    minLat = Double.parseDouble(parts[1].trim());
                    maxLng = Double.parseDouble(parts[2].trim());
                    maxLat = Double.parseDouble(parts[3].trim());
                } catch (NumberFormatException ignored) {
                    // Malformed bbox → fall back to an unbounded (source-only) query.
                    minLng = minLat = maxLng = maxLat = null;
                }
            }
        }
        return analyticsPointService.getPoints(
                source, minLat, minLng, maxLat, maxLng, governorate, delegation, limit);
    }

    /**
     * Searches MES analytics rows by MSISDN so the map can locate/highlight a case directly.
     * Returns 0..N matches (a single MSISDN normally maps to one row); the UI focuses the match,
     * or lets the user pick when several rows share the MSISDN.
     */
    @GetMapping("/analytics-points/search")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_ZM')")
    public List<CoverageAnalyticsGeoJson.Properties> searchAnalyticsPoints(
            @RequestParam(defaultValue = "MES") String source,
            @RequestParam String msisdn
    ) {
        return analyticsPointService.searchByMsisdn(source, msisdn);
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

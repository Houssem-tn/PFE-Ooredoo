package com.pfe.coverage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.coverage.dto.CoverageAnalysisResultResponse;
import com.pfe.coverage.dto.CoverageAnalysisSummaryResponse;
import com.pfe.coverage.dto.PointCheckResponse;
import com.pfe.coverage.entity.CoverageAnalysisResult;
import com.pfe.coverage.entity.CoverageLayer;
import com.pfe.coverage.enums.CoverageLayerStatus;
import com.pfe.coverage.enums.CoverageLayerType;
import com.pfe.coverage.enums.CoverageTechnology;
import com.pfe.coverage.repository.CoverageAnalysisResultRepository;
import com.pfe.coverage.repository.CoverageLayerRepository;
import com.pfe.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

/**
 * Determines for each activation/complaint case whether its geographic
 * coordinates fall inside any active 4G/5G coverage or WS polygon layer.
 *
 * Point-in-polygon uses the ray-casting algorithm (planar approximation —
 * accurate enough for regional telecom coverage zones in Tunisia).
 *
 * TODO: future optimisations
 *   - Pre-compute bounding boxes to skip irrelevant layers early
 *   - Polygon simplification (Ramer–Douglas–Peucker) before storage
 *   - Spatial index (R-tree) for large fleets of coverage polygons
 *   - RSRP/RSRQ/SINR metrics for signal-quality analysis inside covered zones
 */
@Service
public class CoverageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CoverageAnalysisService.class);

    private final CoverageAnalysisResultRepository resultRepository;
    private final CoverageLayerRepository layerRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public CoverageAnalysisService(
            CoverageAnalysisResultRepository resultRepository,
            CoverageLayerRepository layerRepository,
            CaseRepository caseRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.resultRepository = resultRepository;
        this.layerRepository = layerRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs the full coverage analysis for the caller's scope and persists results.
     * ADMIN can analyse all cases; ZM is restricted to their own cases.
     */
    public CoverageAnalysisSummaryResponse runAnalysis(String callerEmail, String governorateFilter) {
        AppUser caller = resolveUser(callerEmail);

        List<CaseEntity> cases = loadCasesForCaller(caller, governorateFilter);
        List<CoverageLayer> allActiveLayers = layerRepository.findByStatus(CoverageLayerStatus.ACTIVE);

        // Pre-parse GeoJSON outside the per-case loop to avoid repeated parsing
        Map<String, List<ParsedFeature>> layerFeatures = new HashMap<>();
        for (CoverageLayer layer : allActiveLayers) {
            layerFeatures.put(layer.getId(), parseFeatures(layer.getGeoJson(), layer.getId()));
        }

        long invalidCoordinates = 0;
        long totalAnalyzed = 0;
        long count4G = 0, count5G = 0, countWs4G = 0, countWs5G = 0, countOutside = 0;

        List<CoverageAnalysisResult> results = new ArrayList<>();

        for (CaseEntity c : cases) {
            double[] point = parseCoordinates(c.getLatitude(), c.getLongitude());
            if (point == null) {
                invalidCoordinates++;
                continue;
            }

            boolean in4G = false, in5G = false, inWs4G = false, inWs5G = false;
            List<String> matchedIds = new ArrayList<>();

            for (CoverageLayer layer : allActiveLayers) {
                List<ParsedFeature> features = layerFeatures.getOrDefault(layer.getId(), List.of());
                if (!isPointInLayer(point, features)) continue;

                matchedIds.add(layer.getId());

                if (layer.getTechnology() == CoverageTechnology.FOUR_G
                        && layer.getLayerType() == CoverageLayerType.COVERAGE) in4G = true;
                if (layer.getTechnology() == CoverageTechnology.FIVE_G
                        && layer.getLayerType() == CoverageLayerType.COVERAGE) in5G = true;
                if (layer.getTechnology() == CoverageTechnology.FOUR_G
                        && layer.getLayerType() == CoverageLayerType.WS) inWs4G = true;
                if (layer.getTechnology() == CoverageTechnology.FIVE_G
                        && layer.getLayerType() == CoverageLayerType.WS) inWs5G = true;
            }

            String decision = buildDecision(in4G, in5G, inWs4G, inWs5G);

            // Upsert: update existing result or create new
            CoverageAnalysisResult result = resultRepository.findByCaseId(c.getId())
                    .orElse(CoverageAnalysisResult.builder().caseId(c.getId()).build());

            result.setCrmCase(c.getCrmCase());
            result.setCaseType(c.getType() != null ? c.getType().name() : null);
            result.setGovernorate(c.getGouvernorat());
            result.setLatitude(point[0]);
            result.setLongitude(point[1]);
            result.setInside4G(in4G);
            result.setInside5G(in5G);
            result.setInsideWs4G(inWs4G);
            result.setInsideWs5G(inWs5G);
            result.setMatchedLayerIds(matchedIds);
            result.setDecision(decision);
            result.setAnalyzedByUserId(caller.getId());
            result.setAnalyzedByEmail(caller.getEmail());

            results.add(result);
            totalAnalyzed++;

            if (in4G) count4G++;
            if (in5G) count5G++;
            if (inWs4G) countWs4G++;
            if (inWs5G) countWs5G++;
            if (!in4G && !in5G && !inWs4G && !inWs5G) countOutside++;
        }

        resultRepository.saveAll(results);

        return new CoverageAnalysisSummaryResponse(
                cases.size(),
                totalAnalyzed,
                invalidCoordinates,
                count4G,
                count5G,
                countWs4G,
                countWs5G,
                countOutside
        );
    }

    /** Computes the summary from previously saved analysis results. */
    public CoverageAnalysisSummaryResponse getSummary(String callerEmail) {
        resolveUser(callerEmail);
        List<CoverageAnalysisResult> all = resultRepository.findAll();

        long in4G = all.stream().filter(CoverageAnalysisResult::isInside4G).count();
        long in5G = all.stream().filter(CoverageAnalysisResult::isInside5G).count();
        long inWs4G = all.stream().filter(CoverageAnalysisResult::isInsideWs4G).count();
        long inWs5G = all.stream().filter(CoverageAnalysisResult::isInsideWs5G).count();
        long outside = all.stream()
                .filter(r -> !r.isInside4G() && !r.isInside5G() && !r.isInsideWs4G() && !r.isInsideWs5G())
                .count();

        return new CoverageAnalysisSummaryResponse(
                all.size(), all.size(), 0, in4G, in5G, inWs4G, inWs5G, outside);
    }

    /** Returns the stored analysis result for a single case. */
    public CoverageAnalysisResultResponse getCaseResult(String caseId, String callerEmail) {
        resolveUser(callerEmail);
        CoverageAnalysisResult r = resultRepository.findByCaseId(caseId)
                .orElseThrow(() -> new NotFoundException("No analysis result found for case: " + caseId));
        return toResponse(r);
    }

    /** Returns all cases found to be outside every coverage layer. */
    public List<CoverageAnalysisResultResponse> getOutsideCoverage(String callerEmail) {
        resolveUser(callerEmail);
        return resultRepository
                .findByInside4GFalseAndInside5GFalseAndInsideWs4GFalseAndInsideWs5GFalse()
                .stream().map(this::toResponse).toList();
    }

    /** Returns all stored analysis results. */
    public List<CoverageAnalysisResultResponse> getAllResults(String callerEmail) {
        resolveUser(callerEmail);
        return resultRepository.findAll().stream().map(this::toResponse).toList();
    }

    // ── Case loading ──────────────────────────────────────────────────────────

    private List<CaseEntity> loadCasesForCaller(AppUser caller, String governorateFilter) {
        Stream<CaseEntity> stream;

        if (caller.getRole() == UserRole.ROLE_ADMIN) {
            // Admin can see all activation and complaint cases
            stream = Stream.concat(
                    caseRepository.findByType(CaseType.ACTIVATION).stream(),
                    caseRepository.findByType(CaseType.PLAINTE).stream()
            );
        } else if (caller.getRole() == UserRole.ROLE_ZM) {
            // ZM sees only cases where they are the assigned ZM
            String email = caller.getEmail();
            stream = Stream.concat(
                    caseRepository.findByTypeAndZmEmailIgnoreCase(CaseType.ACTIVATION, email).stream(),
                    caseRepository.findByTypeAndZmEmailIgnoreCase(CaseType.PLAINTE, email).stream()
            );
        } else {
            throw new AccessDeniedException("Only ADMIN or ZM can run coverage analysis.");
        }

        if (governorateFilter != null && !governorateFilter.isBlank()) {
            stream = stream.filter(c -> governorateFilter.equalsIgnoreCase(c.getGouvernorat()));
        }

        return stream.toList();
    }

    // ── Point-in-polygon ──────────────────────────────────────────────────────

    /** Parses GeoJSON FeatureCollection into a list of polygon rings for fast lookup. */
    private List<ParsedFeature> parseFeatures(String geoJson, String layerId) {
        if (geoJson == null || geoJson.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            List<ParsedFeature> features = new ArrayList<>();
            for (JsonNode feature : root.path("features")) {
                JsonNode geometry = feature.path("geometry");
                collectRings(geometry, features);
            }
            return features;
        } catch (Exception e) {
            log.warn("Failed to parse GeoJSON for layer {}: {}", layerId, e.getMessage());
            return List.of();
        }
    }

    private void collectRings(JsonNode geometry, List<ParsedFeature> out) {
        String type = geometry.path("type").asText();
        JsonNode coords = geometry.path("coordinates");

        switch (type) {
            case "Polygon" -> {
                // coords[0] = outer ring; [1..n] = holes (ignored for containment check)
                if (!coords.isEmpty()) out.add(new ParsedFeature(toRing(coords.get(0))));
            }
            case "MultiPolygon" -> {
                // coords is array of polygons; each polygon[0] is the outer ring
                for (JsonNode polygon : coords) {
                    if (!polygon.isEmpty()) out.add(new ParsedFeature(toRing(polygon.get(0))));
                }
            }
            case "GeometryCollection" -> {
                for (JsonNode geom : geometry.path("geometries")) collectRings(geom, out);
            }
            // LineString and Point geometries are parsed but skipped for containment
        }
    }

    private double[][] toRing(JsonNode ring) {
        double[][] result = new double[ring.size()][2];
        for (int i = 0; i < ring.size(); i++) {
            result[i][0] = ring.get(i).get(0).asDouble(); // longitude
            result[i][1] = ring.get(i).get(1).asDouble(); // latitude
        }
        return result;
    }

    private boolean isPointInLayer(double[] point, List<ParsedFeature> features) {
        for (ParsedFeature f : features) {
            if (rayCast(point[0], point[1], f.ring())) return true;
        }
        return false;
    }

    /**
     * Ray-casting algorithm for point-in-polygon.
     * point: [latitude, longitude] — ring: double[][longitude, latitude]
     */
    private boolean rayCast(double lat, double lon, double[][] ring) {
        int n = ring.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[i][0], yi = ring[i][1]; // lon, lat of vertex i
            double xj = ring[j][0], yj = ring[j][1]; // lon, lat of vertex j
            if (((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    // ── Decision ──────────────────────────────────────────────────────────────

    private String buildDecision(boolean in4G, boolean in5G, boolean inWs4G, boolean inWs5G) {
        if (in4G && in5G) return "Covered by 4G and 5G";
        if (in4G)         return "Covered by 4G only";
        if (in5G)         return "Covered by 5G only";
        return "Outside known coverage";
    }

    // ── Coordinate parsing ────────────────────────────────────────────────────

    /** Returns [lat, lon] or null if either coordinate is missing/invalid. */
    private double[] parseCoordinates(String latStr, String lonStr) {
        if (latStr == null || latStr.isBlank() || lonStr == null || lonStr.isBlank()) return null;
        try {
            double lat = Double.parseDouble(latStr.trim());
            double lon = Double.parseDouble(lonStr.trim());
            // Basic sanity: reject clearly invalid values
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            return new double[]{lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AppUser resolveUser(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }

    private CoverageAnalysisResultResponse toResponse(CoverageAnalysisResult r) {
        return new CoverageAnalysisResultResponse(
                r.getId(), r.getCaseId(), r.getCrmCase(), r.getCaseType(),
                r.getGovernorate(), r.getLatitude(), r.getLongitude(),
                r.isInside4G(), r.isInside5G(), r.isInsideWs4G(), r.isInsideWs5G(),
                r.getMatchedLayerIds(), r.getDecision(),
                r.getAnalyzedByEmail(), r.getAnalyzedAt()
        );
    }

    /**
     * Checks whether a single coordinate falls inside any active coverage layer.
     * Reuses the same point-in-polygon logic as batch analysis — no code duplication.
     * Does not require a caseId or caller scope; available to both ADMIN and ZM.
     */
    public PointCheckResponse checkPoint(double lat, double lon) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Coordinates out of valid range.");
        }

        List<CoverageLayer> activeLayers = layerRepository.findByStatus(CoverageLayerStatus.ACTIVE);
        double[] point = {lat, lon};

        boolean in4G = false, in5G = false, inWs4G = false, inWs5G = false;
        List<String> matchedNames = new ArrayList<>();

        for (CoverageLayer layer : activeLayers) {
            List<ParsedFeature> features = parseFeatures(layer.getGeoJson(), layer.getId());
            if (!isPointInLayer(point, features)) continue;

            matchedNames.add(layer.getName());

            if (layer.getTechnology() == CoverageTechnology.FOUR_G
                    && layer.getLayerType() == CoverageLayerType.COVERAGE) in4G = true;
            if (layer.getTechnology() == CoverageTechnology.FIVE_G
                    && layer.getLayerType() == CoverageLayerType.COVERAGE) in5G = true;
            if (layer.getTechnology() == CoverageTechnology.FOUR_G
                    && layer.getLayerType() == CoverageLayerType.WS) inWs4G = true;
            if (layer.getTechnology() == CoverageTechnology.FIVE_G
                    && layer.getLayerType() == CoverageLayerType.WS) inWs5G = true;
        }

        return new PointCheckResponse(
                lat, lon, in4G, in5G, inWs4G, inWs5G,
                buildDecision(in4G, in5G, inWs4G, inWs5G),
                matchedNames
        );
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /** Holds a single pre-parsed polygon ring for fast point-in-polygon checks. */
    private record ParsedFeature(double[][] ring) {}
}

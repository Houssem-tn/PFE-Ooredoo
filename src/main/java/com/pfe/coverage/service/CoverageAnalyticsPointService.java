package com.pfe.coverage.service;

import com.pfe.coverage.dto.CoverageAnalyticsPointResponse;
import com.pfe.coverage.dto.CoverageAnalyticsPointsResponse;
import com.pfe.merged_analytics.entity.MergedAnalyticsRowEntity;
import com.pfe.merged_analytics.entity.MergedAnalyticsStudyEntity;
import com.pfe.merged_analytics.model.MergedProcessingStatus;
import com.pfe.merged_analytics.model.MergedSourceLabel;
import com.pfe.merged_analytics.repository.MergedAnalyticsStudyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Coverage map data provider backed by the merged-analytics dataset.
 *
 * <p>Coverage markers no longer come from the Activation / Complaint / Résiliation
 * case collections. Instead they are read directly from the most recently imported
 * Merged Operations file (collection {@code merged_case_analytics_rows}), filtered
 * by {@code SOURCE} — e.g. {@code SOURCE = MES} = activation.</p>
 *
 * <p>No data is duplicated: rows are queried in place via {@link MongoTemplate}
 * with a field projection and a server-side cap, so huge studies (100K+ rows) never
 * load wholesale.</p>
 */
@Service
public class CoverageAnalyticsPointService {

    private static final Logger log = LoggerFactory.getLogger(CoverageAnalyticsPointService.class);

    /** Default number of markers returned when the caller does not specify a limit. */
    private static final int DEFAULT_LIMIT = 5_000;
    /** Hard ceiling, regardless of the requested limit, to protect payload size and the map. */
    private static final int MAX_POINTS = 20_000;

    private final MergedAnalyticsStudyRepository studyRepository;
    private final MongoTemplate mongoTemplate;

    public CoverageAnalyticsPointService(
            MergedAnalyticsStudyRepository studyRepository,
            MongoTemplate mongoTemplate
    ) {
        this.studyRepository = studyRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Returns map markers for the given {@code source} (default MES) from the latest
     * completed merged-analytics study. Only rows with valid coordinates are returned.
     * When all four bounding-box parameters are supplied, results are restricted to that box.
     */
    public CoverageAnalyticsPointsResponse getPoints(
            String source,
            Double minLat, Double minLng, Double maxLat, Double maxLng,
            int limit
    ) {
        String normalizedSource = normalizeSource(source);

        Optional<MergedAnalyticsStudyEntity> latest = studyRepository
                .findFirstByProcessingStatusOrderByUploadedAtDesc(MergedProcessingStatus.COMPLETED);

        if (latest.isEmpty()) {
            log.info("coverage analytics-points: no completed merged-analytics study found");
            return new CoverageAnalyticsPointsResponse(null, normalizedSource, 0, 0, false, List.of());
        }

        String studyId = latest.get().getStudyId();
        Criteria criteria = buildCriteria(studyId, normalizedSource, minLat, minLng, maxLat, maxLng);

        // Count uses its own (unlimited) query so the cap below never affects the total.
        long total = mongoTemplate.count(new Query(criteria), MergedAnalyticsRowEntity.class);

        int safeLimit = Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_POINTS);

        Query fetch = new Query(criteria).limit(safeLimit);
        fetch.fields()
                .include("crmCase").include("source")
                .include("latitudeSiteValue").include("longitudeSiteValue")
                .include("gouvernorat").include("delegation").include("localite")
                .include("msisdn")
                .include("debitDlValue").include("debitUlValue")
                .include("rsrpValue").include("rsrqValue").include("sinrValue");

        List<MergedAnalyticsRowEntity> rows = mongoTemplate.find(fetch, MergedAnalyticsRowEntity.class);
        List<CoverageAnalyticsPointResponse> points = new ArrayList<>(rows.size());
        for (MergedAnalyticsRowEntity r : rows) {
            points.add(toPoint(r));
        }

        boolean capped = total > points.size();
        log.info("coverage analytics-points: studyId={} source={} total={} returned={} capped={}",
                studyId, normalizedSource, total, points.size(), capped);

        return new CoverageAnalyticsPointsResponse(
                studyId, normalizedSource, total, points.size(), capped, points);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Criteria buildCriteria(String studyId, String normalizedSource,
                                   Double minLat, Double minLng, Double maxLat, Double maxLng) {
        List<Criteria> ands = new ArrayList<>();
        ands.add(Criteria.where("studyId").is(studyId));
        ands.add(Criteria.where("source").is(normalizedSource));

        // gte/lte against a numeric range naturally excludes missing (null) coordinates,
        // so the bounding-box branch already enforces "valid coordinates only".
        boolean hasLatBox = minLat != null && maxLat != null;
        boolean hasLngBox = minLng != null && maxLng != null;

        ands.add(hasLatBox
                ? Criteria.where("latitudeSiteValue").gte(minLat).lte(maxLat)
                : Criteria.where("latitudeSiteValue").ne(null));
        ands.add(hasLngBox
                ? Criteria.where("longitudeSiteValue").gte(minLng).lte(maxLng)
                : Criteria.where("longitudeSiteValue").ne(null));

        return new Criteria().andOperator(ands.toArray(new Criteria[0]));
    }

    private String normalizeSource(String source) {
        String normalized = MergedSourceLabel.normalizeSource(source);
        if (normalized != null) return normalized;
        // Unknown/blank source: fall back to a sane uppercase token (defaults to MES).
        if (source == null || source.isBlank()) return "MES";
        return source.trim().toUpperCase(Locale.ROOT);
    }

    private CoverageAnalyticsPointResponse toPoint(MergedAnalyticsRowEntity r) {
        return new CoverageAnalyticsPointResponse(
                r.getId(),
                r.getCrmCase(),
                r.getSource(),
                r.getLatitudeSiteValue(),
                r.getLongitudeSiteValue(),
                r.getGouvernorat(),
                r.getDelegation(),
                r.getLocalite(),
                r.getMsisdn(),
                r.getDebitDlValue(),
                r.getDebitUlValue(),
                r.getRsrpValue(),
                r.getRsrqValue(),
                r.getSinrValue()
        );
    }
}

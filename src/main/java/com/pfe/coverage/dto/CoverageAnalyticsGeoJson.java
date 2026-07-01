package com.pfe.coverage.dto;

import java.util.List;

/**
 * GeoJSON payload for the Network Coverage map.
 *
 * <p>Returned by {@code GET /api/v1/coverage-analysis/analytics-points}. The shape is a
 * standard GeoJSON {@code FeatureCollection} so it can be handed straight to a MapLibre
 * {@code geojson} source with client-side clustering — no client transformation needed.
 * The extra top-level members ({@code studyId}, {@code totalMatching}, {@code returned},
 * {@code capped}) are GeoJSON foreign members: MapLibre ignores them while the UI uses them
 * for its summary/loading state.</p>
 *
 * <p>Only points inside the requested bounding box are returned, capped server-side. When
 * {@code capped == true} the returned features are a uniform random sample of the matching
 * rows (so zoomed-out clusters represent the whole area rather than an index-ordered slice).</p>
 */
public final class CoverageAnalyticsGeoJson {

    private CoverageAnalyticsGeoJson() {}

    public record FeatureCollection(
            String type,            // always "FeatureCollection"
            String studyId,
            String source,
            long totalMatching,
            int returned,
            boolean capped,
            List<Feature> features
    ) {
        public static FeatureCollection of(String studyId, String source, long totalMatching,
                                           boolean capped, List<Feature> features) {
            return new FeatureCollection("FeatureCollection", studyId, source,
                    totalMatching, features.size(), capped, features);
        }
    }

    public record Feature(
            String type,            // always "Feature"
            Geometry geometry,
            Properties properties
    ) {
        public static Feature point(double lng, double lat, Properties props) {
            return new Feature("Feature", new Geometry("Point", new double[]{lng, lat}), props);
        }
    }

    /** GeoJSON geometry; {@code coordinates} are serialized as {@code [longitude, latitude]}. */
    public record Geometry(String type, double[] coordinates) {}

    /** Per-marker attributes exposed to map popups and the results table. */
    public record Properties(
            String id,
            String caseCrm,
            String source,
            double latitude,
            double longitude,
            String governorate,
            String delegation,
            String locality,
            String msisdn,
            Double debitDl,
            Double debitUl,
            Double rsrp,
            Double rsrq,
            Double sinr
    ) {}
}

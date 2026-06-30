package com.pfe.coverage.dto;

/**
 * A single map marker sourced directly from a merged-analytics row.
 *
 * Replaces the old case-entity based markers: coverage points now come from the
 * imported Merged Operations file (e.g. SOURCE = MES → activation) instead of the
 * Activation / Complaint / Résiliation case collections.
 */
public record CoverageAnalyticsPointResponse(
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

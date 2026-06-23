package com.pfe.case_management.dto;

import java.time.LocalDate;

/**
 * Carries the relevant fields from one PLT Excel row to {@code CaseService}
 * for complaint case creation. Built by {@code TodoImportService} from a
 * {@code ParsedAnalyticsRow}.
 */
public record PltCaseRow(
        String crmCase,
        String contact1,
        String contact2,
        String msisdn,
        String gouvernorat,
        String description,
        String rawLatitude,
        String rawLongitude,
        String normalizedLatitude,
        String normalizedLongitude,
        LocalDate openDateSurTimosDate,
        String sttFromExcel,
        String statutFromExcel,
        int rowNumber
) {
}

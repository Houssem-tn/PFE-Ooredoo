package com.pfe.case_management.dto;

import java.time.LocalDate;

/**
 * Carries one ACT TIMOS import row from {@code TodoImportService} to
 * {@code CaseService} so activation cases can be created through the same
 * ZM import workflow used by other field-import modules.
 */
public record ActCaseRow(
        String crmCase,
        LocalDate dateCreationCrmDate,
        String contactClient,
        String msisdn,
        String gouvernorat,
        String delegation,
        String localite,
        String rawLatitude,
        String rawLongitude,
        String normalizedLatitude,
        String normalizedLongitude,
        String offre,
        String desPack,
        LocalDate openDateSurTimosDate,
        String sttFromExcel,
        String statutFromExcel,
        int rowNumber
) {
}

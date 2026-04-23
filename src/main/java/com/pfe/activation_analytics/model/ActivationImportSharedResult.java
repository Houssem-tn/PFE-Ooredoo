package com.pfe.activation_analytics.model;

import java.util.List;

public record ActivationImportSharedResult(
        String sourceFileName,
        int totalRows,
        int processableRows,
        int validNewRows,
        int invalidRows,
        int duplicateRows,
        int distinctStts,
        int distinctGovernorates,
        int enCoursRows,
        int effectueRows,
        int termineRows,
        int actionableRows,
        List<ActivationImportSharedRow> rows,
        List<ActivationImportSharedRow> analyticsRowsList,
        List<ActivationImportSharedRow> processableRowsList,
        List<ActivationImportSharedRow> invalidRowsList
) {
    public List<ActivationImportSharedRow> analyticsRowsToStore() {
        return analyticsRowsList;
    }

    public List<ActivationImportSharedRow> rowsForDistribution() {
        return processableRowsList;
    }

    public List<ActivationImportSharedRow> invalidRowsOnly() {
        return invalidRowsList;
    }
}

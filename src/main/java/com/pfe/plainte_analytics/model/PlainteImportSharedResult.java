package com.pfe.plainte_analytics.model;

import java.util.List;

public record PlainteImportSharedResult(
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
        List<PlainteImportSharedRow> rows,
        List<PlainteImportSharedRow> analyticsRowsList,
        List<PlainteImportSharedRow> processableRowsList,
        List<PlainteImportSharedRow> invalidRowsList
) {
    public List<PlainteImportSharedRow> analyticsRowsToStore() {
        return analyticsRowsList;
    }

    public List<PlainteImportSharedRow> rowsForDistribution() {
        return processableRowsList;
    }

    public List<PlainteImportSharedRow> invalidRowsOnly() {
        return invalidRowsList;
    }
}

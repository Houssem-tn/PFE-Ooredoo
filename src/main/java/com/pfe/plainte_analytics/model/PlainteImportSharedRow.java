package com.pfe.plainte_analytics.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record PlainteImportSharedRow(
        int rowNumber,
        String crmCase,
        String crmCaseNormalized,
        String dateCreationCrm,
        LocalDate dateCreationCrmDate,
        String openingDateSurTimos,
        LocalDate openingDateSurTimosDate,
        String statut,
        String client,
        String contactClient,
        String contactCl2,
        String commentaire,
        String msisdn,
        String latitudeSite,
        Double latitudeSiteValue,
        String longitudeSite,
        Double longitudeSiteValue,
        String localite,
        String delegation,
        String gouvernorat,
        String gouvernoratNormalized,
        String offre,
        String desPack,
        String nameStt,
        String nameSttNormalized,
        String dateAffectationStt,
        LocalDate dateAffectationSttDate,
        String repRdv,
        String datePriseRdv,
        LocalDate datePriseRdvDate,
        String causeInjoignabilite,
        String causeAbandon,
        String detail,
        String cmtRdv,
        String repTravauxStt,
        String dateFinTrv,
        LocalDate dateFinTrvDate,
        String notifResolution,
        String cmtTrvStt,
        String repZm,
        String cmtZm,
        String metrageCable,
        Double metrageCableValue,
        String nombreDePv,
        Double nombreDePvValue,
        String debitDl,
        Double debitDlValue,
        String debitUl,
        Double debitUlValue,
        String rsrp,
        Double rsrpValue,
        String rsrq,
        Double rsrqValue,
        String sinr,
        Double sinrValue,
        String siteSecteur,
        String typeOffreSpeed,
        String typeClient,
        String source,
        String description,
        String openDateActivation,
        LocalDate openDateActivationDate,
        Integer assignmentBusinessDays,
        Integer rdvBusinessDays,
        Integer executionBusinessDays,
        Integer endToEndBusinessDays,
        List<String> validationIssues,
        boolean duplicateInFile,
        boolean duplicateInAnalyticsDataset
) {
    private static final String DUPLICATE_IN_FILE_ISSUE = "Duplicate CRM_CASE inside the selected file";
    private static final String DUPLICATE_IN_DATASET_ISSUE =
            "Duplicate CRM_CASE already imported in the admin plainte analytics dataset";

    public boolean analyticsInsertableRow() {
        return validationIssues().isEmpty() && !duplicateInFile() && !duplicateInAnalyticsDataset();
    }

    public boolean distributionCandidateRow() {
        return validationIssues().isEmpty() && !duplicateInFile();
    }

    public boolean duplicateOnlyRow() {
        return validationIssues().isEmpty() && (duplicateInFile() || duplicateInAnalyticsDataset());
    }

    public boolean invalidOnlyRow() {
        return !validationIssues().isEmpty();
    }

    public String category() {
        if (analyticsInsertableRow()) {
            return "valid_new";
        }
        if (duplicateOnlyRow()) {
            return "duplicate";
        }
        return "invalid";
    }

    public List<String> issues() {
        List<String> issues = new ArrayList<>(validationIssues());
        if (duplicateInFile()) {
            issues.add(DUPLICATE_IN_FILE_ISSUE);
        }
        if (duplicateInAnalyticsDataset()) {
            issues.add(DUPLICATE_IN_DATASET_ISSUE);
        }
        return List.copyOf(issues);
    }

    public PlainteImportSharedRow withDuplicateFlags(boolean duplicateInFile, boolean duplicateInAnalyticsDataset) {
        return new PlainteImportSharedRow(
                rowNumber,
                crmCase,
                crmCaseNormalized,
                dateCreationCrm,
                dateCreationCrmDate,
                openingDateSurTimos,
                openingDateSurTimosDate,
                statut,
                client,
                contactClient,
                contactCl2,
                commentaire,
                msisdn,
                latitudeSite,
                latitudeSiteValue,
                longitudeSite,
                longitudeSiteValue,
                localite,
                delegation,
                gouvernorat,
                gouvernoratNormalized,
                offre,
                desPack,
                nameStt,
                nameSttNormalized,
                dateAffectationStt,
                dateAffectationSttDate,
                repRdv,
                datePriseRdv,
                datePriseRdvDate,
                causeInjoignabilite,
                causeAbandon,
                detail,
                cmtRdv,
                repTravauxStt,
                dateFinTrv,
                dateFinTrvDate,
                notifResolution,
                cmtTrvStt,
                repZm,
                cmtZm,
                metrageCable,
                metrageCableValue,
                nombreDePv,
                nombreDePvValue,
                debitDl,
                debitDlValue,
                debitUl,
                debitUlValue,
                rsrp,
                rsrpValue,
                rsrq,
                rsrqValue,
                sinr,
                sinrValue,
                siteSecteur,
                typeOffreSpeed,
                typeClient,
                source,
                description,
                openDateActivation,
                openDateActivationDate,
                assignmentBusinessDays,
                rdvBusinessDays,
                executionBusinessDays,
                endToEndBusinessDays,
                validationIssues,
                duplicateInFile,
                duplicateInAnalyticsDataset
        );
    }
}

package com.pfe.merged_analytics.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "merged_case_analytics_rows")
@CompoundIndexes({
    @CompoundIndex(name = "merged_rows_study_idx",          def = "{'studyId': 1}"),
    @CompoundIndex(name = "merged_rows_study_source_idx",   def = "{'studyId': 1, 'source': 1}"),
    @CompoundIndex(name = "merged_rows_study_zm_idx",       def = "{'studyId': 1, 'zoneManagerNormalized': 1}"),
    @CompoundIndex(name = "merged_rows_study_stt_idx",      def = "{'studyId': 1, 'nameSttNormalized': 1}"),
    @CompoundIndex(name = "merged_rows_study_status_idx",   def = "{'studyId': 1, 'statut': 1}"),
    @CompoundIndex(name = "merged_rows_study_gov_idx",      def = "{'studyId': 1, 'gouvernoratNormalized': 1}"),
    @CompoundIndex(name = "merged_rows_study_date_crm_idx", def = "{'studyId': 1, 'dateCreationCrmDate': 1}"),
    @CompoundIndex(name = "merged_rows_study_date_fin_idx", def = "{'studyId': 1, 'dateFinTrvDate': 1}"),
    @CompoundIndex(name = "merged_rows_study_src_zm_idx",   def = "{'studyId': 1, 'source': 1, 'zoneManagerNormalized': 1}"),
    @CompoundIndex(name = "merged_rows_study_src_stt_idx",  def = "{'studyId': 1, 'source': 1, 'nameSttNormalized': 1}"),
    @CompoundIndex(name = "merged_rows_study_zm_stt_idx",   def = "{'studyId': 1, 'zoneManagerNormalized': 1, 'nameSttNormalized': 1}")
})
public class MergedAnalyticsRowEntity {

    @Id
    private String id;
    private String studyId;
    private int rowNumber;

    private String crmCase;
    private String dateCreationCrm;
    private LocalDate dateCreationCrmDate;
    private String openingDateSurTimos;
    private LocalDate openingDateSurTimosDate;
    private String statut;
    private String client;
    private String contactClient;
    private String commentaire;
    private String msisdn;

    private String latitudeSite;
    private Double latitudeSiteValue;
    private String longitudeSite;
    private Double longitudeSiteValue;

    private String localite;
    private String delegation;
    private String gouvernorat;
    private String gouvernoratNormalized;

    private String offre;
    private String desPack;

    private String nameStt;
    private String nameSttNormalized;
    private String dateAffectationStt;
    private LocalDate dateAffectationSttDate;

    private String repRdv;
    private String datePriseRdv;
    private LocalDate datePriseRdvDate;
    private String causeInjoignabilite;
    private String causeAbandon;
    private String detail;
    private String cmtRdv;

    private String repTravauxStt;
    private String dateFinTrv;
    private LocalDate dateFinTrvDate;
    private String cmtTrvStt;

    private String repZm;
    private String cmtZm;

    private String metrageCable;
    private Double metrageCableValue;
    private String nombreDePv;
    private Double nombreDePvValue;
    private String debitDl;
    private Double debitDlValue;
    private String debitUl;
    private Double debitUlValue;
    private String rsrp;
    private Double rsrpValue;
    private String rsrq;
    private Double rsrqValue;
    private String sinr;
    private Double sinrValue;

    private String siteSecteur;
    private String typeOffreSpeed;
    private String typeClient;

    private String source;
    private String sourceNormalized;
    private String sourceLabel;

    private String description;
    private String contactCl2;
    private String entite;
    private String label;
    private String notifResolution;

    private String openDateActivation;
    private LocalDate openDateActivationDate;
    private String actCmtZm;
    private String actRepZm;
    private String dateTravauxResiliation;
    private LocalDate dateTravauxResiliationDate;
    private String installer;

    private String slaCrm;
    private Double slaCrmValue;
    private String slaTech;
    private Double slaTechValue;
    private String slaCrmLabel;
    private String slaTechLabel;

    private String zoneManager;
    private String zoneManagerNormalized;

    private Instant createdAt;
}

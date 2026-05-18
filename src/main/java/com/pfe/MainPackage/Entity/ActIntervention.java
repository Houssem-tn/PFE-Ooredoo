package com.pfe.MainPackage.Entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "act_interventions")
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ActIntervention {
    @Id
    private String id;

    // ===== CRM columns =====
    @Indexed(unique = true)
    private String crmCase;                 // CRM_CASE
    private Instant dateCreationCrm;        // DATE_CREATION_CRM
    private String statutRawCrm;            // optional if CRM has its own status label

    // ===== TIMOS/FSM workflow =====
    private Instant openingDateSurTimos;    // OPENING_DATE_SUR_TIMOS
    private ActStatus statut;               // STATUT (workflow status)

    // ===== Client/Site =====
    private String client;                  // CLIENT
    private String contactClient;           // CONTACT_CLIENT

    public void setId(String id) {
        this.id = id;
    }

    public void setCrmCase(String crmCase) {
        this.crmCase = crmCase;
    }

    public void setDateCreationCrm(Instant dateCreationCrm) {
        this.dateCreationCrm = dateCreationCrm;
    }

    public void setStatutRawCrm(String statutRawCrm) {
        this.statutRawCrm = statutRawCrm;
    }

    public void setOpeningDateSurTimos(Instant openingDateSurTimos) {
        this.openingDateSurTimos = openingDateSurTimos;
    }

    public void setStatut(ActStatus statut) {
        this.statut = statut;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public void setContactClient(String contactClient) {
        this.contactClient = contactClient;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public void setLatitudeSite(Double latitudeSite) {
        this.latitudeSite = latitudeSite;
    }

    public void setLongitudeSite(Double longitudeSite) {
        this.longitudeSite = longitudeSite;
    }

    public void setLocalite(String localite) {
        this.localite = localite;
    }

    public void setDelegation(String delegation) {
        this.delegation = delegation;
    }

    public void setGouvernorat(String gouvernorat) {
        this.gouvernorat = gouvernorat;
    }

    public void setOffre(String offre) {
        this.offre = offre;
    }

    public void setDesPack(String desPack) {
        this.desPack = desPack;
    }

    public void setNameStt(String nameStt) {
        this.nameStt = nameStt;
    }

    public void setSttCompanyId(String sttCompanyId) {
        this.sttCompanyId = sttCompanyId;
    }

    public void setDateAffectationStt(Instant dateAffectationStt) {
        this.dateAffectationStt = dateAffectationStt;
    }

    public void setRepRdv(YesNo repRdv) {
        this.repRdv = repRdv;
    }

    public void setDatePriseRdv(Instant datePriseRdv) {
        this.datePriseRdv = datePriseRdv;
    }

    public void setCauseInjoignabilite(String causeInjoignabilite) {
        this.causeInjoignabilite = causeInjoignabilite;
    }

    public void setCauseAbandon(String causeAbandon) {
        this.causeAbandon = causeAbandon;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public void setCmtRdv(String cmtRdv) {
        this.cmtRdv = cmtRdv;
    }

    public void setRepTravauxStt(WorkResult repTravauxStt) {
        this.repTravauxStt = repTravauxStt;
    }

    public void setDateFinTrv(Instant dateFinTrv) {
        this.dateFinTrv = dateFinTrv;
    }

    public void setCmtTrvStt(String cmtTrvStt) {
        this.cmtTrvStt = cmtTrvStt;
    }

    public void setRepZm(YesNo repZm) {
        this.repZm = repZm;
    }

    public void setCmtZm(String cmtZm) {
        this.cmtZm = cmtZm;
    }

    public void setMetrageCable(Double metrageCable) {
        this.metrageCable = metrageCable;
    }

    public void setNombreDePv(Integer nombreDePv) {
        this.nombreDePv = nombreDePv;
    }

    public void setDebitDl(Double debitDl) {
        this.debitDl = debitDl;
    }

    public void setDebitUl(Double debitUl) {
        this.debitUl = debitUl;
    }

    public void setRsrp(Double rsrp) {
        this.rsrp = rsrp;
    }

    public void setRsrq(Double rsrq) {
        this.rsrq = rsrq;
    }

    public void setSinr(Double sinr) {
        this.sinr = sinr;
    }

    public void setSiteSecteur(String siteSecteur) {
        this.siteSecteur = siteSecteur;
    }

    public void setTechnicianUserId(String technicianUserId) {
        this.technicianUserId = technicianUserId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getCrmCase() {
        return crmCase;
    }

    public Instant getDateCreationCrm() {
        return dateCreationCrm;
    }

    public String getStatutRawCrm() {
        return statutRawCrm;
    }

    public Instant getOpeningDateSurTimos() {
        return openingDateSurTimos;
    }

    public ActStatus getStatut() {
        return statut;
    }

    public String getClient() {
        return client;
    }

    public String getContactClient() {
        return contactClient;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public Double getLatitudeSite() {
        return latitudeSite;
    }

    public Double getLongitudeSite() {
        return longitudeSite;
    }

    public String getLocalite() {
        return localite;
    }

    public String getDelegation() {
        return delegation;
    }

    public String getGouvernorat() {
        return gouvernorat;
    }

    public String getOffre() {
        return offre;
    }

    public String getDesPack() {
        return desPack;
    }

    public String getNameStt() {
        return nameStt;
    }

    public String getSttCompanyId() {
        return sttCompanyId;
    }

    public Instant getDateAffectationStt() {
        return dateAffectationStt;
    }

    public YesNo getRepRdv() {
        return repRdv;
    }

    public Instant getDatePriseRdv() {
        return datePriseRdv;
    }

    public String getCauseInjoignabilite() {
        return causeInjoignabilite;
    }

    public String getCauseAbandon() {
        return causeAbandon;
    }

    public String getDetail() {
        return detail;
    }

    public String getCmtRdv() {
        return cmtRdv;
    }

    public WorkResult getRepTravauxStt() {
        return repTravauxStt;
    }

    public Instant getDateFinTrv() {
        return dateFinTrv;
    }

    public String getCmtTrvStt() {
        return cmtTrvStt;
    }

    public YesNo getRepZm() {
        return repZm;
    }

    public String getCmtZm() {
        return cmtZm;
    }

    public Double getMetrageCable() {
        return metrageCable;
    }

    public Integer getNombreDePv() {
        return nombreDePv;
    }

    public Double getDebitDl() {
        return debitDl;
    }

    public Double getDebitUl() {
        return debitUl;
    }

    public Double getRsrp() {
        return rsrp;
    }

    public Double getRsrq() {
        return rsrq;
    }

    public Double getSinr() {
        return sinr;
    }

    public String getSiteSecteur() {
        return siteSecteur;
    }

    public String getTechnicianUserId() {
        return technicianUserId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private String commentaire;             // COMMENTAIRE
    private String msisdn;                  // MSISDN

    private Double latitudeSite;            // LATITUDE_SITE
    private Double longitudeSite;           // LONGITUDE_SITE
    private String localite;                // LOCALITE
    private String delegation;              // DELEGATION
    private String gouvernorat;             // GOUVERNORAT

    private String offre;                   // OFFRE
    private String desPack;                 // DES_PACK

    // ===== STT assignment =====
    private String nameStt;                 // NAME_STT
    private String sttCompanyId;            // internal id for STT company
    private Instant dateAffectationStt;     // DATE_AFFECTATION_STT

    // ===== RDV =====
    private YesNo repRdv;                   // REP_RDV
    private Instant datePriseRdv;           // DATE_PRISE_RDV
    private String causeInjoignabilite;     // CAUSE_INJOIGNABILITE
    private String causeAbandon;            // CAUSE_ABANDON
    private String detail;                  // DETAIL
    private String cmtRdv;                  // CMT_RDV

    // ===== Work / STT pre-validation =====
    private WorkResult repTravauxStt;       // REP_TRAVAUX_STT
    private Instant dateFinTrv;             // DATE_FIN_TRV
    private String cmtTrvStt;               // CMT_TRV_STT

    // ===== ZM validation =====
    private YesNo repZm;                    // REP_ZM
    private String cmtZm;                   // CMT_ZM

    // ===== Metrics extracted (from evidence/pipeline) =====
    private Double metrageCable;            // METRAGE_CABLE
    private Integer nombreDePv;             // NOMBRE_DE_PV
    private Double debitDl;                 // DEBIT_DL
    private Double debitUl;                 // DEBIT_UL
    private Double rsrp;                    // RSRP
    private Double rsrq;                    // RSRQ
    private Double sinr;                    // SINR
    private String siteSecteur;             // SITESECTEUR

    // ===== Tracking =====
    private String technicianUserId;        // assigned tech (STT assigns)
    private String zoneId;                  // for ZM filtering

    private Instant createdAt = Instant.now();
    private Instant updatedAt;
}

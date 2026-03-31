package com.pfe.case_management.model;

import java.time.Instant;

public class RdvInfo {

    private Boolean joignable;
    private Instant datePriseRdv;
    private String causeInjoignabilite;
    private String causeAbandon;
    private String detail;

    public Boolean getJoignable() {
        return joignable;
    }

    public void setJoignable(Boolean joignable) {
        this.joignable = joignable;
    }

    public Instant getDatePriseRdv() {
        return datePriseRdv;
    }

    public void setDatePriseRdv(Instant datePriseRdv) {
        this.datePriseRdv = datePriseRdv;
    }

    public String getCauseInjoignabilite() {
        return causeInjoignabilite;
    }

    public void setCauseInjoignabilite(String causeInjoignabilite) {
        this.causeInjoignabilite = causeInjoignabilite;
    }

    public String getCauseAbandon() {
        return causeAbandon;
    }

    public void setCauseAbandon(String causeAbandon) {
        this.causeAbandon = causeAbandon;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getCmtRdv() {
        return cmtRdv;
    }

    public void setCmtRdv(String cmtRdv) {
        this.cmtRdv = cmtRdv;
    }

    public Boolean getAbandon() {
        return abandon;
    }

    public void setAbandon(Boolean abandon) {
        this.abandon = abandon;
    }

    private String cmtRdv;
    private Boolean abandon;

    // getters/setters
}
package com.pfe.case_management.model;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkInfo {
    private String repTravauxStt;
    private Instant dateFinTrv;

    public String getRepTravauxStt() {
        return repTravauxStt;
    }

    public void setRepTravauxStt(String repTravauxStt) {
        this.repTravauxStt = repTravauxStt;
    }

    public Instant getDateFinTrv() {
        return dateFinTrv;
    }

    public void setDateFinTrv(Instant dateFinTrv) {
        this.dateFinTrv = dateFinTrv;
    }

    public String getCmtTrvStt() {
        return cmtTrvStt;
    }

    public void setCmtTrvStt(String cmtTrvStt) {
        this.cmtTrvStt = cmtTrvStt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    private String cmtTrvStt;
    private Instant updatedAt;
    private String updatedBy;
    private Double locationLatitude;
    private Double locationLongitude;
    private Instant locationCapturedAt;
    private Double locationAccuracyMeters;
    private List<UsedEquipmentItem> usedEquipment;
    private String stockAppliedTechId;
}

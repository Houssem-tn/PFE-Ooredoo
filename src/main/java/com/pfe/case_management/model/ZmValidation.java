package com.pfe.case_management.model;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZmValidation {
    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getCmtZm() {
        return cmtZm;
    }

    public void setCmtZm(String cmtZm) {
        this.cmtZm = cmtZm;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public String getValidatedBy() {
        return validatedBy;
    }

    public void setValidatedBy(String validatedBy) {
        this.validatedBy = validatedBy;
    }

    private Boolean approved;
    private String cmtZm;
    private Instant validatedAt;
    private String validatedBy;
}
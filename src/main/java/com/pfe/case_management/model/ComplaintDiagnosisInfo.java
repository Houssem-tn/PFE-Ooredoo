package com.pfe.case_management.model;

import com.pfe.case_management.enums.ComplaintCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintDiagnosisInfo {
    private ComplaintCategory complaintCategory;
    private String rootCause;
    private String diagnosisComment;
    private Boolean requiresFieldWork;
    private Boolean requiresTechIntervention;
    private Boolean requiresEscalation;
    private Instant diagnosedAt;
    private String diagnosedBy;
}

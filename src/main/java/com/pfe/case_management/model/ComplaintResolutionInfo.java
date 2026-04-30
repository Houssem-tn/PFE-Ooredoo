package com.pfe.case_management.model;

import com.pfe.case_management.enums.ResolutionValidationStatus;
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
public class ComplaintResolutionInfo {
    private String resolutionType;
    private String resolutionComment;
    private ResolutionValidationStatus validationStatus;
    private Instant submittedAt;
    private String submittedBy;
    private Instant validatedAt;
    private String validatedBy;
    private Instant reopenedAt;
    private String reopenedBy;
}

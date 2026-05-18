package com.pfe.stock_management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RecordExternalApprovalRequest {

    private String approvalReference;
    private String approvalNote;

    @NotBlank(message = "RDV date is required")
    private String rdvDate;

    @NotBlank(message = "RDV location is required")
    private String rdvLocation;

    private String rdvNote;
}

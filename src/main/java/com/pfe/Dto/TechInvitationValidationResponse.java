package com.pfe.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechInvitationValidationResponse {

    private boolean valid;
    private boolean expired;
    private boolean used;

    private String message;

    // Present only when token is valid
    private String email;
    private String sttId;
    private String sttName;
}
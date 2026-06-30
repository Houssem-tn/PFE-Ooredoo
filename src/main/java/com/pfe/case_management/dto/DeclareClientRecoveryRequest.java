package com.pfe.case_management.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Payload for a RES client-material recovery declaration. The declared items are recorded on the
 * case and linked to a material return (source = résiliation recovery) so the STT can hand the
 * material back to the ZM/depot through the existing return workflow.
 */
public record DeclareClientRecoveryRequest(
        @NotEmpty(message = "Declare at least one recovered item")
        @Valid List<RecoveredEquipmentRequestItem> items,
        String comment
) {
}

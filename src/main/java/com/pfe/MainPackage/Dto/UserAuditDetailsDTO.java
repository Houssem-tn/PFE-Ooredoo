package com.pfe.MainPackage.Dto;

import com.pfe.case_management.dto.PerformanceSummaryResponse;

public record UserAuditDetailsDTO(
        UserDTO user,
        UserAuditActorDTO validator,
        UserAuditActorDTO assignedStt,
        UserAuditActorDTO assignedZm,
        UserAuditActorDTO invitedBy,
        UserAuditActorDTO invitationZm,
        UserInvitationAuditDTO invitation,
        UserHierarchyAuditDTO hierarchy,
        UserRoleProfileDTO roleProfile,
        PerformanceSummaryResponse performance
) {
}

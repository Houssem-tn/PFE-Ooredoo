package com.pfe.MainPackage.Dto;

import java.util.List;

public record UserHierarchyAuditDTO(
        long directReportCount,
        long sttCount,
        long techCount,
        List<UserAuditActorDTO> directReports
) {
}

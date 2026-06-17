package com.pfe.case_management.dto;

import java.util.List;

/** One page of intervention audit records (case-centric), newest activity first. */
public record InterventionAuditPageDto(
        List<InterventionAuditDto> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}

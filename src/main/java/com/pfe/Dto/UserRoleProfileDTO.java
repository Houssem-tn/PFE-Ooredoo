package com.pfe.Dto;

public record UserRoleProfileDTO(
        String boutiqueName,
        String boutiqueCode,
        String boutiqueAddress,
        String serviceCenter,
        String department,
        String matricule,
        String skillSet,
        String region,
        Boolean available,
        String zoneName,
        String zoneCode,
        String coverage
) {
}

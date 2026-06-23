package com.pfe.MainPackage.Dto;

import com.pfe.MainPackage.Entity.AccountStatus;

import java.time.Instant;

public record TechnicianListItemDTO(
        String id,
        String fullName,
        String email,
        String phone,
        AccountStatus status,
        boolean enabled,
        Instant createdAt
) {}
package com.pfe.Dto;

import com.pfe.Entity.AccountStatus;

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
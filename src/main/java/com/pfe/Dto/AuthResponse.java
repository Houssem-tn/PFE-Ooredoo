package com.pfe.Dto;

import com.pfe.Entity.UserRole;

public record AuthResponse(
        String token,
        String tokenType,
        UserRole role,
        UserDTO user
) {}

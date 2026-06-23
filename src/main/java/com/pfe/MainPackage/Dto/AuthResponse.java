package com.pfe.MainPackage.Dto;

import com.pfe.MainPackage.Entity.UserRole;

public record AuthResponse(
        String token,
        String tokenType,
        UserRole role,
        UserDTO user
) {}

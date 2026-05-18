package com.pfe.MainPackage.Dto;

import com.pfe.MainPackage.Entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public  record UserUpdateRequest(
@Size(min = 3, max = 120)
String fullName,
@Email
String email,
UserRole role,
Boolean enabled,
Boolean canCreateDpm,
@Size(min = 6, max = 120) String password
) {}

package com.pfe.mapper;

import com.pfe.Dto.UserDTO;
import com.pfe.Entity.AppUser;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDTO toDto(AppUser user);
}

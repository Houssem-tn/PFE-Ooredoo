package com.pfe.mapper;

import com.pfe.Dto.UserDTO;
import com.pfe.Entity.AppUser;
import com.pfe.Service.FileStorageService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    @Autowired
    protected FileStorageService fileStorageService;

    @Mapping(target = "profileImageUrl", expression = "java(resolveProfileImageUrl(user))")
    @Mapping(target = "profileThumbnailUrl", expression = "java(resolveProfileImageUrl(user))")
    public abstract UserDTO toDto(AppUser user);

    protected String resolveProfileImageUrl(AppUser user) {
        if (user == null) {
            return null;
        }

        String fileId = user.getProfileImageFileId();
        if (fileId != null && !fileId.isBlank()) {
            return fileStorageService.buildViewUrl(fileId);
        }

        return user.getProfileImageUrl();
    }
}

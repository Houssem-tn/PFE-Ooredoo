package com.pfe.mapper;

import com.pfe.Dto.UserDTO;
import com.pfe.Entity.AppUser;
import com.pfe.Service.FileStorageService;
import com.pfe.support.TunisiaGovernorateSupport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    @Autowired
    protected FileStorageService fileStorageService;

    @Mapping(target = "profileImageUrl", expression = "java(resolveProfileImageUrl(user))")
    @Mapping(target = "profileThumbnailUrl", expression = "java(resolveProfileImageUrl(user))")
    @Mapping(target = "assignedGovernorates", expression = "java(normalizeGovernorates(user.getAssignedGovernorates()))")
    @Mapping(target = "coveredGovernorates", expression = "java(normalizeGovernorates(user.getCoveredGovernorates()))")
    @Mapping(target = "requestedGovernorates", expression = "java(normalizeGovernorates(user.getRequestedGovernorates()))")
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

    protected List<String> normalizeGovernorates(List<String> governorates) {
        return TunisiaGovernorateSupport.normalizeStoredValues(governorates);
    }
}

package com.pfe.MainPackage.Repository;

import com.pfe.MainPackage.Entity.FileEntityType;
import com.pfe.MainPackage.Entity.FileMetadata;
import com.pfe.MainPackage.Entity.FileRole;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {

    Optional<FileMetadata> findByObjectKeyAndDeletedFalse(String objectKey);

    Optional<FileMetadata> findByIdAndDeletedFalse(String id);

    List<FileMetadata> findByEntityTypeAndEntityIdAndDeletedFalse(FileEntityType entityType, String entityId);

    List<FileMetadata> findByEntityTypeAndEntityIdAndFileRoleAndDeletedFalse(
            FileEntityType entityType,
            String entityId,
            FileRole fileRole
    );

    List<FileMetadata> findByEntityTypeAndEntityIdAndFileRoleAndDeletedFalseOrderByCreatedAtDesc(
            FileEntityType entityType,
            String entityId,
            FileRole fileRole
    );
}

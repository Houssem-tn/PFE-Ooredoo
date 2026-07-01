package com.pfe.case_management.repository;

import com.pfe.case_management.entity.ZmFieldImportAuditEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ZmFieldImportAuditRepository extends MongoRepository<ZmFieldImportAuditEntity, String> {

    /** Most recent field-work imports uploaded by a given ZM (by email), newest first. */
    List<ZmFieldImportAuditEntity> findTop12ByUploadedByIgnoreCaseOrderByUploadedAtDesc(String uploadedBy);
}

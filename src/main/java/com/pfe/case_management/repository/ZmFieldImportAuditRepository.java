package com.pfe.case_management.repository;

import com.pfe.case_management.entity.ZmFieldImportAuditEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ZmFieldImportAuditRepository extends MongoRepository<ZmFieldImportAuditEntity, String> {
}

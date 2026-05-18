package com.pfe.MainPackage.Repository;

import com.pfe.MainPackage.Entity.ActIntervention;
import com.pfe.MainPackage.Entity.ActStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ActRepository extends MongoRepository<ActIntervention,String> {
    Optional<ActIntervention> findByCrmCase(String crmCase);

    List<ActIntervention> findBySttCompanyId(String sttCompanyId);
    List<ActIntervention> findByZoneId(String zoneId);
    List<ActIntervention> findByStatut(ActStatus statut);

}

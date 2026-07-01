package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.EquipmentInstance;
import com.pfe.stock_management.enums.InstanceOwnerType;
import com.pfe.stock_management.enums.InstanceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface EquipmentInstanceRepository extends MongoRepository<EquipmentInstance, String> {

    // ── Per-equipment uniqueness checks (normalised identifiers) ─────────────
    boolean existsByEquipmentIdAndNormalizedImei(String equipmentId, String normalizedImei);

    boolean existsByEquipmentIdAndNormalizedSerialNumber(String equipmentId, String normalizedSerialNumber);

    // ── Lifecycle lookups ─────────────────────────────────────────────────────
    List<EquipmentInstance> findByCurrentOwnerTypeAndCurrentOwnerIdAndStatus(
            InstanceOwnerType ownerType, String ownerId, InstanceStatus status);

    List<EquipmentInstance> findByCurrentOwnerIdAndStatusIn(String ownerId, Collection<InstanceStatus> statuses);

    List<EquipmentInstance> findByCaseIdAndCurrentOwnerIdAndStatus(
            String caseId, String ownerId, InstanceStatus status);

    // ── Audit / provenance lookups ────────────────────────────────────────────
    List<EquipmentInstance> findBySourceDpmId(String sourceDpmId);

    /** All tracked units that originated from any of the given STTs — used to scope the ZM dashboard. */
    List<EquipmentInstance> findByOriginSttIdIn(Collection<String> originSttIds);

    List<EquipmentInstance> findByCaseId(String caseId);

    List<EquipmentInstance> findByReturnId(String returnId);

    List<EquipmentInstance> findByEquipmentId(String equipmentId);
}

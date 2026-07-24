package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.dto.EquipmentInstanceDto;
import com.pfe.stock_management.dto.UnitTrackingInput;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.entity.EquipmentInstance;
import com.pfe.stock_management.enums.InstanceOwnerType;
import com.pfe.stock_management.enums.InstanceStatus;
import com.pfe.stock_management.enums.TrackingMode;
import com.pfe.stock_management.material_return.MaterialReturnRequest;
import com.pfe.stock_management.model.InstanceMovementEvent;
import com.pfe.stock_management.repository.EquipmentInstanceRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Owns the full lifecycle of per-unit {@link EquipmentInstance} records for tracked equipment.
 *
 * <p>This is the single place that validates IMEI/Serial tracking data and moves instances
 * through Depot/DPM → STT → TECH → case/client → recovery/return → depot. It sits alongside
 * the quantity-based counters (SttStock / TechStock): those remain authoritative for totals;
 * instances add per-unit traceability. Equipment with {@code trackingMode = NONE} never
 * produces instances and every method here is a no-op for it, preserving legacy behaviour.
 */
@Service
@RequiredArgsConstructor
public class EquipmentInstanceService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentInstanceService.class);

    private final EquipmentInstanceRepository instanceRepository;
    private final EquipmentRepository equipmentRepository;
    private final MongoTemplate mongoTemplate;

    // ── DPM reception ─────────────────────────────────────────────────────────

    /**
     * Validates the per-unit detail rows an STT entered for a tracked equipment line during
     * DPM reception. No-op for {@code NONE} equipment or a zero confirmed quantity.
     * Throws {@link ResponseStatusException} (BAD_REQUEST) on the first problem so the STT
     * cannot confirm reception with missing / mismatched / duplicate tracking data.
     */
    public void validateUnitsForConfirmation(Equipment eq, int confirmedQty, List<UnitTrackingInput> units) {
        TrackingMode mode = trackingMode(eq);
        if (!mode.isTracked() || confirmedQty <= 0) {
            return;
        }

        List<UnitTrackingInput> rows = units == null ? List.of() : units;
        if (rows.size() != confirmedQty) {
            throw bad(eq.getName() + " is tracked (" + mode + "): you must enter exactly "
                    + confirmedQty + " unit detail row(s) but received " + rows.size());
        }

        Set<String> seenImei = new HashSet<>();
        Set<String> seenSerial = new HashSet<>();
        int idx = 0;
        for (UnitTrackingInput u : rows) {
            idx++;
            String imei = normalize(u.getImei());
            String serial = normalize(u.getSerialNumber());

            if (mode.requiresImei() && imei == null) {
                throw bad(eq.getName() + " unit #" + idx + ": IMEI is required.");
            }
            if (mode.requiresSerial() && serial == null) {
                throw bad(eq.getName() + " unit #" + idx + ": Serial Number is required.");
            }
            if (mode.requiresAtLeastOne() && imei == null && serial == null) {
                throw bad(eq.getName() + " unit #" + idx + ": enter at least an IMEI or a Serial Number.");
            }

            if (imei != null && !seenImei.add(imei)) {
                throw bad(eq.getName() + ": IMEI '" + u.getImei() + "' is repeated in this reception.");
            }
            if (serial != null && !seenSerial.add(serial)) {
                throw bad(eq.getName() + ": Serial Number '" + u.getSerialNumber() + "' is repeated in this reception.");
            }

            if (imei != null && instanceRepository.existsByEquipmentIdAndNormalizedImei(eq.getId(), imei)) {
                throw bad(eq.getName() + ": IMEI '" + u.getImei() + "' already exists in stock.");
            }
            if (serial != null && instanceRepository.existsByEquipmentIdAndNormalizedSerialNumber(eq.getId(), serial)) {
                throw bad(eq.getName() + ": Serial Number '" + u.getSerialNumber() + "' already exists in stock.");
            }
        }
    }

    /**
     * Materialises one {@link EquipmentInstance} (status IN_STT_STOCK, owner = DPM's STT) per
     * validated unit row. Call only after {@link #validateUnitsForConfirmation}. No-op for NONE.
     */
    public List<EquipmentInstance> createForDpm(Equipment eq, DpmRequest dpm,
                                                List<UnitTrackingInput> units, String actor) {
        TrackingMode mode = trackingMode(eq);
        if (!mode.isTracked() || units == null || units.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<EquipmentInstance> created = new ArrayList<>();
        for (UnitTrackingInput u : units) {
            EquipmentInstance inst = EquipmentInstance.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .articleCode(eq.getArticleCode())
                    .trackingMode(mode)
                    .imei(trimToNull(u.getImei()))
                    .serialNumber(trimToNull(u.getSerialNumber()))
                    .normalizedImei(normalize(u.getImei()))
                    .normalizedSerialNumber(normalize(u.getSerialNumber()))
                    .sourceDpmId(dpm.getId())
                    .sourceDpmNumber(dpm.getDpmNumber())
                    .originSttId(dpm.getSttId())
                    .currentOwnerType(InstanceOwnerType.STT)
                    .currentOwnerId(dpm.getSttId())
                    .currentOwnerName(dpm.getSttName())
                    .status(InstanceStatus.IN_STT_STOCK)
                    .movements(new ArrayList<>())
                    .createdBy(actor)
                    .build();

            inst.getMovements().add(InstanceMovementEvent.builder()
                    .type("RECEIVED_FROM_DPM")
                    .toOwnerType(InstanceOwnerType.STT)
                    .toOwnerId(dpm.getSttId())
                    .toOwnerName(dpm.getSttName())
                    .resultingStatus(InstanceStatus.IN_STT_STOCK)
                    .dpmId(dpm.getId())
                    .dpmNumber(dpm.getDpmNumber())
                    .note(trimToNull(u.getNote()))
                    .occurredAt(now)
                    .performedBy(actor)
                    .build());

            created.add(instanceRepository.save(inst));
        }
        log.info("equipment_instances_created dpmId={} equipmentId={} count={}",
                dpm.getId(), eq.getId(), created.size());
        return created;
    }

    // ── STT → TECH distribution ────────────────────────────────────────────────

    /**
     * Read-only pre-flight: verifies every selected instance is currently owned by {@code sttId},
     * IN_STT_STOCK, and belongs to {@code equipmentId}. Used by the distribution flow to fail
     * fast before quantity counters are decremented. Returns the loaded instances.
     */
    public List<EquipmentInstance> assertDistributable(String equipmentId, List<String> instanceIds, String sttId) {
        List<EquipmentInstance> result = new ArrayList<>();
        for (String id : dedupe(instanceIds)) {
            EquipmentInstance inst = getOwned(id, sttId, InstanceStatus.IN_STT_STOCK);
            requireEquipment(inst, equipmentId);
            result.add(inst);
        }
        return result;
    }

    /** Builds denormalised IMEI/Serial references for a set of instances (display & audit). */
    public static List<com.pfe.stock_management.model.TrackedUnitRef> toRefs(List<EquipmentInstance> instances) {
        return instances.stream()
                .map(i -> com.pfe.stock_management.model.TrackedUnitRef.builder()
                        .instanceId(i.getId())
                        .imei(i.getImei())
                        .serialNumber(i.getSerialNumber())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Transfers the exact selected instances from an STT to a TECH (status → IN_TECH_STOCK).
     * Validates each instance is currently owned by {@code stt} and IN_STT_STOCK and belongs to
     * {@code equipmentId}. Returns the moved instances (for denormalised display on the distribution).
     */
    public List<EquipmentInstance> moveStockToTech(String equipmentId, List<String> instanceIds,
                                                   AppUser stt, AppUser tech,
                                                   String distributionId, String actor) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        List<EquipmentInstance> moved = new ArrayList<>();
        for (String id : dedupe(instanceIds)) {
            EquipmentInstance inst = getOwned(id, stt.getId(), InstanceStatus.IN_STT_STOCK);
            requireEquipment(inst, equipmentId);

            inst.getMovements().add(InstanceMovementEvent.builder()
                    .type("DISTRIBUTED_TO_TECH")
                    .fromOwnerType(InstanceOwnerType.STT)
                    .fromOwnerId(stt.getId())
                    .fromOwnerName(stt.getFullName())
                    .toOwnerType(InstanceOwnerType.TECH)
                    .toOwnerId(tech.getId())
                    .toOwnerName(tech.getFullName())
                    .resultingStatus(InstanceStatus.IN_TECH_STOCK)
                    .distributionId(distributionId)
                    .occurredAt(now)
                    .performedBy(actor)
                    .build());

            inst.setCurrentOwnerType(InstanceOwnerType.TECH);
            inst.setCurrentOwnerId(tech.getId());
            inst.setCurrentOwnerName(tech.getFullName());
            inst.setStatus(InstanceStatus.IN_TECH_STOCK);
            moved.add(instanceRepository.save(inst));
        }
        return moved;
    }

    // ── Case usage (ACT / PLT) ──────────────────────────────────────────────────

    /**
     * Reconciles the tracked units consumed on a case with the newly submitted selection,
     * for a single executing owner (TECH or STT self-execution). Idempotent: instances no
     * longer selected are restored to stock, newly selected ones are marked USED_ON_CASE, and
     * unchanged ones are left as-is. No-op for non-tracked equipment.
     *
     * @param quantityByEquipment merged equipmentId → consumed quantity (all used equipment)
     * @param instanceIdsByEquipment equipmentId → selected instance ids (only present for tracked items)
     */
    public void syncCaseUsageInstances(CaseEntity caseEntity,
                                       InstanceOwnerType ownerType, String ownerId, String ownerName,
                                       Map<String, Integer> quantityByEquipment,
                                       Map<String, List<String>> instanceIdsByEquipment,
                                       String actor) {
        if (quantityByEquipment == null || quantityByEquipment.isEmpty()) {
            // Even with no usage submitted, a previous selection might need restoring.
            restoreCaseUsageNotIn(caseEntity.getId(), Set.of(), actor);
            return;
        }

        Map<String, Equipment> equipment = equipmentRepository
                .findAllById(quantityByEquipment.keySet()).stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));

        Set<String> desired = new LinkedHashSet<>();
        // Validate the full new selection BEFORE mutating anything.
        for (Map.Entry<String, Integer> entry : quantityByEquipment.entrySet()) {
            String equipmentId = entry.getKey();
            int qty = entry.getValue() == null ? 0 : entry.getValue();
            Equipment eq = equipment.get(equipmentId);
            TrackingMode mode = trackingMode(eq);
            List<String> ids = instanceIdsByEquipment == null ? null : instanceIdsByEquipment.get(equipmentId);
            ids = ids == null ? List.of() : dedupe(ids);

            if (!mode.isTracked()) {
                continue; // quantity-based item — ignore any instanceIds
            }
            if (qty <= 0) {
                continue;
            }
            String name = eq != null ? eq.getName() : equipmentId;
            if (ids.size() != qty) {
                throw bad(name + " is tracked: select exactly " + qty
                        + " unit(s) by IMEI/Serial (received " + ids.size() + ").");
            }
            for (String instanceId : ids) {
                EquipmentInstance inst = load(instanceId);
                requireEquipment(inst, equipmentId);
                boolean alreadyUsedHere = inst.getStatus() == InstanceStatus.USED_ON_CASE
                        && caseEntity.getId().equals(inst.getCaseId());
                if (!alreadyUsedHere) {
                    requireOwner(inst, ownerId, expectedStockStatus(ownerType));
                }
                desired.add(instanceId);
            }
        }

        // Restore any unit previously used on this case that is not in the new selection.
        restoreCaseUsageNotIn(caseEntity.getId(), desired, actor);

        // Mark the newly selected units (skip ones already used on this case).
        Instant now = Instant.now();
        for (String instanceId : desired) {
            EquipmentInstance inst = load(instanceId);
            if (inst.getStatus() == InstanceStatus.USED_ON_CASE
                    && caseEntity.getId().equals(inst.getCaseId())) {
                continue;
            }
            inst.getMovements().add(InstanceMovementEvent.builder()
                    .type("USED_ON_CASE")
                    .fromOwnerType(ownerType)
                    .fromOwnerId(ownerId)
                    .fromOwnerName(ownerName)
                    .toOwnerType(InstanceOwnerType.CLIENT)
                    .toOwnerId(caseEntity.getId())
                    .toOwnerName(caseEntity.getCrmCase())
                    .resultingStatus(InstanceStatus.USED_ON_CASE)
                    .caseId(caseEntity.getId())
                    .caseReference(caseEntity.getCrmCase())
                    .occurredAt(now)
                    .performedBy(actor)
                    .build());
            inst.setStatus(InstanceStatus.USED_ON_CASE);
            inst.setCaseId(caseEntity.getId());
            inst.setCaseReference(caseEntity.getCrmCase());
            inst.setCurrentOwnerType(InstanceOwnerType.CLIENT);
            inst.setCurrentOwnerId(caseEntity.getId());
            inst.setCurrentOwnerName(caseEntity.getCrmCase());
            instanceRepository.save(inst);
        }
    }

    /** Restores every tracked unit currently used on a case back to its prior holder's stock. */
    public void restoreAllCaseUsage(String caseId, String actor) {
        restoreCaseUsageNotIn(caseId, Set.of(), actor);
    }

    private void restoreCaseUsageNotIn(String caseId, Set<String> keepInstanceIds, String actor) {
        List<EquipmentInstance> used = instanceRepository.findByCaseId(caseId).stream()
                .filter(i -> i.getStatus() == InstanceStatus.USED_ON_CASE)
                .filter(i -> !keepInstanceIds.contains(i.getId()))
                .toList();
        if (used.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (EquipmentInstance inst : used) {
            InstanceMovementEvent usage = lastUsageMovement(inst);
            InstanceOwnerType backOwnerType = usage != null && usage.getFromOwnerType() != null
                    ? usage.getFromOwnerType() : InstanceOwnerType.TECH;
            String backOwnerId = usage != null ? usage.getFromOwnerId() : null;
            String backOwnerName = usage != null ? usage.getFromOwnerName() : null;
            InstanceStatus backStatus = expectedStockStatus(backOwnerType);

            inst.getMovements().add(InstanceMovementEvent.builder()
                    .type("USAGE_RESTORED")
                    .fromOwnerType(InstanceOwnerType.CLIENT)
                    .fromOwnerId(inst.getCurrentOwnerId())
                    .fromOwnerName(inst.getCurrentOwnerName())
                    .toOwnerType(backOwnerType)
                    .toOwnerId(backOwnerId)
                    .toOwnerName(backOwnerName)
                    .resultingStatus(backStatus)
                    .caseId(caseId)
                    .caseReference(inst.getCaseReference())
                    .occurredAt(now)
                    .performedBy(actor)
                    .build());

            inst.setStatus(backStatus);
            inst.setCurrentOwnerType(backOwnerType);
            inst.setCurrentOwnerId(backOwnerId);
            inst.setCurrentOwnerName(backOwnerName);
            inst.setCaseId(null);
            inst.setCaseReference(null);
            instanceRepository.save(inst);
        }
    }

    // ── RES recovery + material return ──────────────────────────────────────────

    /**
     * Records a tracked unit recovered from the client during a RES résiliation. If a matching
     * instance already exists for this equipment (same IMEI/Serial), it is transitioned to
     * RECOVERED_FROM_CLIENT; otherwise a new instance is created. Linked to the case and the
     * material return. Returns the instance id (to store on the {@code ReturnedItem}), or null
     * when the equipment is not tracked / no identifier was supplied.
     */
    public String recordClientRecovery(Equipment eq, CaseEntity caseEntity,
                                       String imei, String serial,
                                       MaterialReturnRequest ret, String actor) {
        if (eq == null || !trackingMode(eq).isTracked()) {
            return null;
        }
        String normImei = normalize(imei);
        String normSerial = normalize(serial);
        if (normImei == null && normSerial == null) {
            return null; // nothing to track for this recovered unit
        }

        EquipmentInstance inst = findExisting(eq.getId(), normImei, normSerial)
                .orElseGet(() -> EquipmentInstance.builder()
                        .equipmentId(eq.getId())
                        .equipmentName(eq.getName())
                        .articleCode(eq.getArticleCode())
                        .trackingMode(trackingMode(eq))
                        .imei(trimToNull(imei))
                        .serialNumber(trimToNull(serial))
                        .normalizedImei(normImei)
                        .normalizedSerialNumber(normSerial)
                        .movements(new ArrayList<>())
                        .createdBy(actor)
                        .build());

        if (inst.getMovements() == null) {
            inst.setMovements(new ArrayList<>());
        }
        inst.getMovements().add(InstanceMovementEvent.builder()
                .type("RECOVERED_FROM_CLIENT")
                .fromOwnerType(InstanceOwnerType.CLIENT)
                .toOwnerType(InstanceOwnerType.RETURN)
                .toOwnerId(ret != null ? ret.getId() : null)
                .toOwnerName(ret != null ? ret.getReturnCode() : null)
                .resultingStatus(InstanceStatus.RECOVERED_FROM_CLIENT)
                .caseId(caseEntity != null ? caseEntity.getId() : null)
                .caseReference(caseEntity != null ? caseEntity.getCrmCase() : null)
                .returnId(ret != null ? ret.getId() : null)
                .returnCode(ret != null ? ret.getReturnCode() : null)
                .occurredAt(Instant.now())
                .performedBy(actor)
                .build());

        inst.setStatus(InstanceStatus.RECOVERED_FROM_CLIENT);
        inst.setCurrentOwnerType(InstanceOwnerType.RETURN);
        inst.setCurrentOwnerId(ret != null ? ret.getId() : null);
        inst.setCurrentOwnerName(ret != null ? ret.getReturnCode() : null);
        if (inst.getOriginSttId() == null && ret != null) {
            inst.setOriginSttId(ret.getSttId());
        }
        inst.setCaseId(caseEntity != null ? caseEntity.getId() : null);
        inst.setCaseReference(caseEntity != null ? caseEntity.getCrmCase() : null);
        inst.setReturnId(ret != null ? ret.getId() : null);
        inst.setReturnCode(ret != null ? ret.getReturnCode() : null);

        return instanceRepository.save(inst).getId();
    }

    // ── PLT/RES technician retrieval + ZM collection ─────────────────────────────

    /**
     * Records a tracked unit retrieved from the client during a PLT/RES intervention through the
     * technician Retrieved-Stock workflow. Unlike {@link #recordClientRecovery} (the older RES-only
     * path into {@code MaterialReturnRequest}), the resulting instance stays owned by the
     * <b>technician</b> (status {@code PENDING_ZM_COLLECTION}) rather than jumping straight to a
     * return-owned state — it only moves to the ZM once {@link #markCollectedByZm} is called.
     * If a matching instance already exists for this equipment (same IMEI/Serial), it is
     * transitioned in place; otherwise a new instance is created. Returns the instance id (to store
     * on the {@code RetrievedStockItem}), or null when the equipment is not tracked / no identifier
     * was supplied.
     */
    public String recordTechRetrieval(Equipment eq, CaseEntity caseEntity,
                                      String imei, String serial,
                                      com.pfe.stock_management.retrieved_stock.RetrievedStockItem item,
                                      String actor) {
        if (eq == null || !trackingMode(eq).isTracked()) {
            return null;
        }
        String normImei = normalize(imei);
        String normSerial = normalize(serial);
        if (normImei == null && normSerial == null) {
            return null; // nothing to track for this retrieved unit
        }

        EquipmentInstance inst = findExisting(eq.getId(), normImei, normSerial)
                .orElseGet(() -> EquipmentInstance.builder()
                        .equipmentId(eq.getId())
                        .equipmentName(eq.getName())
                        .articleCode(eq.getArticleCode())
                        .trackingMode(trackingMode(eq))
                        .imei(trimToNull(imei))
                        .serialNumber(trimToNull(serial))
                        .normalizedImei(normImei)
                        .normalizedSerialNumber(normSerial)
                        .movements(new ArrayList<>())
                        .createdBy(actor)
                        .build());

        if (inst.getMovements() == null) {
            inst.setMovements(new ArrayList<>());
        }
        inst.getMovements().add(InstanceMovementEvent.builder()
                .type("RETRIEVED_BY_TECH")
                .fromOwnerType(InstanceOwnerType.CLIENT)
                .toOwnerType(InstanceOwnerType.TECH)
                .toOwnerId(item != null ? item.getTechnicianId() : null)
                .toOwnerName(item != null ? item.getTechnicianName() : null)
                .resultingStatus(InstanceStatus.PENDING_ZM_COLLECTION)
                .caseId(caseEntity != null ? caseEntity.getId() : null)
                .caseReference(caseEntity != null ? caseEntity.getCrmCase() : null)
                .note(item != null && item.getDisposition() != null ? item.getDisposition().name() : null)
                .occurredAt(Instant.now())
                .performedBy(actor)
                .build());

        inst.setStatus(InstanceStatus.PENDING_ZM_COLLECTION);
        inst.setCurrentOwnerType(InstanceOwnerType.TECH);
        inst.setCurrentOwnerId(item != null ? item.getTechnicianId() : null);
        inst.setCurrentOwnerName(item != null ? item.getTechnicianName() : null);
        if (inst.getOriginSttId() == null && item != null) {
            inst.setOriginSttId(item.getSttId());
        }
        inst.setCaseId(caseEntity != null ? caseEntity.getId() : null);
        inst.setCaseReference(caseEntity != null ? caseEntity.getCrmCase() : null);
        inst.setRetrievedStockItemId(item != null ? item.getId() : null);

        try {
            return instanceRepository.save(inst).getId();
        } catch (DuplicateKeyException dup) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    eq.getName() + ": tracked unit " + unitLabel(inst)
                            + " already exists in stock (duplicate IMEI/Serial).");
        }
    }

    /**
     * ZM confirms physical collection: transitions every given instance from
     * {@code PENDING_ZM_COLLECTION} (owner TECH) to {@code COLLECTED_BY_ZM} (owner ZM). Instances
     * not currently in {@code PENDING_ZM_COLLECTION} are left untouched (defensive — the caller,
     * {@code RetrievedStockService.confirmCollection}, has already validated the corresponding
     * {@code RetrievedStockItem}s are all pending before calling this).
     */
    public void markCollectedByZm(List<String> instanceIds, String zmId, String zmName, String actor) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (String id : dedupe(instanceIds)) {
            EquipmentInstance inst = load(id);
            if (inst.getStatus() != InstanceStatus.PENDING_ZM_COLLECTION) {
                continue;
            }
            inst.getMovements().add(InstanceMovementEvent.builder()
                    .type("COLLECTED_BY_ZM")
                    .fromOwnerType(InstanceOwnerType.TECH)
                    .fromOwnerId(inst.getCurrentOwnerId())
                    .fromOwnerName(inst.getCurrentOwnerName())
                    .toOwnerType(InstanceOwnerType.ZM)
                    .toOwnerId(zmId)
                    .toOwnerName(zmName)
                    .resultingStatus(InstanceStatus.COLLECTED_BY_ZM)
                    .caseId(inst.getCaseId())
                    .caseReference(inst.getCaseReference())
                    .occurredAt(now)
                    .performedBy(actor)
                    .build());
            inst.setStatus(InstanceStatus.COLLECTED_BY_ZM);
            inst.setCurrentOwnerType(InstanceOwnerType.ZM);
            inst.setCurrentOwnerId(zmId);
            inst.setCurrentOwnerName(zmName);
            instanceRepository.save(inst);
        }
    }

    /** On material-return handover/approval, flips the return's tracked units to RETURNED_TO_DEPOT. */
    public void markReturnedToDepot(String returnId, String actor) {
        if (returnId == null) {
            return;
        }
        List<EquipmentInstance> instances = instanceRepository.findByReturnId(returnId).stream()
                .filter(i -> i.getStatus() != InstanceStatus.RETURNED_TO_DEPOT)
                .toList();
        if (instances.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (EquipmentInstance inst : instances) {
            inst.getMovements().add(InstanceMovementEvent.builder()
                    .type("RETURNED_TO_DEPOT")
                    .fromOwnerType(inst.getCurrentOwnerType())
                    .fromOwnerId(inst.getCurrentOwnerId())
                    .fromOwnerName(inst.getCurrentOwnerName())
                    .toOwnerType(InstanceOwnerType.DEPOT)
                    .resultingStatus(InstanceStatus.RETURNED_TO_DEPOT)
                    .returnId(returnId)
                    .returnCode(inst.getReturnCode())
                    .occurredAt(now)
                    .performedBy(actor)
                    .build());
            inst.setStatus(InstanceStatus.RETURNED_TO_DEPOT);
            inst.setCurrentOwnerType(InstanceOwnerType.DEPOT);
            inst.setCurrentOwnerId(null);
            inst.setCurrentOwnerName(null);
            instanceRepository.save(inst);
        }
    }

    // ── Read / audit ────────────────────────────────────────────────────────────

    /** Filter for the Admin / ZM "Unit Tracking" audit search. Any field may be null/blank. */
    public record InstanceAuditFilter(
            String equipmentId, String imei, String serial, String ownerType,
            String ownerId, String status, String caseId, String dpmId, String q) {
    }

    /**
     * Searches tracked instances for the audit page (newest first, capped). When
     * {@code originSttScope} is non-null the results are limited to units that originated from
     * one of those STTs (ZM territory scoping); an empty scope yields no rows.
     */
    public List<EquipmentInstanceDto> search(InstanceAuditFilter filter, List<String> originSttScope) {
        if (originSttScope != null && originSttScope.isEmpty()) {
            return List.of();
        }
        Query query = new Query();
        if (originSttScope != null) {
            query.addCriteria(Criteria.where("originSttId").in(originSttScope));
        }
        InstanceAuditFilter f = filter == null
                ? new InstanceAuditFilter(null, null, null, null, null, null, null, null, null)
                : filter;

        if (notBlank(f.equipmentId())) query.addCriteria(Criteria.where("equipmentId").is(f.equipmentId().trim()));
        if (notBlank(f.status()))      query.addCriteria(Criteria.where("status").is(f.status().trim()));
        if (notBlank(f.ownerType()))   query.addCriteria(Criteria.where("currentOwnerType").is(f.ownerType().trim()));
        if (notBlank(f.ownerId()))     query.addCriteria(Criteria.where("currentOwnerId").is(f.ownerId().trim()));
        if (notBlank(f.caseId()))      query.addCriteria(Criteria.where("caseId").is(f.caseId().trim()));
        if (notBlank(f.dpmId()))       query.addCriteria(Criteria.where("sourceDpmId").is(f.dpmId().trim()));
        if (notBlank(f.imei()))
            query.addCriteria(Criteria.where("normalizedImei").regex(Pattern.quote(normalize(f.imei())), "i"));
        if (notBlank(f.serial()))
            query.addCriteria(Criteria.where("normalizedSerialNumber").regex(Pattern.quote(normalize(f.serial())), "i"));
        if (notBlank(f.q())) {
            String raw = f.q().trim();
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("normalizedImei").regex(Pattern.quote(raw.toUpperCase()), "i"),
                    Criteria.where("normalizedSerialNumber").regex(Pattern.quote(raw.toUpperCase()), "i"),
                    Criteria.where("equipmentName").regex(Pattern.quote(raw), "i")
            ));
        }

        query.with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        query.limit(500);
        return mongoTemplate.find(query, EquipmentInstance.class).stream()
                .map(EquipmentInstanceDto::from)
                .collect(Collectors.toList());
    }

    /** Full single-instance projection with movement history for the audit detail panel. */
    public EquipmentInstanceDto getDetail(String id) {
        return EquipmentInstanceDto.fromDetail(load(id));
    }

    /**
     * Tracked units currently available in an owner's own stock (STT → IN_STT_STOCK,
     * TECH → IN_TECH_STOCK), optionally filtered to one equipment. Powers the STT distribution
     * and TECH/STT usage unit pickers.
     */
    public List<EquipmentInstanceDto> listOwnedAvailable(InstanceOwnerType ownerType, String ownerId, String equipmentId) {
        InstanceStatus status = expectedStockStatus(ownerType);
        return instanceRepository.findByCurrentOwnerTypeAndCurrentOwnerIdAndStatus(ownerType, ownerId, status).stream()
                .filter(i -> !notBlank(equipmentId) || equipmentId.equals(i.getEquipmentId()))
                .map(EquipmentInstanceDto::from)
                .collect(Collectors.toList());
    }

    public EquipmentInstance getByIdOrThrow(String id) {
        return load(id);
    }

    public List<EquipmentInstance> findBySourceDpm(String dpmId) {
        return instanceRepository.findBySourceDpmId(dpmId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private java.util.Optional<EquipmentInstance> findExisting(String equipmentId, String normImei, String normSerial) {
        return instanceRepository.findByEquipmentId(equipmentId).stream()
                .filter(i -> (normImei != null && normImei.equals(i.getNormalizedImei()))
                        || (normSerial != null && normSerial.equals(i.getNormalizedSerialNumber())))
                .findFirst();
    }

    private InstanceMovementEvent lastUsageMovement(EquipmentInstance inst) {
        if (inst.getMovements() == null) {
            return null;
        }
        InstanceMovementEvent found = null;
        for (InstanceMovementEvent m : inst.getMovements()) {
            if ("USED_ON_CASE".equals(m.getType())) {
                found = m;
            }
        }
        return found;
    }

    private InstanceStatus expectedStockStatus(InstanceOwnerType ownerType) {
        return ownerType == InstanceOwnerType.STT ? InstanceStatus.IN_STT_STOCK : InstanceStatus.IN_TECH_STOCK;
    }

    private EquipmentInstance load(String id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> bad("Tracked unit not found: " + id));
    }

    private EquipmentInstance getOwned(String id, String ownerId, InstanceStatus expectedStatus) {
        EquipmentInstance inst = load(id);
        requireOwner(inst, ownerId, expectedStatus);
        return inst;
    }

    private void requireOwner(EquipmentInstance inst, String ownerId, InstanceStatus expectedStatus) {
        if (ownerId == null || !ownerId.equals(inst.getCurrentOwnerId())) {
            throw bad("Tracked unit " + unitLabel(inst) + " is not in your stock.");
        }
        if (inst.getStatus() != expectedStatus) {
            throw bad("Tracked unit " + unitLabel(inst) + " is not available (status " + inst.getStatus() + ").");
        }
    }

    private void requireEquipment(EquipmentInstance inst, String equipmentId) {
        if (equipmentId != null && !equipmentId.equals(inst.getEquipmentId())) {
            throw bad("Tracked unit " + unitLabel(inst) + " does not belong to the selected equipment.");
        }
    }

    private String unitLabel(EquipmentInstance inst) {
        if (inst.getSerialNumber() != null && !inst.getSerialNumber().isBlank()) {
            return "SN " + inst.getSerialNumber();
        }
        if (inst.getImei() != null && !inst.getImei().isBlank()) {
            return "IMEI " + inst.getImei();
        }
        return inst.getId();
    }

    private static TrackingMode trackingMode(Equipment eq) {
        if (eq == null || eq.getTrackingMode() == null) {
            return TrackingMode.NONE;
        }
        return eq.getTrackingMode();
    }

    private static List<String> dedupe(List<String> ids) {
        return ids.stream().filter(s -> s != null && !s.isBlank())
                .map(String::trim).distinct().collect(Collectors.toList());
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toUpperCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

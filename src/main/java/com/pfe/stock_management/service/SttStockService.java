package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.UsedEquipmentRequestItem;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.model.UsedEquipmentItem;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.metrics.BusinessMetrics;
import com.pfe.stock_management.dto.DeclareTechUsageRequest;
import com.pfe.stock_management.dto.DeclareTechUsageResponse;
import com.pfe.stock_management.dto.DistributableDpmDto;
import com.pfe.stock_management.dto.DistributableDpmItemDto;
import com.pfe.stock_management.dto.DistributeToTechRequest;
import com.pfe.stock_management.dto.PendingDistributionDto;
import com.pfe.stock_management.dto.TechUsageItemRequest;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.entity.EquipmentInstance;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.entity.TechStockUsage;
import com.pfe.stock_management.enums.DistributionStatus;
import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.enums.InstanceOwnerType;
import com.pfe.stock_management.enums.TrackingMode;
import com.pfe.stock_management.model.DistributionItem;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.model.TechStockItem;
import com.pfe.stock_management.model.UsageItem;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttDistributionRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import com.pfe.stock_management.repository.TechStockUsageRepository;
import com.pfe.stock_management.model.ConsumedEquipmentItem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SttStockService {
    private static final Logger log = LoggerFactory.getLogger(SttStockService.class);

    private final SttStockRepository sttStockRepository;
    private final TechStockRepository techStockRepository;
    private final SttDistributionRepository distributionRepository;
    private final DpmRepository dpmRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final StockMovementService stockMovementService;
    private final NotificationEventService notificationEventService;
    private final TechStockUsageRepository techStockUsageRepository;
    private final MongoTemplate mongoTemplate;
    private final CaseStockConsumptionService caseStockConsumptionService;
    private final StockAlertEvaluationService stockAlertEvaluationService;
    private final StockUsagePermissionService stockUsagePermissionService;
    private final DpmAllocationService dpmAllocationService;
    private final EquipmentUsagePolicyService equipmentUsagePolicy;
    private final EquipmentInstanceService equipmentInstanceService;
    private final EquipmentRepository equipmentRepository;
    private final BusinessMetrics businessMetrics;

    public void applyDpmConfirmation(DpmRequest dpm) {
        SttStock stock = sttStockRepository.findBySttId(dpm.getSttId())
                .orElseGet(() -> SttStock.builder().sttId(dpm.getSttId()).items(new ArrayList<>()).build());

        for (DpmItem item : dpm.getItems()) {
            if (item.getConfirmedQuantity() == null || item.getConfirmedQuantity() <= 0) continue;

            Optional<SttStockItem> existing = stock.getItems().stream()
                    .filter(si -> si.getEquipmentId().equals(item.getEquipmentId()))
                    .findFirst();

            if (existing.isPresent()) {
                SttStockItem si = existing.get();
                si.setTotalReceived(si.getTotalReceived() + item.getConfirmedQuantity());
                si.setLastReceivedAt(Instant.now());
                if (si.getUnit() == null && item.getUnit() != null) si.setUnit(item.getUnit());
            } else {
                stock.getItems().add(SttStockItem.builder()
                        .equipmentId(item.getEquipmentId())
                        .equipmentName(item.getEquipmentName())
                        .unit(item.getUnit())
                        .totalReceived(item.getConfirmedQuantity())
                        .lastReceivedAt(Instant.now())
                        .build());
            }
        }

        stock.setLastUpdatedAt(Instant.now());
        sttStockRepository.save(stock);

        // Materialise per-DPM source balances so future pooled distributions can be
        // automatically attributed back to this DPM (FIFO) without the STT picking one.
        dpmAllocationService.createSourceBalances(dpm);

        // Evaluate stock alert thresholds after DPM increases STT stock
        stockAlertEvaluationService.evaluateSttStock(dpm.getSttId());
    }

    public SttDistribution distributeToTech(DistributeToTechRequest request) {
        String callerEmail = currentUser();
        AppUser stt = findByEmail(callerEmail);

        AppUser tech;
        if (request.getTechId().equals(stt.getId())) {
            tech = stt;
        } else {
            tech = userRepository.findByIdAndRoleAndStatusAndEnabled(
                    request.getTechId(), UserRole.ROLE_TECH, AccountStatus.APPROVED, true)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "TECH not found or not active"));
            if (!stt.getId().equals(tech.getAssignedSttId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This TECH is not assigned to you");
            }
        }

        // Per-DPM validation
        if (request.getDpmId() != null && !request.getDpmId().isBlank()) {
            DpmRequest dpm = dpmRepository.findById(request.getDpmId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "DPM not found"));
            if (!dpm.getSttId().equals(stt.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This DPM does not belong to you");
            }
            Map<String, Integer> confirmed = dpm.getItems().stream()
                    .filter(i -> i.getConfirmedQuantity() != null && i.getConfirmedQuantity() > 0)
                    .collect(Collectors.toMap(DpmItem::getEquipmentId, DpmItem::getConfirmedQuantity));
            Map<String, Integer> alreadyDistributed = new HashMap<>();
            for (SttDistribution d : distributionRepository.findByDpmId(request.getDpmId())) {
                for (DistributionItem di : d.getItems()) {
                    alreadyDistributed.merge(di.getEquipmentId(), di.getQuantity(), Integer::sum);
                }
            }
            for (var itemReq : request.getItems()) {
                int remaining = confirmed.getOrDefault(itemReq.getEquipmentId(), 0)
                        - alreadyDistributed.getOrDefault(itemReq.getEquipmentId(), 0);
                if (itemReq.getQuantity() > remaining) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Exceeds remaining DPM quantity for equipment " + itemReq.getEquipmentId()
                                    + ": remaining=" + remaining + ", requested=" + itemReq.getQuantity());
                }
            }
        }

        SttStock sttStock = sttStockRepository.findBySttId(stt.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No stock found for your account"));

        Map<String, SttStockItem> stockMap = sttStock.getItems().stream()
                .collect(Collectors.toMap(SttStockItem::getEquipmentId, si -> si));

        // Tracking config for the requested items (drives per-unit instance selection).
        Map<String, Equipment> equipmentById = equipmentRepository.findAllById(
                        request.getItems().stream().map(i -> i.getEquipmentId()).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Equipment::getId, e -> e, (a, b) -> a));

        List<DistributionItem> distributionItems = new ArrayList<>();

        for (var itemReq : request.getItems()) {
            SttStockItem si = stockMap.get(itemReq.getEquipmentId());
            if (si == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Equipment not in your stock: " + itemReq.getEquipmentId());
            }
            if (si.getAvailableQuantity() < itemReq.getQuantity()) {
                businessMetrics.recordStockInsufficient();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock for " + si.getEquipmentName()
                                + ": available=" + si.getAvailableQuantity()
                                + ", requested=" + itemReq.getQuantity());
            }

            // For tracked equipment the STT must select the exact units. Validate up-front
            // (read-only) so nothing is mutated if the selection is wrong.
            Equipment eq = equipmentById.get(itemReq.getEquipmentId());
            List<String> instanceIds = null;
            if (eq != null && eq.getTrackingMode() != null && eq.getTrackingMode() != TrackingMode.NONE) {
                instanceIds = itemReq.getInstanceIds() == null ? List.of() : itemReq.getInstanceIds();
                if (instanceIds.size() != itemReq.getQuantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            si.getEquipmentName() + " is tracked: select exactly " + itemReq.getQuantity()
                                    + " unit(s) by IMEI/Serial (received " + instanceIds.size() + ").");
                }
                equipmentInstanceService.assertDistributable(itemReq.getEquipmentId(), instanceIds, stt.getId());
            }

            distributionItems.add(DistributionItem.builder()
                    .equipmentId(si.getEquipmentId())
                    .equipmentName(si.getEquipmentName())
                    .unit(si.getUnit())
                    .quantity(itemReq.getQuantity())
                    .instanceIds(instanceIds)
                    .build());
        }

        for (DistributionItem di : distributionItems) {
            SttStockItem si = stockMap.get(di.getEquipmentId());
            si.setTotalDistributedToTechs(si.getTotalDistributedToTechs() + di.getQuantity());
            si.setLastDistributedAt(Instant.now());
        }
        sttStock.setLastUpdatedAt(Instant.now());
        sttStockRepository.save(sttStock);

        SttDistribution distribution = SttDistribution.builder()
                .sttId(stt.getId())
                .sttName(stt.getFullName())
                .sttEmail(stt.getEmail())
                .techId(tech.getId())
                .techName(tech.getFullName())
                .techEmail(tech.getEmail())
                .dpmId(request.getDpmId())
                .items(distributionItems)
                .status(DistributionStatus.PENDING_TECH_CONFIRMATION)
                .distributedAt(Instant.now())
                .notes(request.getNotes())
                .distributedBy(callerEmail)
                .build();

        SttDistribution saved = distributionRepository.save(distribution);

        // Transfer the exact tracked units STT → TECH (status → IN_TECH_STOCK) and persist
        // their denormalised IMEI/Serial on the distribution line for display & audit. Quantity
        // counters above remain authoritative; instances add per-unit traceability.
        boolean movedAnyInstance = false;
        for (DistributionItem di : saved.getItems()) {
            if (di.getInstanceIds() == null || di.getInstanceIds().isEmpty()) {
                continue;
            }
            List<EquipmentInstance> moved = equipmentInstanceService.moveStockToTech(
                    di.getEquipmentId(), di.getInstanceIds(), stt, tech, saved.getId(), callerEmail);
            di.setTrackedUnits(EquipmentInstanceService.toRefs(moved));
            movedAnyInstance = true;
        }
        if (movedAnyInstance) {
            saved = distributionRepository.save(saved);
        }

        // Automatically attribute this pooled distribution back to confirmed DPM
        // sources (FIFO). A single distribution can span multiple DPMs; any shortfall
        // (e.g. legacy pre-upgrade stock) is left unattributed rather than mis-attributed.
        dpmAllocationService.allocateForDistribution(saved);

        stockMovementService.recordDistribution(saved, callerEmail);
        notificationEventService.onStockDistributedToTech(saved);

        // Evaluate STT stock alerts after distribution reduces STT stock
        stockAlertEvaluationService.evaluateSttStock(stt.getId());
        // Evaluate TECH stock alerts after TECH receives new items
        stockAlertEvaluationService.evaluateTechStock(tech.getId());

        return saved;
    }

    public SttStock getStockForStt(String sttId) {
        return sttStockRepository.findBySttId(sttId)
                .orElseGet(() -> SttStock.builder().sttId(sttId).items(new ArrayList<>()).build());
    }

    public TechStock getStockForTech(String techId) {
        TechStock techStock = techStockRepository.findByTechId(techId)
                .orElseGet(() -> TechStock.builder().techId(techId).items(new ArrayList<>()).build());
        ensureTechStockItems(techStock);
        return techStock;
    }

    public List<TechStock> getTechStocksForStt(String sttId) {
        return techStockRepository.findBySttId(sttId);
    }

    public List<UsedEquipmentItem> syncCaseUsageForWorkSubmission(
            CaseEntity caseEntity,
            AppUser technician,
            List<UsedEquipmentRequestItem> requestedItems,
            String performedBy
    ) {
        if (caseEntity == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case is required for stock usage synchronization");
        }
        if (technician == null || isBlank(technician.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Technician is required for stock usage synchronization");
        }

        // ── Business-usage classification enforcement ─────────────────────────
        // Blocks pack items / non-ACT items in ACT, non-PLT items in PLT, and any
        // consumption on RES cases — even if the frontend filter is bypassed.
        equipmentUsagePolicy.assertConsumable(caseEntity, equipmentIdsOf(requestedItems));
        String usageContext = equipmentUsagePolicy.consumptionAuditLabel(
                equipmentUsagePolicy.resolveContext(caseEntity));
        // ─────────────────────────────────────────────────────────────────────

        // ── Over-limit enforcement ────────────────────────────────────────────
        // Limits are ZM-configured per item via StockThreshold.caseUsageLimit.
        if (requestedItems != null && !requestedItems.isEmpty()) {
            String zmId = resolveZmIdForTech(technician);
            stockUsagePermissionService.enforceUsageLimits(
                    caseEntity.getId(), technician.getId(), zmId, requestedItems);
        }
        // ─────────────────────────────────────────────────────────────────────

        Map<String, UsageItem> requestedUsage = mergeCaseUsageItems(requestedItems);
        Map<String, UsageItem> previousUsage = mergePersistedUsageItems(
                caseEntity.getWork() != null ? caseEntity.getWork().getUsedEquipment() : null
        );
        String previousAppliedTechId = trimToNull(
                caseEntity.getWork() != null ? caseEntity.getWork().getStockAppliedTechId() : null
        );

        Map<String, UsageItem> baselineUsage = Map.of();
        TechStock currentStock = null;
        TechStock previousAppliedStock = null;
        if (previousAppliedTechId != null) {
            if (previousAppliedTechId.equals(technician.getId())) {
                baselineUsage = previousUsage;
            } else if (!previousUsage.isEmpty()) {
                previousAppliedStock = findTechStockOrThrow(
                        previousAppliedTechId,
                        "Cannot restore previously used equipment because technician stock was not found for " + previousAppliedTechId
                );
                validateStockRestoration(previousAppliedStock, previousUsage, caseEntity.getId());
            }
        }

        boolean needsCurrentStock = !requestedUsage.isEmpty() || !baselineUsage.isEmpty();
        if (needsCurrentStock) {
            currentStock = findTechStockOrThrow(
                    technician.getId(),
                    "No stock found for technician " + technician.getId()
            );
            hydrateUsageLabelsFromStock(currentStock, requestedUsage);
            validateRequestedUsageAgainstStock(currentStock, requestedUsage, baselineUsage, caseEntity);
        }

        boolean previousStockChanged = false;
        if (previousAppliedStock != null) {
            previousStockChanged = restoreAppliedUsage(
                    previousAppliedStock,
                    previousUsage,
                    caseEntity.getId(),
                    performedBy,
                    "case_reassigned_or_resubmitted"
            );
        }

        boolean currentStockChanged = false;
        if (currentStock != null) {
            currentStockChanged = applyUsageDelta(
                    currentStock,
                    requestedUsage,
                    baselineUsage,
                    caseEntity,
                    technician,
                    performedBy
            );
        }

        Instant syncedAt = Instant.now();

        if (previousStockChanged) {
            // Restore usage for the old tech: decrement each item that was previously applied.
            for (Map.Entry<String, UsageItem> entry : previousUsage.entrySet()) {
                Query q = Query.query(
                        Criteria.where("techId").is(previousAppliedTechId)
                                .and("items.equipmentId").is(entry.getKey())
                );
                mongoTemplate.updateFirst(q,
                        new Update().inc("items.$.usedQuantity", -entry.getValue().getQuantity())
                                .set("lastUpdatedAt", syncedAt),
                        TechStock.class);
            }
        }

        if (currentStockChanged) {
            // Apply the net delta (requested - baseline) for each item via $inc.
            Set<String> allKeys = new java.util.LinkedHashSet<>();
            allKeys.addAll(baselineUsage.keySet());
            allKeys.addAll(requestedUsage.keySet());
            int zero = 0;
            for (String equipmentId : allKeys) {
                int prev = baselineUsage.containsKey(equipmentId)
                        ? baselineUsage.get(equipmentId).getQuantity() : zero;
                int req  = requestedUsage.containsKey(equipmentId)
                        ? requestedUsage.get(equipmentId).getQuantity() : zero;
                int delta = req - prev;
                if (delta == 0) continue;
                Query q = Query.query(
                        Criteria.where("techId").is(technician.getId())
                                .and("items.equipmentId").is(equipmentId)
                );
                mongoTemplate.updateFirst(q,
                        new Update().inc("items.$.usedQuantity", delta)
                                .set("lastUpdatedAt", syncedAt),
                        TechStock.class);
            }
        }

        if (!requestedUsage.isEmpty() && currentStock != null) {
            List<ConsumedEquipmentItem> consumedItems = new java.util.ArrayList<>();
            TechStock stockSnapshot = currentStock;
            for (Map.Entry<String, UsageItem> entry : requestedUsage.entrySet()) {
                String equipId = entry.getKey();
                UsageItem requestedItem = entry.getValue();
                TechStockItem stockItem = stockSnapshot.getItems().stream()
                        .filter(i -> equipId.equals(i.getEquipmentId()))
                        .findFirst().orElse(null);
                if (stockItem == null) continue;
                int baseline = baselineUsage.containsKey(equipId)
                        ? baselineUsage.get(equipId).getQuantity() : 0;
                int stockBefore = stockItem.getRemainingQuantity() + baseline;
                int stockAfter = stockBefore - requestedItem.getQuantity();
                String name = requestedItem.getEquipmentName() != null
                        ? requestedItem.getEquipmentName() : stockItem.getEquipmentName();
                String unit = requestedItem.getUnit() != null
                        ? requestedItem.getUnit() : stockItem.getUnit();
                consumedItems.add(ConsumedEquipmentItem.builder()
                        .equipmentId(equipId)
                        .equipmentName(name)
                        .unit(unit)
                        .quantityUsed(requestedItem.getQuantity())
                        .stockBefore(stockBefore)
                        .stockAfter(stockAfter)
                        .build());
            }
            if (!consumedItems.isEmpty()) {
                caseStockConsumptionService.createOrUpdate(caseEntity, technician, consumedItems, performedBy, usageContext);
            }
        }

        // ── Per-unit tracking: reconcile the exact tracked units consumed on this case ──
        equipmentInstanceService.syncCaseUsageInstances(
                caseEntity, InstanceOwnerType.TECH, technician.getId(), technician.getFullName(),
                quantitiesByEquipment(requestedUsage), mergeInstanceIdsFromUsed(requestedItems), performedBy);

        // Evaluate TECH stock alerts after consumption
        stockAlertEvaluationService.evaluateTechStock(technician.getId());

        return toUsedEquipmentItems(requestedUsage);
    }

    public void releaseCaseUsageReservation(
            CaseEntity caseEntity,
            String performedBy,
            String reason,
            boolean restoreStock
    ) {
        if (caseEntity == null || caseEntity.getWork() == null) {
            return;
        }

        // Restore any tracked units consumed on this case back to their holder's stock.
        if (restoreStock) {
            equipmentInstanceService.restoreAllCaseUsage(caseEntity.getId(), performedBy);
        }

        String appliedTechId = trimToNull(caseEntity.getWork().getStockAppliedTechId());
        if (appliedTechId == null) {
            return;
        }

        Map<String, UsageItem> appliedUsage = mergePersistedUsageItems(caseEntity.getWork().getUsedEquipment());
        if (restoreStock && !appliedUsage.isEmpty()) {
            TechStock stock = findTechStockOrThrow(
                    appliedTechId,
                    "Cannot restore technician stock because technician stock was not found for " + appliedTechId
            );
            validateStockRestoration(stock, appliedUsage, caseEntity.getId());

            if (restoreAppliedUsage(stock, appliedUsage, caseEntity.getId(), performedBy, reason)) {
                stock.setLastUpdatedAt(Instant.now());
                techStockRepository.save(stock);
            }
        }

        caseEntity.getWork().setStockAppliedTechId(null);
        log.info(
                "case_stock_usage_reservation_released caseId={} techId={} restoreStock={} actor={} reason={}",
                caseEntity.getId(),
                appliedTechId,
                restoreStock,
                performedBy,
                reason
        );
    }

    public DeclareTechUsageResponse declareUsage(AppUser tech, DeclareTechUsageRequest request) {
        CaseEntity caseEntity = caseRepository.findById(request.getCaseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));

        boolean matchById    = tech.getId() != null && tech.getId().equals(caseEntity.getTechId());
        boolean matchByEmail = tech.getEmail() != null
                && tech.getEmail().equalsIgnoreCase(caseEntity.getTechEmail());
        if (!matchById && !matchByEmail) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This case is not assigned to you");
        }

        // Same ACT/PLT/RES classification enforcement as the work-submission path.
        List<String> declaredIds = request.getItems() == null ? List.of()
                : request.getItems().stream()
                        .filter(i -> i != null && !isBlank(i.getEquipmentId()))
                        .map(i -> i.getEquipmentId().trim())
                        .distinct()
                        .collect(Collectors.toList());
        equipmentUsagePolicy.assertConsumable(caseEntity, declaredIds);

        TechStock techStock = techStockRepository.findByTechId(tech.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No stock found for your account"));

        ensureTechStockItems(techStock);

        Map<String, TechStockItem> techStockMap = techStock.getItems().stream()
                .collect(Collectors.toMap(TechStockItem::getEquipmentId, ti -> ti));

        List<String> warnings = new ArrayList<>();
        Instant declaredAt = Instant.now();
        List<UsageItem> usageItems = new ArrayList<>();

        // Validate all items first before applying any updates
        Map<String, UsageItem> mergedItems = mergeUsageItems(request.getItems());
        for (UsageItem requestedItem : mergedItems.values()) {
            TechStockItem stockItem = techStockMap.get(requestedItem.getEquipmentId());
            if (stockItem == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Equipment is not part of your stock: " + requestedItem.getEquipmentId()
                );
            }

            int remainingBefore = stockItem.getRemainingQuantity();
            if (remainingBefore < requestedItem.getQuantity()) {
                int overflowQuantity = requestedItem.getQuantity() - remainingBefore;
                warnings.add(
                        firstNonBlank(requestedItem.getEquipmentName(), stockItem.getEquipmentName())
                                + " exceeds remaining stock by "
                                + overflowQuantity
                                + " on case "
                                + firstNonBlank(caseEntity.getCrmCase(), caseEntity.getId())
                );
            }

            usageItems.add(UsageItem.builder()
                    .equipmentId(requestedItem.getEquipmentId())
                    .equipmentName(firstNonBlank(requestedItem.getEquipmentName(), stockItem.getEquipmentName()))
                    .unit(firstNonBlank(requestedItem.getUnit(), stockItem.getUnit()))
                    .quantity(requestedItem.getQuantity())
                    .build());
        }

        // Apply each usage as an atomic $inc on the embedded array element.
        // This avoids any full-document-replace issue with the Lombok @Builder.Default fields.
        for (UsageItem requestedItem : mergedItems.values()) {
            Query query = Query.query(
                    Criteria.where("techId").is(tech.getId())
                            .and("items.equipmentId").is(requestedItem.getEquipmentId())
            );
            Update update = new Update()
                    .inc("items.$.usedQuantity", requestedItem.getQuantity())
                    .set("lastUpdatedAt", declaredAt);
            mongoTemplate.updateFirst(query, update, TechStock.class);
        }

        // ── Per-unit tracking: reconcile the exact tracked units consumed on this case ──
        equipmentInstanceService.syncCaseUsageInstances(
                caseEntity, InstanceOwnerType.TECH, tech.getId(), tech.getFullName(),
                quantitiesByEquipment(mergedItems), mergeInstanceIdsFromTech(request.getItems()),
                trimToNull(tech.getEmail()) != null ? tech.getEmail() : tech.getId());

        TechStockUsage usage = techStockUsageRepository.save(TechStockUsage.builder()
                .techId(tech.getId())
                .caseId(caseEntity.getId())
                .caseReference(firstNonBlank(caseEntity.getCrmCase(), caseEntity.getId()))
                .items(usageItems)
                .declaredAt(declaredAt)
                .notes(trimToNull(request.getNotes()))
                .build());

        // Re-fetch to return the actual persisted state (not just the in-memory object)
        TechStock updatedStock = techStockRepository.findByTechId(tech.getId())
                .orElse(techStock);

        return DeclareTechUsageResponse.builder()
                .stock(updatedStock)
                .usage(usage)
                .hasWarnings(!warnings.isEmpty())
                .warnings(warnings)
                .build();
    }

    public List<SttDistribution> getDistributionHistoryForStt(String sttId) {
        return distributionRepository.findBySttIdOrderByDistributedAtDesc(sttId);
    }

    public List<PendingDistributionDto> getPendingConfirmationsForTech(String techId) {
        return distributionRepository
                .findByTechIdAndStatusOrderByDistributedAtDesc(techId, DistributionStatus.PENDING_TECH_CONFIRMATION)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public PendingDistributionDto getPendingConfirmationById(String distributionId, String techId) {
        SttDistribution dist = distributionRepository.findById(distributionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribution not found"));
        if (!dist.getTechId().equals(techId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This distribution does not belong to you");
        }
        if (dist.getStatus() != DistributionStatus.PENDING_TECH_CONFIRMATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution is no longer pending");
        }
        return toDto(dist);
    }

    public void confirmDistributionByTech(String distributionId, String techId, String techEmail) {
        SttDistribution dist = distributionRepository.findById(distributionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribution not found"));
        if (!dist.getTechId().equals(techId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This distribution does not belong to you");
        }
        if (dist.getStatus() != DistributionStatus.PENDING_TECH_CONFIRMATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution already processed");
        }

        AppUser tech = userRepository.findById(techId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tech user not found"));

        updateTechStock(tech, dist.getSttId(), dist.getItems());

        dist.setStatus(DistributionStatus.CONFIRMED_BY_TECH);
        dist.setConfirmedAt(Instant.now());
        dist.setConfirmedBy(techEmail);
        distributionRepository.save(dist);

        stockMovementService.recordTechConfirmation(dist, techEmail);
    }

    private PendingDistributionDto toDto(SttDistribution dist) {
        return PendingDistributionDto.builder()
                .id(dist.getId())
                .sttId(dist.getSttId())
                .sttName(dist.getSttName())
                .sttEmail(dist.getSttEmail())
                .techId(dist.getTechId())
                .techName(dist.getTechName())
                .dpmId(dist.getDpmId())
                .items(dist.getItems())
                .status(dist.getStatus())
                .distributedAt(dist.getDistributedAt())
                .notes(dist.getNotes())
                .build();
    }

    public List<DistributableDpmDto> getDistributableDpms(String sttId) {
        List<DpmRequest> dpms = dpmRepository.findBySttIdOrderByCreatedAtDesc(sttId).stream()
                .filter(d -> d.getStatus() == DpmStatus.FULLY_CONFIRMED
                          || d.getStatus() == DpmStatus.PARTIALLY_CONFIRMED
                          || d.getStatus() == DpmStatus.MATERIAL_RECEIVED)
                .toList();

        return dpms.stream().map(dpm -> {
            Map<String, Integer> alreadyDistributed = new HashMap<>();
            for (SttDistribution dist : distributionRepository.findByDpmId(dpm.getId())) {
                for (DistributionItem di : dist.getItems()) {
                    alreadyDistributed.merge(di.getEquipmentId(), di.getQuantity(), Integer::sum);
                }
            }
            List<DistributableDpmItemDto> items = dpm.getItems().stream()
                    .filter(i -> i.getConfirmedQuantity() != null && i.getConfirmedQuantity() > 0)
                    .map(i -> {
                        int confirmed = i.getConfirmedQuantity();
                        int distributed = alreadyDistributed.getOrDefault(i.getEquipmentId(), 0);
                        int remaining = Math.max(0, confirmed - distributed);
                        return DistributableDpmItemDto.builder()
                                .equipmentId(i.getEquipmentId())
                                .equipmentName(i.getEquipmentName())
                                .unit(i.getUnit())
                                .confirmedQuantity(confirmed)
                                .distributedQuantity(distributed)
                                .remainingQuantity(remaining)
                                .build();
                    })
                    .filter(i -> i.getRemainingQuantity() > 0)
                    .toList();

            return DistributableDpmDto.builder()
                    .id(dpm.getId())
                    .dpmNumber(dpm.getDpmNumber())
                    .zmName(dpm.getZmName())
                    .status(dpm.getStatus())
                    .createdAt(dpm.getCreatedAt())
                    .confirmedAt(dpm.getConfirmedAt())
                    .items(items)
                    .build();
        })
        .filter(dto -> !dto.getItems().isEmpty())
        .toList();
    }

    public List<SttDistribution> getAllDistributions() {
        return distributionRepository.findAll();
    }

    public List<SttStock> getAllSttStocks() {
        return sttStockRepository.findAll();
    }

    public List<TechStock> getAllTechStocks() {
        return techStockRepository.findAll();
    }

    public List<TechStockUsage> getAllTechUsages() {
        return techStockUsageRepository.findAllByOrderByDeclaredAtDesc();
    }

    public List<TechStockUsage> getTechUsagesForStts(List<String> sttIds) {
        if (sttIds == null || sttIds.isEmpty()) {
            return List.of();
        }

        List<String> techIds = sttIds.stream()
                .flatMap(sttId -> techStockRepository.findBySttId(sttId).stream())
                .map(TechStock::getTechId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        if (techIds.isEmpty()) {
            return List.of();
        }

        return techStockUsageRepository.findByTechIdInOrderByDeclaredAtDesc(techIds);
    }

    private void updateTechStock(AppUser tech, String sttId, List<DistributionItem> items) {
        TechStock techStock = techStockRepository.findByTechId(tech.getId())
                .orElseGet(() -> TechStock.builder()
                        .techId(tech.getId())
                        .sttId(sttId)
                        .items(new ArrayList<>())
                        .build());
        ensureTechStockItems(techStock);

        Map<String, TechStockItem> techStockMap = techStock.getItems().stream()
                .collect(Collectors.toMap(TechStockItem::getEquipmentId, ti -> ti));

        for (DistributionItem di : items) {
            TechStockItem ti = techStockMap.get(di.getEquipmentId());
            if (ti != null) {
                ti.setReceivedQuantity(ti.getReceivedQuantity() + di.getQuantity());
                ti.setLastReceivedAt(Instant.now());
                if (ti.getUnit() == null && di.getUnit() != null) {
                    ti.setUnit(di.getUnit());
                }
            } else {
                techStock.getItems().add(TechStockItem.builder()
                        .equipmentId(di.getEquipmentId())
                        .equipmentName(di.getEquipmentName())
                        .unit(di.getUnit())
                        .receivedQuantity(di.getQuantity())
                        .lastReceivedAt(Instant.now())
                        .build());
            }
        }

        techStock.setLastUpdatedAt(Instant.now());
        techStockRepository.save(techStock);
    }

    /** Resolves the ZM responsible for a TECH via the TECH→STT→ZM chain. Returns null if not resolvable. */
    private String resolveZmIdForTech(AppUser tech) {
        if (tech.getAssignedSttId() == null) return null;
        return userRepository.findById(tech.getAssignedSttId())
                .map(AppUser::getAssignedZmId)
                .orElse(null);
    }

    private AppUser findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private void ensureTechStockItems(TechStock techStock) {
        if (techStock.getItems() == null) {
            techStock.setItems(new ArrayList<>());
        }
    }

    private TechStock findTechStockOrThrow(String techId, String errorMessage) {
        TechStock techStock = techStockRepository.findByTechId(techId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage));
        ensureTechStockItems(techStock);
        return techStock;
    }

    private void validateRequestedUsageAgainstStock(
            TechStock techStock,
            Map<String, UsageItem> requestedUsage,
            Map<String, UsageItem> baselineUsage,
            CaseEntity caseEntity
    ) {
        for (UsageItem requestedItem : requestedUsage.values()) {
            TechStockItem stockItem = getTechStockItemOrThrow(
                    techStock,
                    requestedItem.getEquipmentId(),
                    "Equipment is not part of the technician stock: " + requestedItem.getEquipmentId()
            );

            int alreadyApplied = baselineUsage.getOrDefault(requestedItem.getEquipmentId(), UsageItem.builder().quantity(0).build())
                    .getQuantity();
            int additionalRequired = requestedItem.getQuantity() - alreadyApplied;
            if (additionalRequired <= 0) {
                continue;
            }

            int available = stockItem.getRemainingQuantity();
            if (available < additionalRequired) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Insufficient technician stock for "
                                + firstNonBlank(requestedItem.getEquipmentName(), stockItem.getEquipmentName())
                                + ": available=" + available
                                + ", additionalRequired=" + additionalRequired
                                + ", requestedTotal=" + requestedItem.getQuantity()
                                + ", caseId=" + caseEntity.getId()
                );
            }
        }
    }

    private void validateStockRestoration(TechStock techStock, Map<String, UsageItem> appliedUsage, String caseId) {
        for (UsageItem appliedItem : appliedUsage.values()) {
            TechStockItem stockItem = getTechStockItemOrThrow(
                    techStock,
                    appliedItem.getEquipmentId(),
                    "Cannot restore equipment because it no longer exists in technician stock: " + appliedItem.getEquipmentId()
            );
            if (stockItem.getUsedQuantity() < appliedItem.getQuantity()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot restore technician stock for case "
                                + caseId
                                + " because used quantity is already below the applied amount for equipment "
                                + appliedItem.getEquipmentId()
                );
            }
        }
    }

    private boolean applyUsageDelta(
            TechStock techStock,
            Map<String, UsageItem> requestedUsage,
            Map<String, UsageItem> baselineUsage,
            CaseEntity caseEntity,
            AppUser technician,
            String performedBy
    ) {
        boolean changed = false;
        Map<String, UsageItem> comparisonItems = new LinkedHashMap<>();
        baselineUsage.forEach(comparisonItems::putIfAbsent);
        requestedUsage.forEach(comparisonItems::putIfAbsent);

        for (String equipmentId : comparisonItems.keySet()) {
            int previousQuantity = baselineUsage.getOrDefault(equipmentId, UsageItem.builder().quantity(0).build()).getQuantity();
            int requestedQuantity = requestedUsage.getOrDefault(equipmentId, UsageItem.builder().quantity(0).build()).getQuantity();
            int delta = requestedQuantity - previousQuantity;
            if (delta == 0) {
                continue;
            }

            TechStockItem stockItem = getTechStockItemOrThrow(
                    techStock,
                    equipmentId,
                    "Equipment is not part of the technician stock: " + equipmentId
            );

            int nextUsedQuantity = stockItem.getUsedQuantity() + delta;
            if (nextUsedQuantity < 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Technician stock cannot become negative for equipment " + equipmentId
                );
            }

            stockItem.setUsedQuantity(nextUsedQuantity);
            changed = true;

            UsageItem referenceItem = requestedUsage.containsKey(equipmentId)
                    ? requestedUsage.get(equipmentId)
                    : baselineUsage.get(equipmentId);

            if (delta > 0) {
                stockMovementService.recordCaseUsage(caseEntity, technician, referenceItem, delta, performedBy);
                log.info(
                        "case_stock_usage_applied caseId={} techId={} equipmentId={} delta={} remaining={} actor={}",
                        caseEntity.getId(),
                        technician.getId(),
                        equipmentId,
                        delta,
                        stockItem.getRemainingQuantity(),
                        performedBy
                );
            } else {
                log.info(
                        "case_stock_usage_reduced caseId={} techId={} equipmentId={} delta={} remaining={} actor={}",
                        caseEntity.getId(),
                        technician.getId(),
                        equipmentId,
                        delta,
                        stockItem.getRemainingQuantity(),
                        performedBy
                );
            }
        }

        return changed;
    }

    private boolean restoreAppliedUsage(
            TechStock techStock,
            Map<String, UsageItem> appliedUsage,
            String caseId,
            String performedBy,
            String reason
    ) {
        boolean changed = false;
        for (UsageItem appliedItem : appliedUsage.values()) {
            TechStockItem stockItem = getTechStockItemOrThrow(
                    techStock,
                    appliedItem.getEquipmentId(),
                    "Cannot restore equipment because it no longer exists in technician stock: " + appliedItem.getEquipmentId()
            );

            int nextUsedQuantity = stockItem.getUsedQuantity() - appliedItem.getQuantity();
            if (nextUsedQuantity < 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Technician stock cannot become negative while restoring case usage for equipment "
                                + appliedItem.getEquipmentId()
                );
            }

            stockItem.setUsedQuantity(nextUsedQuantity);
            changed = true;

            log.info(
                    "case_stock_usage_restored caseId={} techId={} equipmentId={} quantity={} remaining={} actor={} reason={}",
                    caseId,
                    techStock.getTechId(),
                    appliedItem.getEquipmentId(),
                    appliedItem.getQuantity(),
                    stockItem.getRemainingQuantity(),
                    performedBy,
                    reason
            );
        }

        return changed;
    }

    private TechStockItem getTechStockItemOrThrow(TechStock techStock, String equipmentId, String errorMessage) {
        return techStock.getItems().stream()
                .filter(item -> equipmentId.equals(item.getEquipmentId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage));
    }

    private void hydrateUsageLabelsFromStock(TechStock techStock, Map<String, UsageItem> usageItems) {
        for (UsageItem usageItem : usageItems.values()) {
            TechStockItem stockItem = getTechStockItemOrThrow(
                    techStock,
                    usageItem.getEquipmentId(),
                    "Equipment is not part of the technician stock: " + usageItem.getEquipmentId()
            );
            usageItem.setEquipmentName(firstNonBlank(usageItem.getEquipmentName(), stockItem.getEquipmentName()));
            usageItem.setUnit(firstNonBlank(usageItem.getUnit(), stockItem.getUnit()));
        }
    }

    private Map<String, UsageItem> mergeCaseUsageItems(List<UsedEquipmentRequestItem> items) {
        Map<String, UsageItem> merged = new LinkedHashMap<>();
        if (items == null) {
            return merged;
        }

        for (UsedEquipmentRequestItem item : items) {
            if (item == null || isBlank(item.equipmentId())) {
                continue;
            }

            UsageItem existing = merged.get(item.equipmentId().trim());
            if (existing == null) {
                merged.put(item.equipmentId().trim(), UsageItem.builder()
                        .equipmentId(item.equipmentId().trim())
                        .equipmentName(trimToNull(item.equipmentName()))
                        .unit(trimToNull(item.unit()))
                        .quantity(item.quantity())
                        .build());
                continue;
            }

            existing.setQuantity(existing.getQuantity() + item.quantity());
            if (isBlank(existing.getEquipmentName())) {
                existing.setEquipmentName(trimToNull(item.equipmentName()));
            }
            if (isBlank(existing.getUnit())) {
                existing.setUnit(trimToNull(item.unit()));
            }
        }

        return merged;
    }

    private Map<String, UsageItem> mergePersistedUsageItems(List<UsedEquipmentItem> items) {
        Map<String, UsageItem> merged = new LinkedHashMap<>();
        if (items == null) {
            return merged;
        }

        for (UsedEquipmentItem item : items) {
            if (item == null || isBlank(item.getEquipmentId())) {
                continue;
            }

            UsageItem existing = merged.get(item.getEquipmentId().trim());
            if (existing == null) {
                merged.put(item.getEquipmentId().trim(), UsageItem.builder()
                        .equipmentId(item.getEquipmentId().trim())
                        .equipmentName(trimToNull(item.getEquipmentName()))
                        .unit(trimToNull(item.getUnit()))
                        .quantity(item.getQuantity())
                        .build());
                continue;
            }

            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            if (isBlank(existing.getEquipmentName())) {
                existing.setEquipmentName(trimToNull(item.getEquipmentName()));
            }
            if (isBlank(existing.getUnit())) {
                existing.setUnit(trimToNull(item.getUnit()));
            }
        }

        return merged;
    }

    private List<UsedEquipmentItem> toUsedEquipmentItems(Map<String, UsageItem> usageItems) {
        return usageItems.values().stream()
                .map(item -> UsedEquipmentItem.builder()
                        .equipmentId(item.getEquipmentId())
                        .equipmentName(item.getEquipmentName())
                        .unit(item.getUnit())
                        .quantity(item.getQuantity())
                        .build())
                .toList();
    }

    private Map<String, UsageItem> mergeUsageItems(List<TechUsageItemRequest> items) {
        Map<String, UsageItem> merged = new LinkedHashMap<>();
        if (items == null) {
            return merged;
        }

        for (TechUsageItemRequest item : items) {
            if (item == null || isBlank(item.getEquipmentId())) {
                continue;
            }

            UsageItem existing = merged.get(item.getEquipmentId());
            if (existing == null) {
                merged.put(item.getEquipmentId(), UsageItem.builder()
                        .equipmentId(item.getEquipmentId().trim())
                        .equipmentName(trimToNull(item.getEquipmentName()))
                        .unit(trimToNull(item.getUnit()))
                        .quantity(item.getQuantity())
                        .build());
                continue;
            }

            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            if (isBlank(existing.getEquipmentName())) {
                existing.setEquipmentName(trimToNull(item.getEquipmentName()));
            }
            if (isBlank(existing.getUnit())) {
                existing.setUnit(trimToNull(item.getUnit()));
            }
        }

        return merged;
    }

    // ── Per-unit tracking helpers ───────────────────────────────────────────────

    /** equipmentId → consumed quantity, from a merged usage map. */
    private Map<String, Integer> quantitiesByEquipment(Map<String, UsageItem> usage) {
        Map<String, Integer> result = new LinkedHashMap<>();
        usage.forEach((equipmentId, item) -> result.put(equipmentId, item.getQuantity()));
        return result;
    }

    /** equipmentId → selected tracked-unit ids, merged across duplicate request lines. */
    private Map<String, List<String>> mergeInstanceIdsFromUsed(List<UsedEquipmentRequestItem> items) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (UsedEquipmentRequestItem item : items) {
            if (item == null || isBlank(item.equipmentId()) || item.instanceIds() == null) {
                continue;
            }
            result.computeIfAbsent(item.equipmentId().trim(), k -> new ArrayList<>())
                    .addAll(item.instanceIds());
        }
        return result;
    }

    /** equipmentId → selected tracked-unit ids, merged across duplicate request lines. */
    private Map<String, List<String>> mergeInstanceIdsFromTech(List<TechUsageItemRequest> items) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (TechUsageItemRequest item : items) {
            if (item == null || isBlank(item.getEquipmentId()) || item.getInstanceIds() == null) {
                continue;
            }
            result.computeIfAbsent(item.getEquipmentId().trim(), k -> new ArrayList<>())
                    .addAll(item.getInstanceIds());
        }
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String primary, String fallback) {
        String safePrimary = trimToNull(primary);
        return safePrimary != null ? safePrimary : trimToNull(fallback);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Projects the STT's warehouse SttStock into a TechStock shape so the field-execution
     * stock picker (designed for TechStock) can display STT self-execution stock without
     * requiring a separate TechStock document.
     * availableQuantity → receivedQuantity, usedQuantity = 0.
     */
    public TechStock getSttStockAsTechView(String sttId) {
        SttStock sttStock = sttStockRepository.findBySttId(sttId)
                .orElseGet(() -> SttStock.builder().sttId(sttId).items(new ArrayList<>()).build());

        List<TechStockItem> items = sttStock.getItems().stream()
                .filter(i -> i.getAvailableQuantity() > 0)
                .map(i -> TechStockItem.builder()
                        .equipmentId(i.getEquipmentId())
                        .equipmentName(i.getEquipmentName())
                        .unit(i.getUnit())
                        .receivedQuantity(i.getAvailableQuantity())
                        .usedQuantity(0)
                        .lastReceivedAt(i.getLastReceivedAt())
                        .build())
                .collect(Collectors.toList());

        return TechStock.builder()
                .techId(sttId)
                .sttId(sttId)
                .items(items)
                .lastUpdatedAt(sttStock.getLastUpdatedAt())
                .build();
    }

    /**
     * Deducts used equipment directly from the STT's warehouse SttStock for self-execution
     * complaint cases. Handles re-submissions by restoring the previous deduction first.
     */
    public List<UsedEquipmentItem> syncSttWarehouseUsageForCase(
            CaseEntity caseEntity,
            AppUser stt,
            List<UsedEquipmentRequestItem> requestedItems,
            String performedBy
    ) {
        // Same business-usage enforcement as the TECH path, for STT self-execution.
        equipmentUsagePolicy.assertConsumable(caseEntity, equipmentIdsOf(requestedItems));
        String usageContext = equipmentUsagePolicy.consumptionAuditLabel(
                equipmentUsagePolicy.resolveContext(caseEntity));

        SttStock stock = sttStockRepository.findBySttId(stt.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "STT stock not found for self-execution"));

        // Restore previous deduction on re-submission
        String prevAppliedId = caseEntity.getWork() != null ? caseEntity.getWork().getStockAppliedTechId() : null;
        if (stt.getId().equals(prevAppliedId) && caseEntity.getWork().getUsedEquipment() != null) {
            for (UsedEquipmentItem prev : caseEntity.getWork().getUsedEquipment()) {
                stock.getItems().stream()
                        .filter(i -> prev.getEquipmentId().equals(i.getEquipmentId()))
                        .findFirst()
                        .ifPresent(i -> i.setTotalDistributedToTechs(
                                Math.max(0, i.getTotalDistributedToTechs() - prev.getQuantity())));
            }
        }

        // Merge duplicate equipment IDs
        Map<String, Integer> requested = new LinkedHashMap<>();
        if (requestedItems != null) {
            for (UsedEquipmentRequestItem req : requestedItems) {
                if (req.equipmentId() != null && req.quantity() > 0) {
                    requested.merge(req.equipmentId(), req.quantity(), Integer::sum);
                }
            }
        }

        // Validate and apply deduction
        List<UsedEquipmentItem> result = new ArrayList<>();
        List<ConsumedEquipmentItem> consumedItems = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            SttStockItem item = stock.getItems().stream()
                    .filter(i -> entry.getKey().equals(i.getEquipmentId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Equipment not found in STT stock: " + entry.getKey()));
            int qty = entry.getValue();
            int stockBefore = item.getAvailableQuantity();
            if (stockBefore < qty) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock for " + item.getEquipmentName()
                        + ": requested " + qty + " but only " + stockBefore + " available");
            }
            item.setTotalDistributedToTechs(item.getTotalDistributedToTechs() + qty);
            int stockAfter = item.getAvailableQuantity();
            result.add(UsedEquipmentItem.builder()
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .unit(item.getUnit())
                    .quantity(qty)
                    .build());
            consumedItems.add(ConsumedEquipmentItem.builder()
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .unit(item.getUnit())
                    .quantityUsed(qty)
                    .stockBefore(stockBefore)
                    .stockAfter(stockAfter)
                    .build());
            stockMovementService.recordCaseUsage(
                    caseEntity, stt,
                    UsageItem.builder()
                            .equipmentId(item.getEquipmentId())
                            .equipmentName(item.getEquipmentName())
                            .unit(item.getUnit())
                            .build(),
                    qty, performedBy);
        }

        stock.setLastUpdatedAt(Instant.now());
        sttStockRepository.save(stock);

        // ── Per-unit tracking: reconcile the exact tracked units consumed on this case ──
        equipmentInstanceService.syncCaseUsageInstances(
                caseEntity, InstanceOwnerType.STT, stt.getId(), stt.getFullName(),
                new LinkedHashMap<>(requested), mergeInstanceIdsFromUsed(requestedItems), performedBy);

        if (!consumedItems.isEmpty()) {
            caseStockConsumptionService.createOrUpdate(caseEntity, stt, consumedItems, performedBy, usageContext);
        }
        return result;
    }

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "system";
    }

    /** Distinct, non-blank equipment ids from a requested-usage list (for the usage policy check). */
    private List<String> equipmentIdsOf(List<UsedEquipmentRequestItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(i -> i != null && !isBlank(i.equipmentId()))
                .map(i -> i.equipmentId().trim())
                .distinct()
                .collect(Collectors.toList());
    }
}

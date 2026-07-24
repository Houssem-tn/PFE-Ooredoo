package com.pfe.stock_management.retrieved_stock;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.FileEntityType;
import com.pfe.MainPackage.Entity.FileMetadata;
import com.pfe.MainPackage.Entity.FileRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.FileStorageService;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.RecoveredEquipmentRequestItem;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseTimelineService;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.enums.TrackingMode;
import com.pfe.stock_management.material_return.ReturnCondition;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.service.EquipmentInstanceService;
import com.pfe.stock_management.service.EquipmentUsagePolicyService;
import com.pfe.stock_management.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the technician Retrieved-Stock → ZM collection workflow for PLT (complaint) and RES
 * (résiliation) interventions: a technician declares equipment retrieved from a client, it sits in
 * a {@link RetrievedStockItemStatus#PENDING_COLLECTION} bucket tied to that technician (never
 * consumable, never installable, never movable into {@code TechStock}), and a ZM confirms physical
 * collection directly — no schedule negotiation.
 *
 * <p>Deliberately separate from {@code MaterialReturnService}: that service is an STT-identity
 * scheduling workflow (propose handover date → STT accepts/rejects → ZM marks handed over) with no
 * technician dimension and no multi-select confirm action. See {@code RetrievedStockItem}'s javadoc.
 */
@Service
@RequiredArgsConstructor
public class RetrievedStockService {

    private static final Logger log = LoggerFactory.getLogger(RetrievedStockService.class);

    private final RetrievedStockItemRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final EquipmentInstanceService equipmentInstanceService;
    private final EquipmentUsagePolicyService equipmentUsagePolicy;
    private final StockMovementService stockMovementService;
    private final NotificationEventService notificationEventService;
    private final CaseTimelineService caseTimelineService;
    private final FileStorageService fileStorageService;

    // ── Technician: declare retrieval ─────────────────────────────────────────

    /**
     * Records a technician's client-material-retrieval declaration (PLT or RES). Only lines whose
     * disposition is physically-held ({@code RETRIEVED}/{@code DAMAGED}) materialise a
     * {@link RetrievedStockItem}; {@code MISSING}/{@code NOT_FOUND}/{@code ALREADY_RETURNED} lines
     * are declared for audit but create nothing here (nothing to collect) — the caller
     * ({@code CaseService.declareClientRecovery}) mirrors the full request, including those, onto
     * the case's {@code WorkInfo} for visibility.
     *
     * @return a list the <b>same size and order as {@code items}</b>, with the created
     * {@link RetrievedStockItem} at each physically-held position and {@code null} at each
     * non-physical (MISSING/NOT_FOUND/ALREADY_RETURNED) position — lets the caller
     * ({@code CaseService.declareClientRecovery}) zip the result back onto the original request by
     * index to build the full {@code WorkInfo} mirror, including the lines that created nothing.
     */
    public List<RetrievedStockItem> recordRetrieval(
            CaseEntity caseEntity,
            AppUser declarer,
            List<RecoveredEquipmentRequestItem> items,
            String comment
    ) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Declare at least one recovered item.");
        }

        List<String> equipmentIds = items.stream()
                .map(RecoveredEquipmentRequestItem::equipmentId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        // Defense in depth: re-assert even though CaseService already resolved the case context,
        // matching SttStockService.syncCaseUsageForWorkSubmission's own re-assertion precedent.
        equipmentUsagePolicy.assertRecoverable(caseEntity, equipmentIds);

        Map<String, Equipment> catalog = new HashMap<>();
        equipmentRepository.findAllById(equipmentIds).forEach(eq -> catalog.put(eq.getId(), eq));

        // In-batch duplicate serial/IMEI rejection — every line's identifiers must be unique
        // against every other line in this same submission, regardless of disposition.
        Set<String> seenImei = new HashSet<>();
        Set<String> seenSerial = new HashSet<>();
        for (RecoveredEquipmentRequestItem item : items) {
            String normImei = normalize(item.imei());
            String normSerial = normalize(item.serialNumber());
            if (normImei != null && !seenImei.add(normImei)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "IMEI '" + item.imei() + "' is repeated in this submission.");
            }
            if (normSerial != null && !seenSerial.add(normSerial)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Serial Number '" + item.serialNumber() + "' is repeated in this submission.");
            }
        }

        AppUser stt = resolveStt(caseEntity);
        AppUser zm = resolveZm(caseEntity);
        String usageContext = equipmentUsagePolicy.resolveContext(caseEntity).name(); // "PLT" or "RES"
        String batchId = "RTS-" + UUID.randomUUID();
        Instant now = Instant.now();
        String actor = declarer != null ? declarer.getEmail() : null;

        List<RetrievedStockItem> positional = new ArrayList<>(); // same size/order as `items`
        List<RetrievedStockItem> created = new ArrayList<>();    // compact, physically-held only
        for (RecoveredEquipmentRequestItem requestItem : items) {
            RecoveryDisposition disposition = parseDisposition(requestItem);
            if (!disposition.isPhysicallyHeld()) {
                positional.add(null); // MISSING / NOT_FOUND / ALREADY_RETURNED — nothing to collect
                continue;
            }

            Equipment eq = catalog.get(requestItem.equipmentId());
            String name = requestItem.equipmentName() != null && !requestItem.equipmentName().isBlank()
                    ? requestItem.equipmentName()
                    : (eq != null ? eq.getName() : requestItem.equipmentId());
            String unit = requestItem.unit() != null && !requestItem.unit().isBlank()
                    ? requestItem.unit()
                    : (eq != null && eq.getUnit() != null ? eq.getUnit().name() : null);
            String articleCode = eq != null ? eq.getArticleCode() : null;

            TrackingMode mode = eq != null && eq.getTrackingMode() != null ? eq.getTrackingMode() : TrackingMode.NONE;
            int quantity = requestItem.quantity();
            if (mode.isTracked()) {
                // One serial/IMEI slot per line — a tech retrieving several identical tracked
                // units submits several lines, mirroring consumption's instanceIds.size()==quantity rule.
                if (quantity != 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            name + " is a tracked item: submit one line per unit (quantity must be 1).");
                }
                String normImei = normalize(requestItem.imei());
                String normSerial = normalize(requestItem.serialNumber());
                if (mode.requiresImei() && normImei == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + ": IMEI is required.");
                }
                if (mode.requiresSerial() && normSerial == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + ": Serial Number is required.");
                }
                if (mode.requiresAtLeastOne() && normImei == null && normSerial == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            name + ": enter at least an IMEI or a Serial Number.");
                }
            }

            RetrievedStockItem item = RetrievedStockItem.builder()
                    .declarationBatchId(batchId)
                    .caseId(caseEntity.getId())
                    .crmCase(caseEntity.getCrmCase())
                    .caseUsageContext(usageContext)
                    .clientName(caseEntity.getClientName())
                    .clientPhone(caseEntity.getClientPhone())
                    .technicianId(declarer != null ? declarer.getId() : null)
                    .technicianName(declarer != null ? declarer.getFullName() : null)
                    .technicianEmail(declarer != null ? declarer.getEmail() : null)
                    .sttId(stt.getId())
                    .sttName(stt.getFullName())
                    .sttEmail(stt.getEmail())
                    .zmId(zm.getId())
                    .zmName(zm.getFullName())
                    .zmEmail(zm.getEmail())
                    .equipmentId(requestItem.equipmentId())
                    .equipmentName(name)
                    .articleCode(articleCode)
                    .unit(unit)
                    .quantity(quantity)
                    .serialNumber(requestItem.serialNumber())
                    .macAddress(requestItem.macAddress())
                    .imei(requestItem.imei())
                    .disposition(disposition)
                    .condition(parseCondition(requestItem.condition()))
                    .dispositionNote(comment)
                    .status(RetrievedStockItemStatus.PENDING_STT_COLLECTION)
                    .retrievedAt(now)
                    .declaredBy(actor)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            item = repository.save(item);

            if (mode.isTracked()) {
                String instanceId = equipmentInstanceService.recordTechRetrieval(
                        eq, caseEntity, requestItem.imei(), requestItem.serialNumber(), item, actor);
                if (instanceId != null) {
                    item.setInstanceId(instanceId);
                    item = repository.save(item);
                }
            }

            positional.add(item);
            created.add(item);
        }

        if (!created.isEmpty()) {
            for (RetrievedStockItem item : created) {
                runSideEffect("record retrieved-stock movement", () ->
                        stockMovementService.recordRetrievedStockDeclared(item, actor));
            }
            runSideEffect("notify ZM about retrieved stock", () ->
                    notificationEventService.onRetrievedStockDeclared(caseEntity, created));
        }

        log.info("retrieved_stock_declared caseId={} technicianId={} batchId={} createdCount={} totalLines={}",
                caseEntity.getId(), declarer != null ? declarer.getId() : null, batchId, created.size(), items.size());

        return positional;
    }

    // ── Reads ──────────────────────────────────────────────────────────────────

    /** Both pending values (new + legacy) so historical documents keep surfacing correctly. */
    @SuppressWarnings("deprecation")
    private static final List<RetrievedStockItemStatus> PENDING_STATUSES = List.of(
            RetrievedStockItemStatus.PENDING_STT_COLLECTION, RetrievedStockItemStatus.PENDING_COLLECTION);

    /** Both collected values (new STT + legacy ZM) for STT Collected Stock / tracking. */
    @SuppressWarnings("deprecation")
    private static final List<RetrievedStockItemStatus> COLLECTED_STATUSES = List.of(
            RetrievedStockItemStatus.COLLECTED_BY_STT, RetrievedStockItemStatus.COLLECTED);

    /** Technician's own pending bucket ("Retrieved Stock — Pending STT Collection"). */
    public List<RetrievedStockItemDto> getMine(String technicianId) {
        return repository.findByTechnicianIdAndStatusInOrderByRetrievedAtDesc(technicianId, PENDING_STATUSES)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /** STT's pending-collection queue, grouped by technician for the STT collection page. */
    public List<TechnicianRetrievedStockGroupDto> getPendingForSttGroupedByTechnician(String sttId) {
        List<RetrievedStockItem> pending = repository.findBySttIdAndStatusInOrderByRetrievedAtDesc(sttId, PENDING_STATUSES);
        return groupByTechnician(pending);
    }

    /** STT Collected Stock — items this STT physically collected, not yet returned. */
    public List<RetrievedStockItemDto> getCollectedForStt(String sttId) {
        return repository.findByCollectedBySttIdAndStatusInOrderByCollectedAtDesc(sttId, COLLECTED_STATUSES)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /** Collected items eligible to be added to a Material Return Request (collected, not yet in one). */
    public List<RetrievedStockItemDto> getReturnEligibleForStt(String sttId) {
        return repository.findByCollectedBySttIdAndStatusAndMaterialReturnRequestIdIsNullOrderByCollectedAtDesc(
                        sttId, RetrievedStockItemStatus.COLLECTED_BY_STT)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── ZM / Admin read-only tracking ──────────────────────────────────────────

    /** ZM read-only tracking: every retrieved item across this ZM's territory (no collect action). */
    public List<RetrievedStockItemDto> getTrackingForZm(String zmId) {
        return repository.findByZmIdOrderByRetrievedAtDesc(zmId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    private List<TechnicianRetrievedStockGroupDto> groupByTechnician(List<RetrievedStockItem> items) {
        Map<String, TechnicianRetrievedStockGroupDto> groups = new LinkedHashMap<>();
        for (RetrievedStockItem item : items) {
            String techId = item.getTechnicianId() != null ? item.getTechnicianId() : "unknown";
            groups.computeIfAbsent(techId, id -> TechnicianRetrievedStockGroupDto.builder()
                    .technicianId(item.getTechnicianId())
                    .technicianName(item.getTechnicianName())
                    .technicianEmail(item.getTechnicianEmail())
                    .items(new ArrayList<>())
                    .build());
            groups.get(techId).getItems().add(toDto(item));
        }
        return groups.values().stream()
                .sorted(Comparator.comparing(g -> g.getTechnicianName() != null ? g.getTechnicianName() : ""))
                .collect(Collectors.toList());
    }

    public List<RetrievedStockItemDto> getByCase(String caseId) {
        return repository.findByCaseIdOrderByRetrievedAtDesc(caseId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RetrievedStockItemDto> getAll() {
        return repository.findAllByOrderByRetrievedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Second phase of the declare flow, mirroring the existing {@code /work} → {@code /work-proof}
     * two-phase pattern: the item must already exist (from {@link #recordRetrieval}) before a proof
     * photo can be tied to it. Owner-only (the declaring technician).
     */
    public RetrievedStockItemDto uploadProof(String itemId, AppUser tech, MultipartFile file) {
        RetrievedStockItem item = repository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Retrieved stock item not found: " + itemId));
        if (tech == null || tech.getId() == null || !tech.getId().equals(item.getTechnicianId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This item does not belong to you.");
        }

        FileMetadata metadata = fileStorageService.upload(
                file, FileEntityType.RETRIEVED_STOCK_ITEM, item.getId(), FileRole.RETRIEVAL_PROOF, tech.getEmail());

        item.setProofFileId(metadata.getId());
        item.setUpdatedAt(Instant.now());
        return toDto(repository.save(item));
    }

    // ── STT: confirm collection (the correct owner of the physical collection step) ──

    /**
     * The one atomic multi-select action: the responsible STT selects any number of pending items
     * (potentially from several of their technicians) and confirms physical collection in one call.
     * Every item is validated (exists, still pending, belongs to a technician under THIS STT)
     * <b>before any is mutated</b> — a stale/foreign/already-collected id rejects the whole call
     * rather than partially applying it (atomic all-or-nothing).
     *
     * <p>Collected items move to the STT's Collected Stock (never into distributable/usable STT stock).
     */
    public ConfirmCollectionResponse confirmCollectionByStt(AppUser stt, ConfirmCollectionRequest request) {
        if (request == null || request.itemIds() == null || request.itemIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one item to confirm collection.");
        }

        List<String> ids = request.itemIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());

        List<RetrievedStockItem> toCollect = repository.findAllById(ids);
        if (toCollect.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more selected items were not found.");
        }
        // Validate the WHOLE batch first — one failure rejects everything (no partial collection).
        for (RetrievedStockItem item : toCollect) {
            if (!stt.getId().equals(item.getSttId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Item " + item.getId() + " belongs to a technician outside your responsibility.");
            }
            if (!item.getStatus().isPendingCollection()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Item " + item.getId() + " is no longer pending collection (already " + item.getStatus() + ").");
            }
        }

        String batchId = "COL-" + UUID.randomUUID();
        Instant now = Instant.now();
        String actor = stt.getEmail();
        String note = request.note();

        List<String> instanceIds = new ArrayList<>();
        List<RetrievedStockItem> collected = new ArrayList<>();
        for (RetrievedStockItem item : toCollect) {
            item.setStatus(RetrievedStockItemStatus.COLLECTED_BY_STT);
            item.setCollectedBySttId(stt.getId());
            item.setCollectedBySttName(stt.getFullName());
            item.setCollectedAt(now);
            item.setCollectionBatchId(batchId);
            item.setCollectionNote(note);
            item.setUpdatedAt(now);
            collected.add(repository.save(item));
            if (item.getInstanceId() != null && !item.getInstanceId().isBlank()) {
                instanceIds.add(item.getInstanceId());
            }
        }

        equipmentInstanceService.markCollectedByStt(instanceIds, stt.getId(), stt.getFullName(), actor);

        for (RetrievedStockItem item : collected) {
            runSideEffect("record retrieved-stock collection movement", () ->
                    stockMovementService.recordRetrievedStockCollected(item, actor));
        }

        // One case-timeline append per distinct case touched.
        Map<String, List<RetrievedStockItem>> byCase = collected.stream()
                .collect(Collectors.groupingBy(RetrievedStockItem::getCaseId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<RetrievedStockItem>> entry : byCase.entrySet()) {
            runSideEffect("append case timeline for retrieved-stock collection", () ->
                    appendCaseTimeline(entry.getKey(), entry.getValue(), actor));
        }

        // One aggregated notification per technician touched, not one per item.
        Map<String, List<RetrievedStockItem>> byTechnician = collected.stream()
                .filter(i -> i.getTechnicianEmail() != null)
                .collect(Collectors.groupingBy(RetrievedStockItem::getTechnicianId, LinkedHashMap::new, Collectors.toList()));
        for (List<RetrievedStockItem> technicianItems : byTechnician.values()) {
            runSideEffect("notify technician about collection", () ->
                    notificationEventService.onRetrievedStockCollected(technicianItems));
        }

        log.info("retrieved_stock_collected sttId={} batchId={} itemCount={}", stt.getId(), batchId, collected.size());

        return ConfirmCollectionResponse.builder()
                .collectedItems(collected.stream().map(this::toDto).collect(Collectors.toList()))
                .collectionBatchId(batchId)
                .build();
    }

    private void appendCaseTimeline(String caseId, List<RetrievedStockItem> items, String actor) {
        if (caseId == null) {
            return;
        }
        caseRepository.findById(caseId).ifPresent(c -> {
            int totalUnits = items.stream().mapToInt(RetrievedStockItem::getQuantity).sum();
            caseTimelineService.append(
                    c,
                    CaseTimelineEventType.RETRIEVED_STOCK_COLLECTED,
                    "Retrieved stock collected by STT",
                    items.size() + " item(s) (" + totalUnits + " unit(s)) collected from the technician's "
                            + "retrieved stock by " + safe(items.get(0).getCollectedBySttName()) + ".",
                    actor,
                    c.getStatus(),
                    c.getStatus(),
                    Map.of(
                            "itemCount", items.size(),
                            "collectionBatchId", items.get(0).getCollectionBatchId() != null
                                    ? items.get(0).getCollectionBatchId() : ""
                    )
            );
            caseRepository.save(c);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private AppUser resolveStt(CaseEntity caseEntity) {
        String sttId = caseEntity.getSttId() != null ? caseEntity.getSttId() : caseEntity.getAssignedSttId();
        if (sttId == null || sttId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This case has no assigned STT, so retrieved material cannot be tracked.");
        }
        return userRepository.findById(sttId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Assigned STT for this case was not found."));
    }

    private AppUser resolveZm(CaseEntity caseEntity) {
        String zmId = caseEntity.getZmId() != null ? caseEntity.getZmId() : caseEntity.getOwnerZmId();
        if (zmId == null || zmId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This case has no owning Zone Manager, so retrieved material cannot be routed for collection.");
        }
        return userRepository.findById(zmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Owning Zone Manager for this case was not found."));
    }

    /** Defaults to RETRIEVED when omitted (Complaint lines never send a disposition). */
    private RecoveryDisposition parseDisposition(RecoveredEquipmentRequestItem item) {
        String raw = item.disposition();
        if (raw == null || raw.isBlank()) {
            return RecoveryDisposition.RETRIEVED;
        }
        try {
            return RecoveryDisposition.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown disposition: " + raw);
        }
    }

    private ReturnCondition parseCondition(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReturnCondition.GOOD;
        }
        try {
            return ReturnCondition.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ReturnCondition.UNKNOWN;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private void runSideEffect(String label, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("[RetrievedStock] side effect failed: {} — {}", label, ex.getMessage());
        }
    }

    private RetrievedStockItemDto toDto(RetrievedStockItem i) {
        return RetrievedStockItemDto.builder()
                .id(i.getId())
                .caseId(i.getCaseId())
                .crmCase(i.getCrmCase())
                .caseUsageContext(i.getCaseUsageContext())
                .clientName(i.getClientName())
                .clientPhone(i.getClientPhone())
                .technicianId(i.getTechnicianId())
                .technicianName(i.getTechnicianName())
                .sttId(i.getSttId())
                .sttName(i.getSttName())
                .zmId(i.getZmId())
                .zmName(i.getZmName())
                .equipmentId(i.getEquipmentId())
                .equipmentName(i.getEquipmentName())
                .articleCode(i.getArticleCode())
                .unit(i.getUnit())
                .quantity(i.getQuantity())
                .serialNumber(i.getSerialNumber())
                .macAddress(i.getMacAddress())
                .imei(i.getImei())
                .instanceId(i.getInstanceId())
                .disposition(i.getDisposition() != null ? i.getDisposition().name() : null)
                .condition(i.getCondition() != null ? i.getCondition().name() : null)
                .dispositionNote(i.getDispositionNote())
                .proofFileId(i.getProofFileId())
                .proofViewUrl(i.getProofFileId() != null ? "/api/v1/files/" + i.getProofFileId() + "/view" : null)
                .proofDownloadUrl(i.getProofFileId() != null ? "/api/v1/files/" + i.getProofFileId() + "/download" : null)
                .status(i.getStatus() != null ? i.getStatus().name() : null)
                .retrievedAt(i.getRetrievedAt())
                .declaredBy(i.getDeclaredBy())
                .collectedBySttId(i.getCollectedBySttId())
                .collectedBySttName(i.getCollectedBySttName())
                .collectedByZmId(i.getCollectedByZmId())
                .collectedByZmName(i.getCollectedByZmName())
                .collectedAt(i.getCollectedAt())
                .collectionNote(i.getCollectionNote())
                .materialReturnRequestId(i.getMaterialReturnRequestId())
                .addedToReturnAt(i.getAddedToReturnAt())
                .returnedAt(i.getReturnedAt())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}

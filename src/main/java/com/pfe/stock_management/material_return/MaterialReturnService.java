package com.pfe.stock_management.material_return;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.RecoveredEquipmentRequestItem;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseTimelineService;
import com.pfe.metrics.BusinessMetrics;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.entity.EquipmentInstance;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.enums.TrackingMode;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItem;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItemRepository;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItemStatus;
import com.pfe.stock_management.service.EquipmentInstanceService;
import com.pfe.stock_management.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MaterialReturnService {

    private static final Logger log = LoggerFactory.getLogger(MaterialReturnService.class);

    private static final List<DpmStatus> DELIVERED_DPM_STATUSES = List.of(
            DpmStatus.MATERIAL_RECEIVED,
            DpmStatus.COMPLETED,
            DpmStatus.FULLY_CONFIRMED
    );

    // Statuses that mean "ZM still needs to act"
    private static final List<MaterialReturnStatus> ZM_PENDING_STATUSES = List.of(
            MaterialReturnStatus.PENDING,
            MaterialReturnStatus.SUBMITTED,
            MaterialReturnStatus.PENDING_ZM_REVIEW,
            MaterialReturnStatus.RESCHEDULE_REQUESTED,
            MaterialReturnStatus.SCHEDULE_REJECTED_BY_STT
    );

    private static final List<MaterialReturnStatus> STT_CONFIRMATION_STATUSES = List.of(
            MaterialReturnStatus.SCHEDULE_PROPOSED,
            MaterialReturnStatus.PENDING_STT_CONFIRMATION,
            MaterialReturnStatus.WAITING_STT_CONFIRMATION,
            MaterialReturnStatus.SCHEDULE_PROPOSED_BY_ZM
    );

    private static final List<MaterialReturnStatus> HANDOVER_CONFIRMED_STATUSES = List.of(
            MaterialReturnStatus.SCHEDULE_CONFIRMED,
            MaterialReturnStatus.APPROVED
    );

    private static final List<MaterialReturnStatus> ACTIVE_RETURN_STATUSES = List.of(
            MaterialReturnStatus.PENDING,
            MaterialReturnStatus.SUBMITTED,
            MaterialReturnStatus.PENDING_ZM_REVIEW,
            MaterialReturnStatus.SCHEDULE_PROPOSED,
            MaterialReturnStatus.PENDING_STT_CONFIRMATION,
            MaterialReturnStatus.WAITING_STT_CONFIRMATION,
            MaterialReturnStatus.SCHEDULE_PROPOSED_BY_ZM,
            MaterialReturnStatus.RESCHEDULE_REQUESTED,
            MaterialReturnStatus.SCHEDULE_REJECTED_BY_STT,
            MaterialReturnStatus.SCHEDULE_CONFIRMED,
            MaterialReturnStatus.APPROVED
    );

    private final MaterialReturnRepository returnRepository;
    private final SttStockRepository sttStockRepository;
    private final EquipmentRepository equipmentRepository;
    private final RetrievedStockItemRepository retrievedStockItemRepository;
    private final UserRepository userRepository;
    private final DpmRepository dpmRepository;
    private final CaseRepository caseRepository;
    private final CaseTimelineService caseTimelineService;
    private final StockMovementService stockMovementService;
    private final NotificationEventService notificationEventService;
    private final EquipmentInstanceService equipmentInstanceService;
    private final BusinessMetrics businessMetrics;

    // ── STT: eligible ZMs ──────────────────────────────────────────────────────

    public List<EligibleZmDto> getEligibleZms() {
        return userRepository
                .findByRoleAndCanCreateDpmAndStatusAndEnabledOrderByFullNameAsc(
                        UserRole.ROLE_ZM, true, AccountStatus.APPROVED, true)
                .stream()
                .map(zm -> new EligibleZmDto(zm.getId(), zm.getFullName(), zm.getEmail()))
                .toList();
    }

    // ── STT: create return request ─────────────────────────────────────────────

    @Transactional
    public MaterialReturnResponseDto createReturn(CreateMaterialReturnDto request) {
        String callerEmail = currentUserEmail();
        AppUser stt = findByEmail(callerEmail);
        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an STT can create a material return request.");
        }
        log.info("[MaterialReturn] createReturn start sttId={} sttEmail={} zmSelection={} source={} reason={}",
                stt.getId(), stt.getEmail(), request.getResponsibleZmId(), request.getReturnSource(), request.getReason());

        if (request.getReason() == ReturnReason.OTHER
                && (request.getComment() == null || request.getComment().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Comment is required when return reason is OTHER");
        }

        ReturnSource returnSource = request.getReturnSource() != null
                ? request.getReturnSource() : ReturnSource.OTHER;
        List<ReturnedItemDto> requestedItems = request.getItems() != null ? request.getItems() : List.of();
        List<String> requestedCollectedIds = normalizeIds(request.getRetrievedStockItemIds());

        Optional<SttStock> sttStockOpt = sttStockRepository.findBySttId(stt.getId());
        Map<String, SttStockItem> stockIndex = sttStockOpt
                .map(this::buildStockIndex).orElse(new HashMap<>());

        if (returnSource == ReturnSource.COLLECTED_ITEM_STOCK) {
            if (!requestedItems.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Manual equipment lines are not allowed for Collected Item Stock returns.");
            }
            if (requestedCollectedIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select at least one collected item for this return.");
            }
        } else {
            if (requestedItems.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one item is required.");
            }
            if (!requestedCollectedIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Collected item ids can only be used when the return source is COLLECTED_ITEM_STOCK.");
            }
        }

        if (returnSource == ReturnSource.STT_STOCK) {
            if (sttStockOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No stock found for your account. Select a different return source.");
            }
            for (ReturnedItemDto itemDto : requestedItems) {
                SttStockItem si = stockIndex.get(itemDto.getEquipmentId());
                if (si == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Equipment not found in your stock: " + itemDto.getEquipmentId());
                }
                if (si.getAvailableQuantity() < itemDto.getQuantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Insufficient quantity for " + si.getEquipmentName()
                                    + ": available=" + si.getAvailableQuantity()
                                    + ", requested=" + itemDto.getQuantity());
                }
            }
        }

        AppUser zm = userRepository.findById(request.getResponsibleZmId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Selected Zone Manager not found."));
        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selected user is not a Zone Manager.");
        }
        if (!zm.isCanCreateDpm()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selected Zone Manager is not authorized to receive material returns.");
        }

        String resolvedZmId    = zm.getId();
        String resolvedZmName  = zm.getFullName();
        String resolvedZmEmail = zm.getEmail();

        String sourceDpmId = dpmRepository
                .findBySttIdAndStatusInOrderByCreatedAtDesc(stt.getId(), DELIVERED_DPM_STATUSES)
                .stream()
                .filter(d -> resolvedZmId.equals(d.getZmId()))
                .map(DpmRequest::getId)
                .findFirst().orElse(null);

        List<RetrievedStockItem> selectedCollectedItems = returnSource == ReturnSource.COLLECTED_ITEM_STOCK
                ? loadCollectedItemsForReturn(requestedCollectedIds, stt)
                : List.of();

        List<String> equipmentIds = returnSource == ReturnSource.COLLECTED_ITEM_STOCK
                ? selectedCollectedItems.stream().map(RetrievedStockItem::getEquipmentId).toList()
                : requestedItems.stream().map(ReturnedItemDto::getEquipmentId).toList();
        Map<String, Equipment> equipmentMap = new HashMap<>();
        equipmentRepository.findAllById(equipmentIds)
                .forEach(e -> equipmentMap.put(e.getId(), e));

        Map<String, EquipmentInstance> selectedStockInstances = returnSource == ReturnSource.STT_STOCK
                ? validateAndResolveTrackedStockItems(stt, requestedItems, stockIndex, equipmentMap)
                : Map.of();

        List<ReturnedItem> returnedItems = returnSource == ReturnSource.COLLECTED_ITEM_STOCK
                ? buildCollectedReturnItems(selectedCollectedItems)
                : buildManualReturnItems(requestedItems, request.getCondition(), stockIndex, equipmentMap, selectedStockInstances);

        String relatedCaseId = trimToNull(request.getRelatedCaseId());
        String relatedCaseType = trimToNull(request.getRelatedCaseType());
        String crmCase = trimToNull(request.getCrmCase());
        if (returnSource == ReturnSource.COLLECTED_ITEM_STOCK && selectedCollectedItems.size() == 1) {
            RetrievedStockItem single = selectedCollectedItems.get(0);
            if (relatedCaseId == null) {
                relatedCaseId = single.getCaseId();
            }
            if (relatedCaseType == null) {
                relatedCaseType = single.getCaseUsageContext();
            }
            if (crmCase == null) {
                crmCase = single.getCrmCase();
            }
        }

        Instant now = Instant.now();
        ReturnTimelineEvent submittedEvent = ReturnTimelineEvent.builder()
                .eventType("SUBMITTED")
                .description("Return request submitted by STT")
                .actor(stt.getEmail())
                .actorName(stt.getFullName())
                .timestamp(now)
                .build();

        MaterialReturnRequest returnRequest = MaterialReturnRequest.builder()
                .returnCode(generateReturnCode())
                .sttId(stt.getId())
                .sttName(stt.getFullName())
                .sttEmail(stt.getEmail())
                .zmId(resolvedZmId)
                .zmName(resolvedZmName)
                .zmEmail(resolvedZmEmail)
                .sourceDpmId(sourceDpmId)
                .relatedCaseId(relatedCaseId)
                .relatedCaseType(relatedCaseType)
                .crmCase(crmCase)
                .returnSource(returnSource)
                .reason(request.getReason())
                .condition(request.getCondition())
                .destination(request.getDestination())
                .items(returnedItems)
                .comment(request.getComment())
                .proofImageUrls(request.getProofImageUrls() != null ? request.getProofImageUrls() : List.of())
                .status(MaterialReturnStatus.PENDING_ZM_REVIEW)
                .createdAt(now)
                .submittedAt(now)
                .timeline(new java.util.ArrayList<>(List.of(submittedEvent)))
                .build();

        MaterialReturnRequest saved = returnRepository.save(returnRequest);
        try {
            if (returnSource == ReturnSource.COLLECTED_ITEM_STOCK) {
                reserveCollectedItemsForReturn(saved, selectedCollectedItems);
            }
            reserveTrackedUnitsForReturn(saved, callerEmail);
        } catch (RuntimeException ex) {
            rollbackNewReturn(saved, callerEmail);
            throw ex;
        }

        businessMetrics.recordMaterialReturnCreated();
        log.info("[MaterialReturn] Created: id={} returnCode={} sttId={} zmId={} status={}",
                saved.getId(), saved.getReturnCode(), saved.getSttId(), saved.getZmId(), saved.getStatus());

        runSideEffect("record submitted stock movement", () ->
                stockMovementService.recordMaterialReturn(saved, callerEmail));

        if (saved.getRelatedCaseId() != null && !saved.getRelatedCaseId().isBlank()) {
            runSideEffect("append submitted case timeline", () ->
                    appendCaseTimeline(saved, CaseTimelineEventType.MATERIAL_RETURN_SUBMITTED,
                            "Material Return Submitted",
                            "STT " + stt.getFullName() + " submitted return " + saved.getReturnCode() + ".",
                            callerEmail));
        }

        runSideEffect("notify ZM about submitted return", () ->
                notificationEventService.onMaterialReturnSubmitted(saved));
        return toDto(saved);
    }

    // ── RES: client material recovery → return ──────────────────────────────────

    /**
     * Creates a material return from equipment recovered off a client during a RES intervention.
     * Unlike {@link #createReturn} this does NOT touch any STT stock (the material comes from the
     * client, not the warehouse). The responsible STT and ZM are derived from the case so the
     * recovery flows into the normal ZM-scheduled handover workflow. Called by the case execution
     * flow, so the acting user may be a TECH/STT/ADMIN rather than the STT owner.
     */
    public MaterialReturnRequest createCaseRecoveryReturn(
            CaseEntity caseEntity,
            AppUser declarer,
            List<RecoveredEquipmentRequestItem> items,
            String comment) {

        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Declare at least one recovered item.");
        }

        AppUser stt = resolveCaseStt(caseEntity);
        AppUser zm = resolveCaseZm(caseEntity);

        List<String> equipmentIds = items.stream()
                .map(RecoveredEquipmentRequestItem::equipmentId).toList();
        Map<String, Equipment> equipmentMap = new HashMap<>();
        equipmentRepository.findAllById(equipmentIds).forEach(e -> equipmentMap.put(e.getId(), e));

        List<ReturnedItem> returnedItems = items.stream().map(item -> {
            Equipment eq = equipmentMap.get(item.equipmentId());
            String name = item.equipmentName() != null && !item.equipmentName().isBlank()
                    ? item.equipmentName()
                    : (eq != null ? eq.getName() : item.equipmentId());
            String unit = item.unit() != null && !item.unit().isBlank()
                    ? item.unit()
                    : (eq != null && eq.getUnit() != null ? eq.getUnit().name() : null);
            String articleCode = eq != null ? eq.getArticleCode() : null;
            return ReturnedItem.builder()
                    .equipmentId(item.equipmentId())
                    .equipmentName(name)
                    .articleCode(articleCode)
                    .unit(unit)
                    .quantity(item.quantity())
                    .serialNumber(item.serialNumber())
                    .macAddress(item.macAddress())
                    .imei(item.imei())
                    .condition(parseCondition(item.condition()))
                    .build();
        }).toList();

        // Overall condition = worst-of, but keep it simple: use the first item's condition or GOOD.
        ReturnCondition overallCondition = returnedItems.stream()
                .map(ReturnedItem::getCondition)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ReturnCondition.GOOD);

        Instant now = Instant.now();
        ReturnTimelineEvent submittedEvent = ReturnTimelineEvent.builder()
                .eventType("SUBMITTED")
                .description("Recovered client material declared during résiliation by "
                        + declarer.getFullName())
                .actor(declarer.getEmail())
                .actorName(declarer.getFullName())
                .timestamp(now)
                .build();

        MaterialReturnRequest returnRequest = MaterialReturnRequest.builder()
                .returnCode(generateReturnCode())
                .sttId(stt.getId())
                .sttName(stt.getFullName())
                .sttEmail(stt.getEmail())
                .zmId(zm.getId())
                .zmName(zm.getFullName())
                .zmEmail(zm.getEmail())
                .relatedCaseId(caseEntity.getId())
                .relatedCaseType(caseEntity.getType() != null ? caseEntity.getType().name() : null)
                .crmCase(caseEntity.getCrmCase())
                .returnSource(ReturnSource.RESILIATION_RECOVERY)
                .reason(ReturnReason.CLIENT_RESILIATION_RECOVERY)
                .condition(overallCondition)
                .destination(ReturnDestination.USABLE_STOCK)
                .items(returnedItems)
                .comment(comment)
                .proofImageUrls(List.of())
                .status(MaterialReturnStatus.PENDING_ZM_REVIEW)
                .createdAt(now)
                .submittedAt(now)
                .timeline(new ArrayList<>(List.of(submittedEvent)))
                .build();

        MaterialReturnRequest saved = returnRepository.save(returnRequest);
        log.info("[MaterialReturn] Client recovery return created: id={} returnCode={} caseId={} sttId={} zmId={}",
                saved.getId(), saved.getReturnCode(), caseEntity.getId(), saved.getSttId(), saved.getZmId());

        // ── Per-unit tracking: register recovered tracked units (RECOVERED_FROM_CLIENT) and
        // link them to this return for traceability. No-op for non-tracked equipment / blank ids.
        boolean linkedAnyInstance = false;
        for (ReturnedItem item : saved.getItems()) {
            Equipment eq = equipmentMap.get(item.getEquipmentId());
            if (eq == null || eq.getTrackingMode() == null
                    || eq.getTrackingMode() == com.pfe.stock_management.enums.TrackingMode.NONE) {
                continue;
            }
            String instanceId = equipmentInstanceService.recordClientRecovery(
                    eq, caseEntity, item.getImei(), item.getSerialNumber(), saved, declarer.getEmail());
            if (instanceId != null) {
                item.setInstanceId(instanceId);
                linkedAnyInstance = true;
            }
        }
        if (linkedAnyInstance) {
            // Persist the linked instanceIds; `saved` already holds them in memory and stays
            // effectively final for the side-effect lambdas below.
            returnRepository.save(saved);
        }

        runSideEffect("record recovery return movement", () ->
                stockMovementService.recordMaterialReturn(saved, declarer.getEmail()));
        runSideEffect("notify ZM about recovery return", () ->
                notificationEventService.onMaterialReturnSubmitted(saved));

        return saved;
    }

    private AppUser resolveCaseStt(CaseEntity caseEntity) {
        String sttId = caseEntity.getSttId() != null ? caseEntity.getSttId() : caseEntity.getAssignedSttId();
        if (sttId == null || sttId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This case has no assigned STT, so recovered material cannot be routed for return.");
        }
        return userRepository.findById(sttId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Assigned STT for this case was not found."));
    }

    private AppUser resolveCaseZm(CaseEntity caseEntity) {
        String zmId = caseEntity.getZmId() != null ? caseEntity.getZmId() : caseEntity.getOwnerZmId();
        if (zmId == null || zmId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This case has no owning Zone Manager, so recovered material cannot be routed for return.");
        }
        return userRepository.findById(zmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Owning Zone Manager for this case was not found."));
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

    // ── STT: list my returns ───────────────────────────────────────────────────

    public List<MaterialReturnResponseDto> getMyReturns() {
        AppUser stt = findByEmail(currentUserEmail());
        List<MaterialReturnRequest> returns = findReturnsForStt(stt);
        log.info("[MaterialReturn] STT my-returns userId={} userEmail={} query=sttId/sttEmail results={}",
                stt.getId(), stt.getEmail(), returns.size());
        return returns.stream().map(this::toDto).toList();
    }

    // ── STT: respond to a proposed handover schedule ───────────────────────────

    public MaterialReturnResponseDto respondToSchedule(String id, SttScheduleResponseDto body) {
        String callerEmail = currentUserEmail();
        AppUser stt = findByEmail(callerEmail);
        MaterialReturnRequest req = findReturnOrThrow(id);
        log.info("[MaterialReturn] scheduleResponse start id={} returnCode={} sttId={} sttEmail={} accepted={} currentStatus={}",
                req.getId(), req.getReturnCode(), stt.getId(), stt.getEmail(), body != null && body.isAccepted(), req.getStatus());

        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule response payload is required.");
        }
        if (!isSttOwner(req, stt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not the STT owner of this return.");
        }
        if (!STT_CONFIRMATION_STATUSES.contains(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No schedule has been proposed for this return (current status: " + req.getStatus() + ").");
        }

        Instant now = Instant.now();

        if (body.isAccepted()) {
            // Decrement STT stock for STT_STOCK source
            if (req.getReturnSource() == ReturnSource.STT_STOCK) {
                applySttStockDecrement(req);
            }

            req.setStatus(MaterialReturnStatus.SCHEDULE_CONFIRMED);
            req.setScheduleConfirmedAt(now);
            req.setScheduleRejectedAt(null);
            req.setSttScheduleResponseComment(body.getComment() != null ? body.getComment().trim() : null);

            req.getTimeline().add(ReturnTimelineEvent.builder()
                    .eventType("SCHEDULE_CONFIRMED")
                    .description("STT accepted the proposed handover schedule: "
                            + req.getProposedHandoverDate() + " at " + req.getProposedHandoverTime())
                    .actor(stt.getEmail())
                    .actorName(stt.getFullName())
                    .timestamp(now)
                    .note(body.getComment())
                    .build());

            MaterialReturnRequest saved = returnRepository.save(req);
            log.info("[MaterialReturn] scheduleResponse saved confirmation id={} status={} confirmedAt={}",
                    saved.getId(), saved.getStatus(), saved.getScheduleConfirmedAt());
            runSideEffect("record approved stock movement", () ->
                    stockMovementService.recordMaterialReturnApproved(saved, stt.getEmail()));

            if (saved.getRelatedCaseId() != null && !saved.getRelatedCaseId().isBlank()) {
                runSideEffect("append confirmed case timeline", () ->
                        appendCaseTimeline(saved, CaseTimelineEventType.MATERIAL_RETURN_APPROVED,
                                "Handover Schedule Confirmed",
                                "STT " + stt.getFullName() + " confirmed handover on "
                                        + saved.getProposedHandoverDate() + " at " + saved.getProposedHandoverTime() + ".",
                                callerEmail));
            }

            runSideEffect("notify ZM about confirmed schedule", () ->
                    notificationEventService.onScheduleAcceptedByStt(saved));
            log.info("[MaterialReturn] Schedule confirmed: id={} sttId={}", saved.getId(), stt.getId());
            return toDto(saved);

        } else {
            String rescheduleComment = (body.getComment() != null) ? body.getComment().trim() : "";
            if (rescheduleComment.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A comment is required when requesting a reschedule.");
            }

            req.setStatus(MaterialReturnStatus.RESCHEDULE_REQUESTED);
            req.setScheduleRejectedAt(now);
            req.setSttScheduleResponseComment(rescheduleComment);

            req.getTimeline().add(ReturnTimelineEvent.builder()
                    .eventType("RESCHEDULE_REQUESTED")
                    .description("STT requested a new handover schedule")
                    .actor(stt.getEmail())
                    .actorName(stt.getFullName())
                    .timestamp(now)
                    .note(rescheduleComment)
                    .build());

            MaterialReturnRequest saved = returnRepository.save(req);
            log.info("[MaterialReturn] scheduleResponse saved reschedule request id={} status={} rejectedAt={}",
                    saved.getId(), saved.getStatus(), saved.getScheduleRejectedAt());
            runSideEffect("notify ZM about reschedule request", () ->
                    notificationEventService.onScheduleRejectedByStt(saved));
            log.info("[MaterialReturn] Reschedule requested: id={} sttId={}", saved.getId(), stt.getId());
            return toDto(saved);
        }
    }

    // ── ZM: list returns needing ZM action ─────────────────────────────────────

    public List<MaterialReturnResponseDto> getPendingForZm() {
        AppUser zm = findByEmail(currentUserEmail());
        List<MaterialReturnRequest> pendingReturns = filterByStatuses(findReturnsForZm(zm), ZM_PENDING_STATUSES);
        log.info("[MaterialReturn] ZM pending-returns userId={} userEmail={} query=zmId/zmEmail statuses={} results={}",
                zm.getId(), zm.getEmail(), ZM_PENDING_STATUSES, pendingReturns.size());
        return pendingReturns.stream().map(this::toDto).toList();
    }

    // ── ZM: list all returns in scope ──────────────────────────────────────────

    public List<MaterialReturnResponseDto> getAllForZm() {
        AppUser zm = findByEmail(currentUserEmail());
        List<MaterialReturnRequest> returns = findReturnsForZm(zm);
        log.info("[MaterialReturn] ZM my-returns userId={} userEmail={} query=zmId/zmEmail results={}",
                zm.getId(), zm.getEmail(), returns.size());
        return returns.stream().map(this::toDto).toList();
    }

    // ── ZM: propose a handover schedule ───────────────────────────────────────

    public MaterialReturnResponseDto proposeHandover(String id, ProposeHandoverDto body) {
        String callerEmail = currentUserEmail();
        AppUser zm = findByEmail(callerEmail);
        MaterialReturnRequest req = findReturnOrThrow(id);
        log.info("[MaterialReturn] proposeHandover start id={} returnCode={} zmId={} zmEmail={} currentStatus={} targetDate={} targetTime={}",
                req.getId(), req.getReturnCode(), zm.getId(), zm.getEmail(), req.getStatus(), body.getHandoverDate(), body.getHandoverTime());

        assertZmScope(req, zm, "propose a handover schedule for");

        if (!ZM_PENDING_STATUSES.contains(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot propose a schedule for a return in status: " + req.getStatus());
        }
        if (body.getHandoverDate() == null || body.getHandoverDate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Handover date is required.");
        }
        if (body.getHandoverTime() == null || body.getHandoverTime().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Handover time is required.");
        }

        Instant now = Instant.now();
        req.setProposedHandoverDate(body.getHandoverDate());
        req.setProposedHandoverTime(body.getHandoverTime());
        req.setProposedHandoverNote(body.getHandoverNote());
        req.setScheduleProposedAt(now);
        req.setStatus(MaterialReturnStatus.SCHEDULE_PROPOSED);

        // Clear previous STT reschedule comment when ZM re-proposes
        req.setSttScheduleResponseComment(null);
        req.setScheduleConfirmedAt(null);
        req.setScheduleRejectedAt(null);

        req.getTimeline().add(ReturnTimelineEvent.builder()
                .eventType("SCHEDULE_PROPOSED")
                .description("ZM proposed handover: " + body.getHandoverDate() + " at " + body.getHandoverTime())
                .actor(zm.getEmail())
                .actorName(zm.getFullName())
                .timestamp(now)
                .note(body.getHandoverNote())
                .build());

        MaterialReturnRequest saved = returnRepository.save(req);
        runSideEffect("notify STT about proposed schedule", () ->
                notificationEventService.onHandoverScheduleProposed(saved));
        log.info("[MaterialReturn] Schedule proposed: id={} zmId={} date={} time={}",
                saved.getId(), zm.getId(), body.getHandoverDate(), body.getHandoverTime());
        return toDto(saved);
    }

    // ── ZM: reject the return request itself ──────────────────────────────────

    @Transactional
    public MaterialReturnResponseDto rejectReturn(String id, ReviewMaterialReturnDto body) {
        String callerEmail = currentUserEmail();
        AppUser zm = findByEmail(callerEmail);
        MaterialReturnRequest req = findReturnOrThrow(id);
        log.info("[MaterialReturn] rejectReturn start id={} returnCode={} zmId={} zmEmail={} currentStatus={}",
                req.getId(), req.getReturnCode(), zm.getId(), zm.getEmail(), req.getStatus());

        assertZmScope(req, zm, "reject");

        if (!ZM_PENDING_STATUSES.contains(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot reject a return in status: " + req.getStatus());
        }

        String rejectionComment = (body != null && body.getReviewComment() != null)
                ? body.getReviewComment().trim() : "";
        if (rejectionComment.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rejection comment is required.");
        }

        Instant now = Instant.now();
        req.setStatus(MaterialReturnStatus.REJECTED);
        req.setReviewedAt(now);
        req.setReviewedByZmId(zm.getId());
        req.setReviewedByZmName(zm.getFullName());
        req.setReviewComment(rejectionComment);

        req.getTimeline().add(ReturnTimelineEvent.builder()
                .eventType("REJECTED")
                .description("ZM rejected the return request")
                .actor(zm.getEmail())
                .actorName(zm.getFullName())
                .timestamp(now)
                .note(rejectionComment)
                .build());

        MaterialReturnRequest saved = returnRepository.save(req);
        if (saved.getReturnSource() == ReturnSource.COLLECTED_ITEM_STOCK) {
            releaseCollectedItems(saved, zm.getEmail());
        }
        runSideEffect("release tracked-unit reservation", () ->
                equipmentInstanceService.releaseReturnReservation(saved.getId(), zm.getEmail()));
        runSideEffect("record rejected stock movement", () ->
                stockMovementService.recordMaterialReturnRejected(saved, zm.getEmail()));

        if (saved.getRelatedCaseId() != null && !saved.getRelatedCaseId().isBlank()) {
            runSideEffect("append rejected case timeline", () ->
                    appendCaseTimeline(saved, CaseTimelineEventType.MATERIAL_RETURN_REJECTED,
                            "Material Return Rejected",
                            "ZM " + zm.getFullName() + " rejected return " + saved.getReturnCode()
                                    + ". Reason: " + rejectionComment,
                            callerEmail));
        }

        runSideEffect("notify STT about rejected return", () ->
                notificationEventService.onMaterialReturnRejected(saved));
        log.info("[MaterialReturn] Rejected: id={} zmId={}", saved.getId(), zm.getId());
        return toDto(saved);
    }

    // ── Shared: get by id ──────────────────────────────────────────────────────

    public MaterialReturnResponseDto getById(String id) {
        MaterialReturnRequest req = findReturnOrThrow(id);
        AppUser caller = findByEmail(currentUserEmail());
        assertReadAccess(req, caller);
        return toDto(req);
    }

    // ── Case-linked returns ────────────────────────────────────────────────────

    public List<MaterialReturnResponseDto> getReturnsByCase(String caseId) {
        return returnRepository.findByRelatedCaseIdOrderByCreatedAtDesc(caseId)
                .stream().map(this::toDto).toList();
    }

    // ── ZM: mark physical handover as complete ─────────────────────────────────

    @Transactional
    public MaterialReturnResponseDto markHandedOver(String id, MarkHandedOverDto body) {
        String callerEmail = currentUserEmail();
        AppUser zm = findByEmail(callerEmail);
        MaterialReturnRequest req = findReturnOrThrow(id);
        log.info("[MaterialReturn] markHandedOver start id={} returnCode={} zmId={} zmEmail={} currentStatus={}",
                req.getId(), req.getReturnCode(), zm.getId(), zm.getEmail(), req.getStatus());

        assertZmScope(req, zm, "mark as handed over");

        if (!HANDOVER_CONFIRMED_STATUSES.contains(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot mark as handed over for a return in status: " + req.getStatus());
        }

        Instant now = Instant.now();
        req.setStatus(MaterialReturnStatus.HANDED_OVER);
        req.setHandedOverAt(now);
        if (body != null && body.getNote() != null && !body.getNote().isBlank()) {
            req.setHandedOverNote(body.getNote().trim());
        }

        req.getTimeline().add(ReturnTimelineEvent.builder()
                .eventType("HANDED_OVER")
                .description("Material physically received by ZM — handover complete")
                .actor(zm.getEmail())
                .actorName(zm.getFullName())
                .timestamp(now)
                .note(body != null ? body.getNote() : null)
                .build());

        MaterialReturnRequest saved = returnRepository.save(req);
        if (saved.getReturnSource() == ReturnSource.COLLECTED_ITEM_STOCK) {
            markCollectedItemsReturned(saved);
        }

        // Per-unit tracking: tracked units linked to this return are now physically back at
        // the ZM/depot → status RETURNED_TO_DEPOT.
        runSideEffect("mark returned tracked units to depot", () ->
                equipmentInstanceService.markReturnedToDepot(saved.getId(), zm.getEmail()));

        runSideEffect("notify STT about handed-over return", () ->
                notificationEventService.onMaterialHandedOver(saved));
        runSideEffect("notify ZM about return closure", () ->
                notificationEventService.onReturnClosedForZm(saved));
        log.info("[MaterialReturn] Handed over: id={} zmId={}", saved.getId(), zm.getId());
        return toDto(saved);
    }

    // ── ADMIN: all returns ─────────────────────────────────────────────────────

    public List<MaterialReturnResponseDto> getAllReturns() {
        return returnRepository.findAll().stream().map(this::toDto).toList();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<RetrievedStockItem> loadCollectedItemsForReturn(List<String> retrievedStockItemIds, AppUser stt) {
        List<RetrievedStockItem> items = retrievedStockItemRepository.findAllById(retrievedStockItemIds);
        if (items.size() != retrievedStockItemIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "One or more selected collected items were not found.");
        }

        Map<String, RetrievedStockItem> itemsById = new LinkedHashMap<>();
        items.forEach(item -> itemsById.put(item.getId(), item));

        List<RetrievedStockItem> orderedItems = new ArrayList<>();
        for (String id : retrievedStockItemIds) {
            RetrievedStockItem item = itemsById.get(id);
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Collected item " + id + " was not found.");
            }
            if (!Objects.equals(item.getSttId(), stt.getId())
                    || !Objects.equals(item.getCollectedBySttId(), stt.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Collected item " + id + " does not belong to your STT collected stock.");
            }
            if (item.getStatus() != RetrievedStockItemStatus.COLLECTED_BY_STT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Collected item " + id + " is not selectable for a return.");
            }
            if (item.getMaterialReturnRequestId() != null && !item.getMaterialReturnRequestId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Collected item " + id + " is already linked to another active return request.");
            }
            orderedItems.add(item);
        }
        return orderedItems;
    }

    private Map<String, EquipmentInstance> validateAndResolveTrackedStockItems(AppUser stt,
                                                                               List<ReturnedItemDto> requestedItems,
                                                                               Map<String, SttStockItem> stockIndex,
                                                                               Map<String, Equipment> equipmentMap) {
        Map<String, EquipmentInstance> resolved = new HashMap<>();
        for (ReturnedItemDto item : requestedItems) {
            Equipment equipment = equipmentMap.get(item.getEquipmentId());
            TrackingMode trackingMode = equipment != null && equipment.getTrackingMode() != null
                    ? equipment.getTrackingMode()
                    : TrackingMode.NONE;
            if (trackingMode == TrackingMode.NONE) {
                continue;
            }

            if (item.getQuantity() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tracked equipment must be returned one physical unit at a time.");
            }

            String instanceId = trimToNull(item.getInstanceId());
            if (instanceId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select the exact serial number or IMEI for tracked equipment.");
            }
            if (resolved.containsKey(instanceId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The same tracked item cannot be selected more than once in a return.");
            }
            if (!returnRepository.findByTrackedInstanceIdAndStatusIn(instanceId, ACTIVE_RETURN_STATUSES).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The selected tracked item is already linked to another active return request.");
            }

            List<EquipmentInstance> matched = equipmentInstanceService.assertDistributable(
                    item.getEquipmentId(), List.of(instanceId), stt.getId());
            EquipmentInstance selectedInstance = matched.stream()
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Tracked stock item " + instanceId + " is not available in your STT stock."));

            assertTrackedIdentifierMatches(item, selectedInstance,
                    equipment != null ? equipment.getName() : item.getEquipmentId());

            SttStockItem stockItem = stockIndex.get(item.getEquipmentId());
            if (stockItem == null || stockItem.getAvailableQuantity() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tracked stock item is no longer available in your STT stock.");
            }

            resolved.put(instanceId, selectedInstance);
        }
        return resolved;
    }

    private void assertTrackedIdentifierMatches(ReturnedItemDto item,
                                                EquipmentInstance selectedInstance,
                                                String equipmentName) {
        String requestedSerial = trimToNull(item.getSerialNumber());
        if (requestedSerial != null && !requestedSerial.equalsIgnoreCase(trimToNull(selectedInstance.getSerialNumber()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selected serial number does not match the tracked stock unit for " + equipmentName + ".");
        }

        String requestedImei = trimToNull(item.getImei());
        if (requestedImei != null && !requestedImei.equalsIgnoreCase(trimToNull(selectedInstance.getImei()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selected IMEI does not match the tracked stock unit for " + equipmentName + ".");
        }
    }

    private List<ReturnedItem> buildManualReturnItems(List<ReturnedItemDto> requestedItems,
                                                      ReturnCondition fallbackCondition,
                                                      Map<String, SttStockItem> stockIndex,
                                                      Map<String, Equipment> equipmentMap,
                                                      Map<String, EquipmentInstance> selectedStockInstances) {
        List<ReturnedItem> items = new ArrayList<>();
        for (ReturnedItemDto dto : requestedItems) {
            Equipment equipment = equipmentMap.get(dto.getEquipmentId());
            SttStockItem stockItem = stockIndex.get(dto.getEquipmentId());
            EquipmentInstance instance = selectedStockInstances.get(trimToNull(dto.getInstanceId()));

            String unit = equipment != null && equipment.getUnit() != null
                    ? equipment.getUnit().name()
                    : (stockItem != null ? stockItem.getUnit() : null);

            ReturnedItem item = ReturnedItem.builder()
                    .equipmentId(dto.getEquipmentId())
                    .equipmentName(stockItem != null ? stockItem.getEquipmentName()
                            : equipment != null ? equipment.getName() : dto.getEquipmentId())
                    .articleCode(equipment != null ? equipment.getArticleCode() : null)
                    .unit(unit)
                    .quantity(dto.getQuantity())
                    .condition(dto.getCondition() != null ? dto.getCondition() : fallbackCondition)
                    .serialNumber(instance != null ? trimToNull(instance.getSerialNumber()) : trimToNull(dto.getSerialNumber()))
                    .imei(instance != null ? trimToNull(instance.getImei()) : trimToNull(dto.getImei()))
                    .macAddress(trimToNull(dto.getMacAddress()))
                    .instanceId(instance != null ? instance.getId() : trimToNull(dto.getInstanceId()))
                    .build();
            items.add(item);
        }
        return items;
    }

    private List<ReturnedItem> buildCollectedReturnItems(List<RetrievedStockItem> selectedCollectedItems) {
        return selectedCollectedItems.stream()
                .map(item -> ReturnedItem.builder()
                        .equipmentId(item.getEquipmentId())
                        .equipmentName(item.getEquipmentName())
                        .articleCode(item.getArticleCode())
                        .unit(item.getUnit())
                        .quantity(item.getQuantity())
                        .serialNumber(trimToNull(item.getSerialNumber()))
                        .imei(trimToNull(item.getImei()))
                        .macAddress(trimToNull(item.getMacAddress()))
                        .condition(item.getCondition())
                        .instanceId(trimToNull(item.getInstanceId()))
                        .retrievedStockItemId(item.getId())
                        .originalTechnicianId(item.getTechnicianId())
                        .originalTechnicianName(item.getTechnicianName())
                        .originalTechnicianEmail(item.getTechnicianEmail())
                        .originalCaseId(item.getCaseId())
                        .originalCaseReference(item.getCrmCase())
                        .originalCaseType(item.getCaseUsageContext())
                        .originalCustomerName(item.getClientName())
                        .originalCustomerPhone(item.getClientPhone())
                        .originalRecoveryDate(item.getRetrievedAt())
                        .sttCollectedAt(item.getCollectedAt())
                        .proofFileId(item.getProofFileId())
                        .proofViewUrl(item.getProofFileId() != null ? "/api/v1/files/" + item.getProofFileId() + "/view" : null)
                        .proofDownloadUrl(item.getProofFileId() != null ? "/api/v1/files/" + item.getProofFileId() + "/download" : null)
                        .build())
                .toList();
    }

    private void reserveCollectedItemsForReturn(MaterialReturnRequest request,
                                                List<RetrievedStockItem> selectedCollectedItems) {
        Instant now = Instant.now();
        for (RetrievedStockItem item : selectedCollectedItems) {
            item.setStatus(RetrievedStockItemStatus.IN_RETURN_REQUEST);
            item.setMaterialReturnRequestId(request.getId());
            item.setAddedToReturnAt(now);
            item.setUpdatedAt(now);
        }
        retrievedStockItemRepository.saveAll(selectedCollectedItems);
    }

    private void releaseCollectedItems(MaterialReturnRequest request, String actor) {
        List<RetrievedStockItem> items = retrievedStockItemRepository.findByMaterialReturnRequestId(request.getId());
        if (items.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (RetrievedStockItem item : items) {
            item.setStatus(RetrievedStockItemStatus.COLLECTED_BY_STT);
            item.setMaterialReturnRequestId(null);
            item.setAddedToReturnAt(null);
            item.setUpdatedAt(now);
        }
        retrievedStockItemRepository.saveAll(items);
        log.info("[MaterialReturn] released {} collected items for return {} by {}",
                items.size(), request.getReturnCode(), actor);
    }

    private void markCollectedItemsReturned(MaterialReturnRequest request) {
        List<RetrievedStockItem> items = retrievedStockItemRepository.findByMaterialReturnRequestId(request.getId());
        if (items.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (RetrievedStockItem item : items) {
            item.setStatus(RetrievedStockItemStatus.RETURNED);
            item.setReturnedAt(now);
            item.setUpdatedAt(now);
        }
        retrievedStockItemRepository.saveAll(items);
    }

    private void reserveTrackedUnitsForReturn(MaterialReturnRequest request, String actor) {
        List<String> instanceIds = request.getItems().stream()
                .map(ReturnedItem::getInstanceId)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (instanceIds.isEmpty()) {
            return;
        }
        equipmentInstanceService.reserveForReturn(request.getId(), request.getReturnCode(), instanceIds, actor);
    }

    private void rollbackNewReturn(MaterialReturnRequest request, String actor) {
        if (request == null || request.getId() == null) {
            return;
        }
        try {
            releaseCollectedItems(request, actor);
        } catch (RuntimeException ex) {
            log.warn("[MaterialReturn] failed to release collected items while rolling back return {}",
                    request.getId(), ex);
        }
        try {
            equipmentInstanceService.releaseReturnReservation(request.getId(), actor);
        } catch (RuntimeException ex) {
            log.warn("[MaterialReturn] failed to release tracked units while rolling back return {}",
                    request.getId(), ex);
        }
        try {
            returnRepository.deleteById(request.getId());
        } catch (RuntimeException ex) {
            log.warn("[MaterialReturn] failed to delete partially-created return {}", request.getId(), ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void applySttStockDecrement(MaterialReturnRequest req) {
        SttStock stock = sttStockRepository.findBySttId(req.getSttId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "STT stock not found for sttId=" + req.getSttId()));
        Map<String, SttStockItem> index = buildStockIndex(stock);
        for (ReturnedItem item : req.getItems()) {
            SttStockItem si = index.get(item.getEquipmentId());
            if (si == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Equipment " + item.getEquipmentId() + " no longer in STT stock.");
            }
            if (si.getAvailableQuantity() < item.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "STT stock for " + si.getEquipmentName()
                                + " is insufficient. Available=" + si.getAvailableQuantity()
                                + ", return qty=" + item.getQuantity());
            }
            si.setTotalReturned(si.getTotalReturned() + item.getQuantity());
            si.setLastReturnedAt(Instant.now());
        }
        stock.setLastUpdatedAt(Instant.now());
        sttStockRepository.save(stock);
    }

    private void appendCaseTimeline(MaterialReturnRequest req,
                                    CaseTimelineEventType eventType,
                                    String title, String details, String actor) {
        Optional<CaseEntity> caseOpt = caseRepository.findById(req.getRelatedCaseId());
        if (caseOpt.isEmpty()) return;
        CaseEntity caseEntity = caseOpt.get();
        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("status", req.getStatus() != null ? req.getStatus().name() : null);
        caseTimelineService.append(caseEntity, eventType, title, details, actor, null, null, data);
        caseRepository.save(caseEntity);
    }

    private String generateReturnCode() {
        int year = Year.now().getValue();
        long count = returnRepository.count();
        String candidate;
        int attempts = 0;
        do {
            candidate = String.format("RET-%d-%04d", year, count + 1 + attempts);
            attempts++;
        } while (returnRepository.findByReturnCode(candidate).isPresent() && attempts < 100);
        return candidate;
    }

    private Map<String, SttStockItem> buildStockIndex(SttStock stock) {
        Map<String, SttStockItem> index = new HashMap<>();
        if (stock.getItems() != null) stock.getItems().forEach(si -> index.put(si.getEquipmentId(), si));
        return index;
    }

    private void runSideEffect(String label, Runnable action) {
        try {
            action.run();
            log.info("[MaterialReturn] side-effect ok: {}", label);
        } catch (Exception ex) {
            log.error("[MaterialReturn] side-effect failed: {}", label, ex);
        }
    }

    private void assertZmScope(MaterialReturnRequest req, AppUser zm, String action) {
        if (!isZmOwner(req, zm)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not authorized to " + action + " this return.");
        }
    }

    private void assertReadAccess(MaterialReturnRequest req, AppUser caller) {
        String role = caller.getRole() != null ? caller.getRole().name() : "";
        boolean ok = role.equals("ROLE_ADMIN")
                || (role.equals("ROLE_STT") && isSttOwner(req, caller))
                || (role.equals("ROLE_ZM")  && isZmOwner(req, caller));
        if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You are not authorized to view this return.");
    }

    private MaterialReturnRequest findReturnOrThrow(String id) {
        return returnRepository.findById(id)
                .or(() -> returnRepository.findByReturnCode(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Material return not found: " + id));
    }

    private AppUser findByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private String currentUserEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? normalizeEmail(auth.getName()) : "system";
    }

    private MaterialReturnResponseDto toDto(MaterialReturnRequest r) {
        return MaterialReturnResponseDto.builder()
                .id(r.getId())
                .returnCode(r.getReturnCode())
                .sttId(r.getSttId())
                .sttName(r.getSttName())
                .sttEmail(r.getSttEmail())
                .zmId(r.getZmId())
                .zmName(r.getZmName())
                .zmEmail(r.getZmEmail())
                .sourceDpmId(r.getSourceDpmId())
                .relatedCaseId(r.getRelatedCaseId())
                .relatedCaseType(r.getRelatedCaseType())
                .crmCase(r.getCrmCase())
                .returnSource(r.getReturnSource())
                .reason(r.getReason())
                .condition(r.getCondition())
                .destination(r.getDestination())
                .items(r.getItems() != null ? r.getItems() : List.of())
                .comment(r.getComment())
                .proofImageUrls(r.getProofImageUrls() != null ? r.getProofImageUrls() : List.of())
                .status(r.getStatus())
                .proposedHandoverDate(r.getProposedHandoverDate())
                .proposedHandoverTime(r.getProposedHandoverTime())
                .proposedHandoverNote(r.getProposedHandoverNote())
                .scheduleProposedAt(r.getScheduleProposedAt())
                .sttScheduleResponseComment(r.getSttScheduleResponseComment())
                .scheduleConfirmedAt(r.getScheduleConfirmedAt())
                .scheduleRejectedAt(r.getScheduleRejectedAt())
                .reviewComment(r.getReviewComment())
                .reviewedByZmId(r.getReviewedByZmId())
                .reviewedByZmName(r.getReviewedByZmName())
                .reviewedAt(r.getReviewedAt())
                .handedOverAt(r.getHandedOverAt())
                .handedOverNote(r.getHandedOverNote())
                .createdAt(r.getCreatedAt())
                .submittedAt(r.getSubmittedAt())
                .timeline(r.getTimeline() != null ? r.getTimeline() : List.of())
                .build();
    }

    private List<MaterialReturnRequest> findReturnsForStt(AppUser stt) {
        String normalizedEmail = normalizeEmail(stt.getEmail());
        List<MaterialReturnRequest> byId = returnRepository.findBySttIdOrderByCreatedAtDesc(stt.getId());
        List<MaterialReturnRequest> byEmail = returnRepository.findBySttEmailOrderByCreatedAtDesc(normalizedEmail);
        log.info("[MaterialReturn] STT lookup details userId={} userEmail={} byId={} byEmail={}",
                stt.getId(), normalizedEmail, byId.size(), byEmail.size());
        return mergeAndSort(byId, byEmail);
    }

    private List<MaterialReturnRequest> findReturnsForZm(AppUser zm) {
        String normalizedEmail = normalizeEmail(zm.getEmail());
        List<MaterialReturnRequest> byId = returnRepository.findByZmIdOrderByCreatedAtDesc(zm.getId());
        List<MaterialReturnRequest> byEmail = returnRepository.findByZmEmailOrderByCreatedAtDesc(normalizedEmail);
        log.info("[MaterialReturn] ZM lookup details userId={} userEmail={} byId={} byEmail={}",
                zm.getId(), normalizedEmail, byId.size(), byEmail.size());
        return mergeAndSort(byId, byEmail);
    }

    private List<MaterialReturnRequest> filterByStatuses(List<MaterialReturnRequest> returns,
                                                         Collection<MaterialReturnStatus> statuses) {
        return returns.stream()
                .filter(request -> request.getStatus() != null && statuses.contains(request.getStatus()))
                .toList();
    }

    @SafeVarargs
    private final List<MaterialReturnRequest> mergeAndSort(List<MaterialReturnRequest>... sources) {
        Map<String, MaterialReturnRequest> unique = new LinkedHashMap<>();
        for (List<MaterialReturnRequest> source : sources) {
            if (source == null) continue;
            for (MaterialReturnRequest request : source) {
                if (request == null) continue;
                unique.putIfAbsent(uniqueKey(request), request);
            }
        }

        List<MaterialReturnRequest> merged = new ArrayList<>(unique.values());
        merged.sort((left, right) -> sortTimestamp(right).compareTo(sortTimestamp(left)));
        return merged;
    }

    private String uniqueKey(MaterialReturnRequest request) {
        if (request.getId() != null && !request.getId().isBlank()) {
            return request.getId();
        }
        if (request.getReturnCode() != null && !request.getReturnCode().isBlank()) {
            return request.getReturnCode();
        }
        return String.join("|",
                Objects.toString(request.getSttEmail(), ""),
                Objects.toString(request.getZmEmail(), ""),
                Objects.toString(request.getCreatedAt(), ""));
    }

    private Instant sortTimestamp(MaterialReturnRequest request) {
        if (request.getCreatedAt() != null) {
            return request.getCreatedAt();
        }
        if (request.getSubmittedAt() != null) {
            return request.getSubmittedAt();
        }
        return Instant.EPOCH;
    }

    private boolean isSttOwner(MaterialReturnRequest request, AppUser stt) {
        return matchesUserByIdOrEmail(request.getSttId(), request.getSttEmail(), stt);
    }

    private boolean isZmOwner(MaterialReturnRequest request, AppUser zm) {
        return matchesUserByIdOrEmail(request.getZmId(), request.getZmEmail(), zm);
    }

    private boolean matchesUserByIdOrEmail(String storedId, String storedEmail, AppUser user) {
        return Objects.equals(user.getId(), storedId)
                || sameEmail(user.getEmail(), storedEmail)
                || sameEmail(user.getEmail(), storedId);
    }

    private boolean sameEmail(String left, String right) {
        String normalizedLeft = normalizeEmail(left);
        String normalizedRight = normalizeEmail(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return false;
        }
        return normalizedLeft.equals(normalizedRight);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

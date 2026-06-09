package com.pfe.stock_management.material_return;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseTimelineService;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
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

    private final MaterialReturnRepository returnRepository;
    private final SttStockRepository sttStockRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final DpmRepository dpmRepository;
    private final CaseRepository caseRepository;
    private final CaseTimelineService caseTimelineService;
    private final StockMovementService stockMovementService;
    private final NotificationEventService notificationEventService;

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

    public MaterialReturnResponseDto createReturn(CreateMaterialReturnDto request) {
        String callerEmail = currentUserEmail();
        AppUser stt = findByEmail(callerEmail);
        log.info("[MaterialReturn] createReturn start sttId={} sttEmail={} zmSelection={} source={} reason={}",
                stt.getId(), stt.getEmail(), request.getResponsibleZmId(), request.getReturnSource(), request.getReason());

        if (request.getReason() == ReturnReason.OTHER
                && (request.getComment() == null || request.getComment().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Comment is required when return reason is OTHER");
        }

        ReturnSource returnSource = request.getReturnSource() != null
                ? request.getReturnSource() : ReturnSource.OTHER;

        Optional<SttStock> sttStockOpt = sttStockRepository.findBySttId(stt.getId());
        Map<String, SttStockItem> stockIndex = sttStockOpt
                .map(this::buildStockIndex).orElse(new HashMap<>());

        if (returnSource == ReturnSource.STT_STOCK) {
            if (sttStockOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No stock found for your account. Select a different return source.");
            }
            for (ReturnedItemDto itemDto : request.getItems()) {
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

        List<String> equipmentIds = request.getItems().stream()
                .map(ReturnedItemDto::getEquipmentId).toList();
        Map<String, Equipment> equipmentMap = new HashMap<>();
        equipmentRepository.findAllById(equipmentIds)
                .forEach(e -> equipmentMap.put(e.getId(), e));

        List<ReturnedItem> returnedItems = request.getItems().stream().map(dto -> {
            SttStockItem si = stockIndex.get(dto.getEquipmentId());
            Equipment    eq = equipmentMap.get(dto.getEquipmentId());
            String name        = si != null ? si.getEquipmentName()
                               : (eq != null ? eq.getName() : dto.getEquipmentId());
            String unit        = si != null ? si.getUnit()
                               : (eq != null && eq.getUnit() != null ? eq.getUnit().name() : null);
            String articleCode = eq != null ? eq.getArticleCode() : null;
            return ReturnedItem.builder()
                    .equipmentId(dto.getEquipmentId())
                    .equipmentName(name)
                    .articleCode(articleCode)
                    .unit(unit)
                    .quantity(dto.getQuantity())
                    .serialNumber(dto.getSerialNumber())
                    .macAddress(dto.getMacAddress())
                    .imei(dto.getImei())
                    .condition(dto.getCondition() != null ? dto.getCondition() : request.getCondition())
                    .build();
        }).toList();

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
                .relatedCaseId(request.getRelatedCaseId())
                .relatedCaseType(request.getRelatedCaseType())
                .crmCase(request.getCrmCase())
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
        runSideEffect("notify STT about handed-over return", () ->
                notificationEventService.onMaterialHandedOver(saved));
        log.info("[MaterialReturn] Handed over: id={} zmId={}", saved.getId(), zm.getId());
        return toDto(saved);
    }

    // ── ADMIN: all returns ─────────────────────────────────────────────────────

    public List<MaterialReturnResponseDto> getAllReturns() {
        return returnRepository.findAll().stream().map(this::toDto).toList();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

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

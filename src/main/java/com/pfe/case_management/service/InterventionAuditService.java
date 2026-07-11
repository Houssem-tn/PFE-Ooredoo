package com.pfe.case_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.case_management.dto.ConsumedMaterialDto;
import com.pfe.case_management.dto.InterventionAuditDto;
import com.pfe.case_management.dto.InterventionAuditEventDto;
import com.pfe.case_management.dto.InterventionAuditPageDto;
import com.pfe.case_management.dto.InterventionAuditSummaryDto;
import com.pfe.case_management.dto.InterventionMaterialReturnRefDto;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.CaseTimelineEvent;
import com.pfe.case_management.model.SlaTrackingInfo;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.stock_management.entity.CaseStockConsumption;
import com.pfe.stock_management.dto.MaterialQuantityBreakdownDto;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.material_return.MaterialReturnRepository;
import com.pfe.stock_management.material_return.MaterialReturnRequest;
import com.pfe.stock_management.model.ConsumedEquipmentItem;
import com.pfe.stock_management.repository.CaseStockConsumptionRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.util.MaterialQuantityGrouping;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Case-centric operational audit for the Admin "Interventions Auditing" cockpit.
 *
 * <p>Reads the existing case lifecycle (already recorded automatically via
 * {@code CaseTimelineService}, SLA tracking, work/proof capture and per-case stock
 * consumption) and projects each intervention into a rich {@link InterventionAuditDto}.
 * Purely read-only — it never mutates a case, its proof, or its stock.</p>
 */
@Service
@RequiredArgsConstructor
public class InterventionAuditService {

    private static final String CASES_COLLECTION = "cases";
    private static final String CONSUMPTION_COLLECTION = "case_stock_consumptions";

    /** Lifecycle event types surfaced in the intervention timeline (incl. material consumption). */
    private static final List<String> TIMELINE_EVENT_TYPES = List.of(
            "CASE_CREATED", "ASSIGNMENT_UPDATED", "STATUS_CHANGED", "RDV_UPDATED",
            "DIAGNOSIS_UPDATED", "WORK_UPDATED", "STOCK_CONSUMED", "ZM_VALIDATION",
            "CASE_REOPENED", "CASE_CLOSED", "SLA_WARNING", "SLA_BREACHED", "SLA_ESCALATED"
    );

    private static final List<String> COMPLETED_STATUSES = List.of(
            "WORK_DONE", "RESOLUTION_SUBMITTED", "VALIDATED_BY_ZM", "CLOSED");

    private static final List<String> PENDING_VALIDATION_STATUSES = List.of(
            "WORK_DONE", "RESOLUTION_SUBMITTED");

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final CaseStockConsumptionRepository consumptionRepository;
    private final EquipmentRepository equipmentRepository;
    private final MaterialReturnRepository materialReturnRepository;

    // ── Paginated audit list ────────────────────────────────────────────────

    public InterventionAuditPageDto getAudits(
            String search,
            String module,
            String status,
            String eventType,
            String slaStatus,
            String proofStatus,
            String governorate,
            String technician,
            Instant from,
            Instant to,
            String sort,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 25 : Math.min(size, 200);

        Criteria criteria = buildCriteria(search, module, status, eventType, slaStatus,
                proofStatus, governorate, technician, from, to);

        Query countQuery = criteria != null ? Query.query(criteria) : new Query();
        long total = mongoTemplate.count(countQuery, CaseEntity.class, CASES_COLLECTION);

        List<InterventionAuditDto> content = new ArrayList<>();
        if (total > 0 && (long) safePage * safeSize < total) {
            Query pageQuery = criteria != null ? Query.query(criteria) : new Query();
            pageQuery.with(resolveSort(sort))
                    .skip((long) safePage * safeSize)
                    .limit(safeSize);
            List<CaseEntity> cases = mongoTemplate.find(pageQuery, CaseEntity.class, CASES_COLLECTION);
            content = mapCases(cases);
        }

        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new InterventionAuditPageDto(content, total, totalPages, safePage, safeSize);
    }

    // ── Summary cards ───────────────────────────────────────────────────────

    public InterventionAuditSummaryDto getSummary() {
        Instant startOfToday = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault()).toInstant();

        long total = mongoTemplate.count(new Query(), CaseEntity.class, CASES_COLLECTION);

        long completedToday = mongoTemplate.count(
                Query.query(Criteria.where("work.dateFinTrv").gte(startOfToday)),
                CaseEntity.class, CASES_COLLECTION);

        long slaBreached = mongoTemplate.count(
                Query.query(Criteria.where("slaTracking.status").in(List.of("BREACHED", "COMPLETED_LATE"))),
                CaseEntity.class, CASES_COLLECTION);

        long pendingValidation = mongoTemplate.count(
                Query.query(Criteria.where("status").in(PENDING_VALIDATION_STATUSES)),
                CaseEntity.class, CASES_COLLECTION);

        long proofMissing = mongoTemplate.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("status").in(COMPLETED_STATUSES),
                        Criteria.where("timeline.data.proofFileId").exists(false))),
                CaseEntity.class, CASES_COLLECTION);

        Map<String, MaterialQuantityGrouping.MaterialAccumulator> materialsBreakdownMap = new LinkedHashMap<>();
        for (CaseStockConsumption consumption : consumptionRepository.findAllByOrderByConsumedAtDesc()) {
            if (consumption.getConsumedItems() == null) continue;
            for (ConsumedEquipmentItem item : consumption.getConsumedItems()) {
                MaterialQuantityGrouping.add(
                        materialsBreakdownMap,
                        item.getEquipmentId(),
                        item.getEquipmentName(),
                        item.getUnit(),
                        item.getQuantityUsed());
            }
        }
        List<MaterialQuantityBreakdownDto> materialsConsumedBreakdown =
                MaterialQuantityGrouping.toBreakdownList(materialsBreakdownMap);
        long materialsConsumed = MaterialQuantityGrouping.totalQuantity(materialsConsumedBreakdown);

        return new InterventionAuditSummaryDto(
                total, completedToday, slaBreached, pendingValidation, proofMissing,
                materialsConsumed, materialsConsumedBreakdown);
    }

    // ── Criteria + sort ─────────────────────────────────────────────────────

    private Criteria buildCriteria(
            String search, String module, String status, String eventType, String slaStatus,
            String proofStatus, String governorate, String technician, Instant from, Instant to
    ) {
        List<Criteria> and = new ArrayList<>();

        if (notBlank(module)) {
            Criteria m = moduleCriteria(module.trim().toUpperCase(Locale.ROOT));
            if (m != null) and.add(m);
        }
        if (notBlank(status)) {
            and.add(Criteria.where("status").is(status.trim().toUpperCase(Locale.ROOT)));
        }
        if (notBlank(eventType)) {
            String et = eventType.trim().toUpperCase(Locale.ROOT);
            if (TIMELINE_EVENT_TYPES.contains(et)) {
                and.add(Criteria.where("timeline.eventType").is(et));
            }
        }
        if (notBlank(slaStatus)) {
            and.add(Criteria.where("slaTracking.status").in(slaStatusValues(slaStatus.trim().toUpperCase(Locale.ROOT))));
        }
        if (notBlank(proofStatus)) {
            String ps = proofStatus.trim().toUpperCase(Locale.ROOT);
            if (ps.equals("MISSING")) {
                and.add(Criteria.where("timeline.data.proofFileId").exists(false));
            } else if (ps.equals("UPLOADED") || ps.equals("PRESENT")) {
                and.add(Criteria.where("timeline.data.proofFileId").exists(true));
            }
        }
        if (notBlank(governorate)) {
            and.add(Criteria.where("gouvernorat").regex(contains(governorate), "i"));
        }
        if (notBlank(technician)) {
            and.add(new Criteria().orOperator(
                    Criteria.where("techId").is(technician.trim()),
                    Criteria.where("techName").regex(contains(technician), "i")));
        }
        if (from != null || to != null) {
            Criteria d = Criteria.where("updatedAt");
            if (from != null) d = d.gte(from);
            if (to != null) d = d.lte(to);
            and.add(d);
        }
        if (notBlank(search)) {
            String rx = contains(search);
            and.add(new Criteria().orOperator(
                    Criteria.where("crmCase").regex(rx, "i"),
                    Criteria.where("techName").regex(rx, "i"),
                    Criteria.where("sttName").regex(rx, "i"),
                    Criteria.where("zmName").regex(rx, "i"),
                    Criteria.where("clientName").regex(rx, "i")));
        }

        if (and.isEmpty()) return null;
        return new Criteria().andOperator(and.toArray(new Criteria[0]));
    }

    private Sort resolveSort(String sort) {
        if (sort != null) {
            switch (sort.trim().toLowerCase(Locale.ROOT)) {
                case "finished", "completed" -> { return Sort.by(Sort.Direction.DESC, "work.dateFinTrv"); }
                case "created" -> { return Sort.by(Sort.Direction.DESC, "createdAt"); }
                case "reference" -> { return Sort.by(Sort.Direction.ASC, "crmCase"); }
                default -> { /* fall through */ }
            }
        }
        return Sort.by(Sort.Direction.DESC, "updatedAt");
    }

    private Criteria moduleCriteria(String module) {
        return switch (module) {
            case "ACT" -> Criteria.where("type").is("ACTIVATION");
            case "RES" -> new Criteria().orOperator(
                    Criteria.where("caseModule").is("RES"),
                    Criteria.where("type").is("RESILIATION"));
            case "PLT" -> new Criteria().andOperator(
                    Criteria.where("type").is("PLAINTE"),
                    new Criteria().orOperator(
                            Criteria.where("caseModule").is("PLT"),
                            Criteria.where("caseModule").exists(false),
                            Criteria.where("caseModule").is(null)));
            default -> null;
        };
    }

    private List<String> slaStatusValues(String category) {
        return switch (category) {
            case "ON_TIME", "ON_TRACK" -> List.of("ON_TRACK", "COMPLETED_ON_TIME");
            case "WARNING" -> List.of("WARNING");
            case "BREACHED" -> List.of("BREACHED", "COMPLETED_LATE");
            default -> List.of(category); // allow raw SlaStatus name
        };
    }

    // ── Mapping ─────────────────────────────────────────────────────────────

    private List<InterventionAuditDto> mapCases(List<CaseEntity> cases) {
        Map<String, AppUser> users = loadUsersByEmail();

        List<String> caseIds = cases.stream().map(CaseEntity::getId).filter(java.util.Objects::nonNull).toList();

        // Consumption per case
        Map<String, CaseStockConsumption> consumptionByCase = new HashMap<>();
        if (!caseIds.isEmpty()) {
            for (CaseStockConsumption c : consumptionRepository.findByCaseIdIn(caseIds)) {
                consumptionByCase.put(c.getCaseId(), c);
            }
        }

        // Equipment lookup (article code / category / unit)
        List<String> equipmentIds = consumptionByCase.values().stream()
                .filter(c -> c.getConsumedItems() != null)
                .flatMap(c -> c.getConsumedItems().stream())
                .map(ConsumedEquipmentItem::getEquipmentId)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        Map<String, Equipment> equipmentById = new HashMap<>();
        if (!equipmentIds.isEmpty()) {
            equipmentRepository.findAllById(equipmentIds).forEach(e -> equipmentById.put(e.getId(), e));
        }

        // Related material returns per case (cross-link only)
        Map<String, List<MaterialReturnRequest>> returnsByCase = new HashMap<>();
        if (!caseIds.isEmpty()) {
            for (MaterialReturnRequest r : materialReturnRepository.findByRelatedCaseIdIn(caseIds)) {
                returnsByCase.computeIfAbsent(r.getRelatedCaseId(), k -> new ArrayList<>()).add(r);
            }
        }

        List<InterventionAuditDto> result = new ArrayList<>();
        for (CaseEntity c : cases) {
            result.add(toDto(c, users, consumptionByCase.get(c.getId()), equipmentById,
                    returnsByCase.getOrDefault(c.getId(), List.of())));
        }
        return result;
    }

    private InterventionAuditDto toDto(
            CaseEntity c,
            Map<String, AppUser> users,
            CaseStockConsumption consumption,
            Map<String, Equipment> equipmentById,
            List<MaterialReturnRequest> returns
    ) {
        List<CaseTimelineEvent> rawTimeline = c.getTimeline() != null ? c.getTimeline() : List.of();

        // Build timeline DTOs (chronological), keep only intervention-relevant events.
        List<InterventionAuditEventDto> timeline = rawTimeline.stream()
                .filter(ev -> ev.getEventType() != null && TIMELINE_EVENT_TYPES.contains(ev.getEventType().name()))
                .sorted(Comparator.comparing(ev -> ev.getOccurredAt() != null ? ev.getOccurredAt() : Instant.EPOCH))
                .map(ev -> toEventDto(c, ev, users))
                .collect(Collectors.toCollection(ArrayList::new));

        CaseTimelineEvent lastEvent = rawTimeline.stream()
                .filter(ev -> ev.getEventType() != null && TIMELINE_EVENT_TYPES.contains(ev.getEventType().name()))
                .max(Comparator.comparing(ev -> ev.getOccurredAt() != null ? ev.getOccurredAt() : Instant.EPOCH))
                .orElse(null);

        // Proof / GPS
        ProofGps proofGps = resolveProofGps(c, rawTimeline);

        // Materials
        List<ConsumedMaterialDto> materials = mapMaterials(consumption, equipmentById);
        int materialCount = materials.size();
        int totalMaterialQty = materials.stream().mapToInt(ConsumedMaterialDto::getQuantity).sum();

        // Timings
        Instant assignedTechAt = c.getDateAffectationTech();
        Instant assignedSttAt = c.getDateAffectationStt();
        Instant assignedZmAt = c.getDateAffectationZm();
        Instant assignedAt = firstNonNull(assignedTechAt, assignedSttAt, assignedZmAt);
        Instant rdvAt = c.getRdv() != null ? c.getRdv().getDatePriseRdv() : null;
        Instant startedAt = earliestEventTime(rawTimeline, List.of("RDV_UPDATED", "DIAGNOSIS_UPDATED", "WORK_UPDATED"));
        Instant completedAt = c.getWork() != null && c.getWork().getDateFinTrv() != null
                ? c.getWork().getDateFinTrv()
                : latestEventTimeByNewStatus(rawTimeline, List.of("WORK_DONE", "RESOLUTION_SUBMITTED"));
        Instant validatedAt = c.getZmValidation() != null ? c.getZmValidation().getValidatedAt() : null;
        Instant closedAt = latestEventTime(rawTimeline, List.of("CASE_CLOSED"));
        if (closedAt == null && "CLOSED".equals(statusName(c))) closedAt = c.getUpdatedAt();

        // SLA
        SlaTrackingInfo sla = c.getSlaTracking();
        String slaStatus = sla != null && sla.getStatus() != null ? sla.getStatus().name() : null;
        String slaCategory = slaCategory(sla != null ? sla.getStatus() : null);
        Instant slaDeadline = sla != null ? sla.getDeadlineAt() : null;
        int escalationLevel = resolveEscalationLevel(c, rawTimeline);

        // Last action actor
        AppUser actorUser = lastEvent != null && lastEvent.getActor() != null
                ? users.get(lastEvent.getActor().toLowerCase(Locale.ROOT)) : null;

        return InterventionAuditDto.builder()
                .auditId(c.getId())
                .caseId(c.getId())
                .caseReference(c.getCrmCase())
                .module(moduleOf(statusEnumName(c.getType()), c.getCaseModule() != null ? c.getCaseModule().name() : null))
                .caseType(c.getType() != null ? c.getType().name() : null)
                .currentStatus(statusName(c))
                .eventType(lastEvent != null && lastEvent.getEventType() != null ? lastEvent.getEventType().name() : null)
                .statusBefore(lastEvent != null && lastEvent.getPreviousStatus() != null ? lastEvent.getPreviousStatus().name() : null)
                .statusAfter(lastEvent != null && lastEvent.getNewStatus() != null ? lastEvent.getNewStatus().name() : null)
                .actorId(actorUser != null ? actorUser.getId() : null)
                .actorName(actorUser != null ? actorUser.getFullName() : friendlyActor(lastEvent != null ? lastEvent.getActor() : null))
                .actorRole(actorUser != null ? displayRole(actorUser.getRole().name()) : systemRole(lastEvent != null ? lastEvent.getActor() : null))
                .lastActionTitle(lastEvent != null ? lastEvent.getTitle() : null)
                .lastActionAt(lastEvent != null ? lastEvent.getOccurredAt() : c.getUpdatedAt())
                .zmId(c.getZmId())
                .zmName(c.getZmName())
                .sttId(c.getSttId())
                .sttName(c.getSttName())
                .technicianId(c.getTechId())
                .technicianName(c.getTechName())
                .governorate(c.getGouvernorat())
                .delegation(c.getDelegation())
                .localite(c.getLocalite())
                .customerName(c.getClientName())
                .customerPhone(c.getClientPhone())
                .createdAt(c.getCreatedAt())
                .assignedAt(assignedAt)
                .assignedZmAt(assignedZmAt)
                .assignedSttAt(assignedSttAt)
                .assignedTechAt(assignedTechAt)
                .rdvAt(rdvAt)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .validatedAt(validatedAt)
                .closedAt(closedAt)
                .slaStatus(slaStatus)
                .slaCategory(slaCategory)
                .slaDeadline(slaDeadline)
                .escalationLevel(escalationLevel)
                .proofPresent(proofGps.proofFileId != null)
                .proofCount(proofGps.proofCount)
                .proofFileId(proofGps.proofFileId)
                .proofFileName(proofGps.proofFileName)
                .gpsLatitude(proofGps.lat)
                .gpsLongitude(proofGps.lng)
                .gpsAccuracy(proofGps.accuracy)
                .consumedMaterials(materials)
                .materialCount(materialCount)
                .totalMaterialQuantity(totalMaterialQty)
                .relatedReturns(returns.stream()
                        .map(r -> new InterventionMaterialReturnRefDto(
                                r.getId(), r.getReturnCode(),
                                r.getStatus() != null ? r.getStatus().name() : null,
                                r.getReason() != null ? r.getReason().name() : null))
                        .toList())
                .timeline(timeline)
                .notes(firstNonBlank(c.getCommentaire(),
                        c.getWork() != null ? c.getWork().getCmtTrvStt() : null))
                .build();
    }

    private List<ConsumedMaterialDto> mapMaterials(CaseStockConsumption consumption, Map<String, Equipment> equipmentById) {
        if (consumption == null || consumption.getConsumedItems() == null) return new ArrayList<>();
        List<ConsumedMaterialDto> out = new ArrayList<>();
        for (ConsumedEquipmentItem it : consumption.getConsumedItems()) {
            Equipment eq = it.getEquipmentId() != null ? equipmentById.get(it.getEquipmentId()) : null;
            out.add(ConsumedMaterialDto.builder()
                    .equipmentId(it.getEquipmentId())
                    .equipmentName(it.getEquipmentName() != null ? it.getEquipmentName()
                            : (eq != null ? eq.getName() : null))
                    .articleCode(eq != null ? eq.getArticleCode() : null)
                    .category(eq != null && eq.getCategory() != null ? eq.getCategory().name() : null)
                    .quantity(it.getQuantityUsed())
                    .unit(it.getUnit() != null ? it.getUnit()
                            : (eq != null && eq.getUnit() != null ? eq.getUnit().name() : null))
                    .consumedAt(consumption.getConsumedAt())
                    .consumedByTechName(consumption.getTechnicianName())
                    .stockBefore(it.getStockBefore())
                    .stockAfter(it.getStockAfter())
                    .build());
        }
        return out;
    }

    private InterventionAuditEventDto toEventDto(CaseEntity c, CaseTimelineEvent ev, Map<String, AppUser> users) {
        Map<String, Object> data = ev.getData() != null ? ev.getData() : Map.of();
        String actor = ev.getActor();
        AppUser actorUser = actor != null ? users.get(actor.toLowerCase(Locale.ROOT)) : null;

        String targetEmail = asString(data.get("assigneeEmail"));
        AppUser targetUser = targetEmail != null ? users.get(targetEmail.toLowerCase(Locale.ROOT)) : null;
        String eventType = ev.getEventType() != null ? ev.getEventType().name() : null;

        return InterventionAuditEventDto.builder()
                .id(ev.getId())
                .caseId(c.getId())
                .caseReference(c.getCrmCase())
                .eventType(eventType)
                .category(categoryOf(eventType))
                .title(ev.getTitle())
                .description(ev.getDetails())
                .actorName(actorUser != null ? actorUser.getFullName() : friendlyActor(actor))
                .actorRole(actorUser != null ? displayRole(actorUser.getRole().name()) : systemRole(actor))
                .actorEmail(actor)
                .targetName(targetUser != null ? targetUser.getFullName() : targetEmail)
                .targetRole(displayRole(asString(data.get("assigneeRole"))))
                .targetEmail(targetEmail)
                .previousStatus(ev.getPreviousStatus() != null ? ev.getPreviousStatus().name() : null)
                .newStatus(ev.getNewStatus() != null ? ev.getNewStatus().name() : null)
                .occurredAt(ev.getOccurredAt())
                .latitude(asString(data.get("locationLatitude")))
                .longitude(asString(data.get("locationLongitude")))
                .gpsCapturedAt(instant(data.get("locationCapturedAt")))
                .gpsAccuracyMeters(asDouble(data.get("locationAccuracyMeters")))
                .proofFileId(asString(data.get("proofFileId")))
                .proofFilename(asString(data.get("proofFilename")))
                .slaStatus(asString(data.get("slaStatus")))
                .breachAgeMinutes(asLong(data.get("breachAgeMinutes")))
                .build();
    }

    // ── Proof / GPS resolution ──────────────────────────────────────────────

    private static final class ProofGps {
        String proofFileId;
        String proofFileName;
        int proofCount;
        Double lat;
        Double lng;
        Double accuracy;
    }

    private ProofGps resolveProofGps(CaseEntity c, List<CaseTimelineEvent> timeline) {
        ProofGps pg = new ProofGps();

        CaseTimelineEvent latestProof = null;
        for (CaseTimelineEvent ev : timeline) {
            Object fid = ev.getData() != null ? ev.getData().get("proofFileId") : null;
            if (fid != null) {
                pg.proofCount++;
                if (latestProof == null || after(ev.getOccurredAt(), latestProof.getOccurredAt())) {
                    latestProof = ev;
                }
            }
        }
        if (latestProof != null) {
            pg.proofFileId = asString(latestProof.getData().get("proofFileId"));
            pg.proofFileName = asString(latestProof.getData().get("proofFilename"));
        }

        // GPS — prefer work location snapshot, then latest proof event data, then case-level.
        WorkInfo work = c.getWork();
        if (work != null && work.getLocationLatitude() != null && work.getLocationLongitude() != null) {
            pg.lat = work.getLocationLatitude();
            pg.lng = work.getLocationLongitude();
            pg.accuracy = work.getLocationAccuracyMeters();
        } else if (latestProof != null) {
            pg.lat = asDouble(latestProof.getData().get("locationLatitude"));
            pg.lng = asDouble(latestProof.getData().get("locationLongitude"));
            pg.accuracy = asDouble(latestProof.getData().get("locationAccuracyMeters"));
        }
        if (pg.lat == null) pg.lat = parseDouble(c.getLatitude());
        if (pg.lng == null) pg.lng = parseDouble(c.getLongitude());
        return pg;
    }

    private int resolveEscalationLevel(CaseEntity c, List<CaseTimelineEvent> timeline) {
        int count = (int) timeline.stream()
                .filter(ev -> ev.getEventType() != null && "SLA_ESCALATED".equals(ev.getEventType().name()))
                .count();
        if (count == 0 && c.getSlaTracking() != null && c.getSlaTracking().getEscalationTriggeredAt() != null) {
            return 1;
        }
        return count;
    }

    // ── Timeline scan helpers ───────────────────────────────────────────────

    private Instant earliestEventTime(List<CaseTimelineEvent> timeline, List<String> eventTypes) {
        return timeline.stream()
                .filter(ev -> ev.getEventType() != null && eventTypes.contains(ev.getEventType().name()))
                .map(CaseTimelineEvent::getOccurredAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant latestEventTime(List<CaseTimelineEvent> timeline, List<String> eventTypes) {
        return timeline.stream()
                .filter(ev -> ev.getEventType() != null && eventTypes.contains(ev.getEventType().name()))
                .map(CaseTimelineEvent::getOccurredAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant latestEventTimeByNewStatus(List<CaseTimelineEvent> timeline, List<String> statuses) {
        return timeline.stream()
                .filter(ev -> ev.getNewStatus() != null && statuses.contains(ev.getNewStatus().name()))
                .map(CaseTimelineEvent::getOccurredAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    // ── Small helpers ───────────────────────────────────────────────────────

    private Map<String, AppUser> loadUsersByEmail() {
        Map<String, AppUser> map = new HashMap<>();
        for (AppUser u : userRepository.findAll()) {
            if (u.getEmail() != null) map.put(u.getEmail().toLowerCase(Locale.ROOT), u);
        }
        return map;
    }

    private String categoryOf(String eventType) {
        if (eventType == null) return "LIFECYCLE";
        return switch (eventType) {
            case "ASSIGNMENT_UPDATED" -> "ASSIGNMENT";
            case "RDV_UPDATED", "DIAGNOSIS_UPDATED", "WORK_UPDATED" -> "FIELD_EXECUTION";
            case "STOCK_CONSUMED" -> "MATERIAL";
            case "SLA_WARNING", "SLA_BREACHED", "SLA_ESCALATED" -> "SLA_ESCALATION";
            case "ZM_VALIDATION", "CASE_CLOSED", "CASE_REOPENED" -> "COMPLETION_CLOSURE";
            default -> "LIFECYCLE";
        };
    }

    private String slaCategory(SlaStatus status) {
        if (status == null) return "NONE";
        return switch (status) {
            case ON_TRACK, COMPLETED_ON_TIME -> "ON_TIME";
            case WARNING -> "WARNING";
            case BREACHED, COMPLETED_LATE -> "BREACHED";
        };
    }

    private String moduleOf(String type, String caseModule) {
        if (caseModule != null && !caseModule.isBlank()) return caseModule.trim().toUpperCase(Locale.ROOT);
        if (type == null) return "PLT";
        return switch (type) {
            case "ACTIVATION" -> "ACT";
            case "RESILIATION" -> "RES";
            default -> "PLT";
        };
    }

    private String statusName(CaseEntity c) {
        return c.getStatus() != null ? c.getStatus().name() : null;
    }

    private String statusEnumName(Enum<?> e) {
        return e != null ? e.name() : null;
    }

    private String displayRole(String rawRole) {
        if (rawRole == null) return null;
        String r = rawRole.trim().toUpperCase(Locale.ROOT);
        if (r.startsWith("ROLE_")) r = r.substring(5);
        return r.isEmpty() ? null : r;
    }

    private String systemRole(String actor) {
        return actor != null && actor.equalsIgnoreCase("SYSTEM") ? "SYSTEM" : null;
    }

    private String friendlyActor(String actor) {
        if (actor == null) return null;
        return actor.equalsIgnoreCase("SYSTEM") ? "System" : actor;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String contains(String s) {
        return ".*" + Pattern.quote(s.trim()) + ".*";
    }

    private boolean after(Instant a, Instant b) {
        if (a == null) return false;
        if (b == null) return true;
        return a.isAfter(b);
    }

    private String asString(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Instant instant(Object o) {
        if (o instanceof Date d) return d.toInstant();
        if (o instanceof Instant i) return i;
        return null;
    }

    private Double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return null;
    }

    private Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return null;
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}

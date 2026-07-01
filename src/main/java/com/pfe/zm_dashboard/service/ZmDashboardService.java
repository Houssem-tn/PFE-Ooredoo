package com.pfe.zm_dashboard.service;

import com.pfe.MainPackage.Dto.UserDTO;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Repository.NotificationRepository;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.UserService;
import com.pfe.case_management.entity.ZmFieldImportAuditEntity;
import com.pfe.case_management.repository.ZmFieldImportAuditRepository;
import com.pfe.stock_management.entity.CaseStockConsumption;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.EquipmentInstance;
import com.pfe.stock_management.entity.StockThreshold;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.material_return.MaterialReturnRequest;
import com.pfe.stock_management.material_return.MaterialReturnRepository;
import com.pfe.stock_management.material_return.MaterialReturnStatus;
import com.pfe.stock_management.material_return.ReturnCondition;
import com.pfe.stock_management.material_return.ReturnedItem;
import com.pfe.stock_management.model.ConsumedEquipmentItem;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.repository.CaseStockConsumptionRepository;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentInstanceRepository;
import com.pfe.stock_management.repository.StockThresholdRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.ActionGroup;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.ActionItems;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.ActionPreview;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.AlertItem;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.CompletionPoint;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.GovernorateStat;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.Kpis;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.ModuleStats;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.SttPerformance;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.StatusCount;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.StockOverview;
import com.pfe.zm_dashboard.dto.ZmDashboardResponse.TodoImportStat;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the Zone Manager operational dashboard in a single pass, scoped to the logged-in ZM.
 *
 * <p>Case metrics are produced by one MongoDB {@code $facet} aggregation over the {@code cases}
 * collection (grouped by status / module / governorate / STT) so we never load case entities into
 * memory — this keeps the endpoint fast even for a ZM owning tens of thousands of imported cases.
 * Stock, DPM, return, import and alert figures come from their existing repositories, which are all
 * low-volume relative to cases.
 *
 * <p><b>SLA "overdue" is a stored-state approximation</b> (no per-case live recompute): a case is
 * overdue when its persisted {@code slaTracking.status} is BREACHED/COMPLETED_LATE, or its
 * {@code deadlineAt} is in the past while the case is still open. This matches the semantics used by
 * {@code CaseAnalyticsService} without the O(n) live refresh.
 */
@Service
@RequiredArgsConstructor
public class ZmDashboardService {

    private static final String CASES_COLLECTION = "cases";
    private static final String RES_IMPORT_SOURCE = "RES_TODO_IMPORT";

    private static final List<String> SCOPED_TYPES = List.of("ACTIVATION", "PLAINTE");

    // Field work is "completed" once submitted by the executor (mirrors CaseService.isWorkCompletedStatus).
    private static final List<String> COMPLETED_WORK = List.of(
            "WORK_DONE", "RESOLUTION_SUBMITTED", "VALIDATED_BY_ZM", "CLOSED");
    // Cases whose response/RDV SLA can no longer become overdue.
    private static final List<String> NOT_OVERDUE = List.of(
            "CLOSED", "VALIDATED_BY_ZM", "ABANDONED", "WORK_DONE", "RESOLUTION_SUBMITTED");
    private static final List<String> WAITING_VALIDATION = List.of("WORK_DONE", "RESOLUTION_SUBMITTED");
    private static final List<String> IN_PROGRESS = List.of(
            "RDV_SET", "NOT_REACHABLE", "DIAGNOSIS_STEP", "ESCALATED", "RETURNED_TO_STT", "REOPENED", "REJECTED_BY_ZM");
    private static final List<String> SLA_LATE = List.of("BREACHED", "COMPLETED_LATE");

    private static final List<DpmStatus> DPM_CONFIRMED = List.of(
            DpmStatus.MATERIAL_RECEIVED, DpmStatus.COMPLETED, DpmStatus.FULLY_CONFIRMED, DpmStatus.PARTIALLY_CONFIRMED);
    private static final List<DpmStatus> DPM_TERMINAL = List.of(
            DpmStatus.COMPLETED, DpmStatus.CANCELLED, DpmStatus.TEAM_STOCK_REJECTED, DpmStatus.REJECTED);
    private static final List<DpmStatus> DPM_FOLLOW_UP = List.of(
            DpmStatus.WAITING_TEAM_STOCK_APPROVAL, DpmStatus.RDV_SCHEDULED, DpmStatus.RDV_CONFIRMED_BY_STT);

    private static final List<MaterialReturnStatus> RETURN_PENDING_ZM = List.of(
            MaterialReturnStatus.PENDING, MaterialReturnStatus.SUBMITTED, MaterialReturnStatus.PENDING_ZM_REVIEW,
            MaterialReturnStatus.RESCHEDULE_REQUESTED, MaterialReturnStatus.SCHEDULE_REJECTED_BY_STT);
    private static final List<ReturnCondition> DAMAGED_OR_MISSING = List.of(
            ReturnCondition.DAMAGED, ReturnCondition.BROKEN, ReturnCondition.LOST);
    private static final List<ReturnCondition> RECOVERABLE = List.of(
            ReturnCondition.GOOD, ReturnCondition.RECONDITIONABLE);

    private static final double DEFAULT_WARNING_THRESHOLD = 3;
    private static final int PREVIEW_LIMIT = 8;

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DpmRepository dpmRepository;
    private final MaterialReturnRepository materialReturnRepository;
    private final SttStockRepository sttStockRepository;
    private final CaseStockConsumptionRepository caseStockConsumptionRepository;
    private final EquipmentInstanceRepository equipmentInstanceRepository;
    private final StockThresholdRepository stockThresholdRepository;
    private final ZmFieldImportAuditRepository importAuditRepository;
    private final NotificationRepository notificationRepository;

    public ZmDashboardResponse getDashboard() {
        AppUser zm = currentZm();
        String email = zm.getEmail();
        Instant now = Instant.now();
        Date nowDate = Date.from(now);

        // ── Cases: single aggregation → all case-based sections ────────────────────
        Document facet = runCaseFacet(email, nowDate, now.minus(Duration.ofDays(90)));

        Map<String, Long> statusCounts = toCountMap(facet.getList("byStatus", Document.class));
        long overdueTotal = firstCount(facet.getList("overdueTotal", Document.class));
        Map<String, Long> moduleOverdue = toCountMap(facet.getList("byModuleOverdue", Document.class));

        List<StatusCount> casesByStatus = statusCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new StatusCount(e.getKey(), e.getValue()))
                .toList();

        List<ModuleStats> casesByModule = buildModuleStats(facet.getList("byModuleStatus", Document.class), moduleOverdue);
        List<GovernorateStat> governorateStats = buildGovernorateStats(facet.getList("byGov", Document.class));
        List<CompletionPoint> completionTrend = buildCompletionTrend(facet.getList("completionTrend", Document.class));

        // ── STT scope + performance (case metrics from aggregation, material from ledgers) ──
        List<String> sttIds = userService.findResponsibleSttIds(zm);
        List<UserDTO> stts = safeStts(email);
        List<MaterialReturnRequest> zmReturns = materialReturnRepository.findByZmIdOrderByCreatedAtDesc(zm.getId());
        List<SttPerformance> sttPerformance = buildSttPerformance(
                facet.getList("byStt", Document.class), stts, zmReturns);
        long activeStts = sttPerformance.stream().filter(s -> (s.assigned() - s.completed()) > 0).count();

        // ── Stock & DPM & returns overview ─────────────────────────────────────────
        List<DpmRequest> zmDpms = dpmRepository.findByZmIdOrderByCreatedAtDesc(zm.getId());
        List<SttStock> teamStocks = sttIds.stream()
                .map(id -> sttStockRepository.findBySttId(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<EquipmentInstance> trackedUnits = sttIds.isEmpty()
                ? List.of() : equipmentInstanceRepository.findByOriginSttIdIn(sttIds);
        StockOverview stockOverview = buildStockOverview(zm, zmDpms, teamStocks, zmReturns, trackedUnits);

        // ── KPIs ───────────────────────────────────────────────────────────────────
        long openDpms = zmDpms.stream().filter(d -> !DPM_TERMINAL.contains(d.getStatus())).count();
        long pendingReturns = zmReturns.stream().filter(r -> RETURN_PENDING_ZM.contains(r.getStatus())).count();
        Kpis kpis = new Kpis(
                sum(statusCounts.values()),
                statusCounts.getOrDefault("ASSIGNED_TO_ZM", 0L),
                statusCounts.getOrDefault("ASSIGNED_TO_STT", 0L),
                statusCounts.getOrDefault("ASSIGNED_TO_TECH", 0L),
                countIn(statusCounts, IN_PROGRESS),
                countIn(statusCounts, COMPLETED_WORK),
                countIn(statusCounts, WAITING_VALIDATION),
                statusCounts.getOrDefault("CLOSED", 0L),
                overdueTotal,
                activeStts,
                openDpms,
                pendingReturns);

        // ── Action items (counts + deep-link previews) ─────────────────────────────
        ActionItems actionItems = buildActionItems(email, nowDate, zmDpms, zmReturns, trackedUnits, teamStocks, zm);

        // ── Imports + alerts ────────────────────────────────────────────────────────
        List<TodoImportStat> todoImportStats = buildImports(email);
        List<AlertItem> recentAlerts = buildAlerts(email);

        return new ZmDashboardResponse(
                kpis, casesByModule, casesByStatus, sttPerformance, actionItems,
                stockOverview, todoImportStats, governorateStats, completionTrend, recentAlerts, now);
    }

    // ── Case aggregation ───────────────────────────────────────────────────────────

    private Document runCaseFacet(String email, Date now, Instant trendFrom) {
        Document match = new Document("$match", new Document()
                .append("zmEmail", new Document("$regex", "^" + Pattern.quote(email) + "$").append("$options", "i"))
                .append("type", new Document("$in", SCOPED_TYPES)));

        Document addFields = new Document("$addFields", new Document()
                .append("moduleKey", new Document("$switch", new Document()
                        .append("branches", List.of(
                                new Document("case", eq("$type", "ACTIVATION")).append("then", "ACT"),
                                new Document("case", new Document("$or", List.of(
                                        eq("$caseModule", "RES"), eq("$source", RES_IMPORT_SOURCE))))
                                        .append("then", "RES")))
                        .append("default", "PLT")))
                .append("completedWork", new Document("$in", List.of("$status", COMPLETED_WORK)))
                .append("overdue", new Document("$or", List.of(
                        new Document("$in", List.of("$slaTracking.status", SLA_LATE)),
                        new Document("$and", List.of(
                                new Document("$ne", bsonArgs("$slaTracking.deadlineAt", null)),
                                new Document("$lt", List.of("$slaTracking.deadlineAt", now)),
                                new Document("$not", List.of(new Document("$in", List.of("$status", NOT_OVERDUE))))))))));

        Document facet = new Document("$facet", new Document()
                .append("byStatus", List.of(group("$status")))
                .append("byModuleStatus", List.of(new Document("$group", new Document()
                        .append("_id", new Document("m", "$moduleKey").append("s", "$status"))
                        .append("count", new Document("$sum", 1)))))
                .append("byModuleOverdue", List.of(
                        new Document("$match", new Document("overdue", true)), group("$moduleKey")))
                .append("byGov", List.of(new Document("$group", new Document()
                        .append("_id", "$gouvernorat")
                        .append("total", new Document("$sum", 1))
                        .append("pending", condSum(eq("$status", "ASSIGNED_TO_ZM")))
                        .append("overdue", condSum("$overdue")))))
                .append("byStt", List.of(
                        new Document("$match", new Document("sttId", new Document("$ne", null))),
                        new Document("$group", new Document()
                                .append("_id", "$sttId")
                                .append("sttName", new Document("$first", "$sttName"))
                                .append("sttEmail", new Document("$first", "$sttEmail"))
                                .append("total", new Document("$sum", 1))
                                .append("completed", condSum("$completedWork"))
                                .append("late", condSum("$overdue"))
                                .append("onTime", condSum(eq("$slaTracking.status", "COMPLETED_ON_TIME")))
                                .append("completedLate", condSum(eq("$slaTracking.status", "COMPLETED_LATE")))
                                .append("respSum", new Document("$sum", new Document("$ifNull", List.of("$slaTracking.responseDurationSeconds", 0))))
                                .append("respCount", condSum(new Document("$ne", bsonArgs("$slaTracking.responseDurationSeconds", null)))))))
                .append("overdueTotal", List.of(
                        new Document("$match", new Document("overdue", true)), new Document("$count", "count")))
                .append("completionTrend", List.of(
                        new Document("$match", new Document("completedWork", true)
                                .append("updatedAt", new Document("$ne", null).append("$gte", Date.from(trendFrom)))),
                        new Document("$group", new Document()
                                .append("_id", new Document("$dateToString", new Document("format", "%Y-%m-%d").append("date", "$updatedAt")))
                                .append("count", new Document("$sum", 1))),
                        new Document("$sort", new Document("_id", 1)))));

        Document result = mongoTemplate.getCollection(CASES_COLLECTION)
                .aggregate(List.of(match, addFields, facet))
                .allowDiskUse(true)
                .first();
        return result != null ? result : new Document();
    }

    private static Document eq(String field, Object value) {
        return new Document("$eq", bsonArgs(field, value));
    }

    private static Document group(String idExpr) {
        return new Document("$group", new Document("_id", idExpr).append("count", new Document("$sum", 1)));
    }

    private static List<Object> bsonArgs(Object... values) {
        return Arrays.asList(values);
    }

    /** {@code { $sum: { $cond: [ <boolExpr>, 1, 0 ] } }} */
    private static Document condSum(Object boolExpr) {
        return new Document("$sum", new Document("$cond", List.of(boolExpr, 1, 0)));
    }

    // ── Builders from aggregation output ─────────────────────────────────────────────

    private List<ModuleStats> buildModuleStats(List<Document> byModuleStatus, Map<String, Long> moduleOverdue) {
        Map<String, Map<String, Long>> perModule = new LinkedHashMap<>();
        for (String m : List.of("ACT", "PLT", "RES")) perModule.put(m, new LinkedHashMap<>());
        for (Document d : safe(byModuleStatus)) {
            Document id = (Document) d.get("_id");
            if (id == null) continue;
            String module = id.getString("m");
            String status = id.getString("s");
            long count = num(d.get("count"));
            perModule.computeIfAbsent(module, k -> new LinkedHashMap<>()).merge(status, count, Long::sum);
        }

        List<ModuleStats> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : perModule.entrySet()) {
            Map<String, Long> s = entry.getValue();
            result.add(new ModuleStats(
                    entry.getKey(),
                    sum(s.values()),
                    s.getOrDefault("ASSIGNED_TO_ZM", 0L),
                    s.getOrDefault("ASSIGNED_TO_STT", 0L),
                    s.getOrDefault("ASSIGNED_TO_TECH", 0L),
                    countIn(s, IN_PROGRESS),
                    countIn(s, COMPLETED_WORK),
                    countIn(s, WAITING_VALIDATION),
                    s.getOrDefault("CLOSED", 0L),
                    moduleOverdue.getOrDefault(entry.getKey(), 0L)));
        }
        return result;
    }

    private List<GovernorateStat> buildGovernorateStats(List<Document> byGov) {
        return safe(byGov).stream()
                .map(d -> new GovernorateStat(
                        d.get("_id") != null ? d.getString("_id") : "Unknown",
                        num(d.get("total")), num(d.get("pending")), num(d.get("overdue"))))
                .filter(g -> g.total() > 0)
                .sorted(Comparator.comparingLong(GovernorateStat::total).reversed())
                .toList();
    }

    private List<CompletionPoint> buildCompletionTrend(List<Document> trend) {
        return safe(trend).stream()
                .map(d -> new CompletionPoint(d.getString("_id"), num(d.get("count"))))
                .toList();
    }

    private List<SttPerformance> buildSttPerformance(
            List<Document> byStt, List<UserDTO> stts, List<MaterialReturnRequest> zmReturns) {

        Map<String, Document> caseStatsById = new LinkedHashMap<>();
        for (Document d : safe(byStt)) {
            if (d.get("_id") != null) caseStatsById.put(d.getString("_id"), d);
        }

        // Material figures per STT from the STT stock ledger + returns submitted to this ZM.
        Map<String, long[]> returnsByStt = new LinkedHashMap<>(); // [returnedQty, damagedMissingQty]
        for (MaterialReturnRequest r : zmReturns) {
            if (r.getSttId() == null) continue;
            long[] agg = returnsByStt.computeIfAbsent(r.getSttId(), k -> new long[2]);
            for (ReturnedItem item : safeItems(r)) {
                agg[0] += item.getQuantity();
                if (isDamagedOrMissing(r, item)) agg[1] += item.getQuantity();
            }
        }

        List<SttPerformance> result = new ArrayList<>();
        for (UserDTO stt : stts) {
            Document cs = caseStatsById.remove(stt.id());
            result.add(buildSttRow(stt.id(), stt.fullName(), stt.email(), cs, returnsByStt.get(stt.id())));
        }
        // Include STTs that have cases but are not in the (approved+enabled) team list — never drop numbers.
        for (Document cs : caseStatsById.values()) {
            String id = cs.getString("_id");
            result.add(buildSttRow(id, cs.getString("sttName"), cs.getString("sttEmail"), cs, returnsByStt.get(id)));
        }

        result.sort(Comparator.comparingLong(SttPerformance::assigned).reversed());
        return result;
    }

    private SttPerformance buildSttRow(String sttId, String name, String email, Document cs, long[] returns) {
        long assigned = cs != null ? num(cs.get("total")) : 0;
        long completed = cs != null ? num(cs.get("completed")) : 0;
        long late = cs != null ? num(cs.get("late")) : 0;
        long onTime = cs != null ? num(cs.get("onTime")) : 0;
        long completedLate = cs != null ? num(cs.get("completedLate")) : 0;
        long respSum = cs != null ? num(cs.get("respSum")) : 0;
        long respCount = cs != null ? num(cs.get("respCount")) : 0;

        double avgResolutionMinutes = respCount > 0 ? round2((double) respSum / respCount / 60d) : 0d;
        long resolvedForSla = onTime + completedLate;
        double slaRespectPercent = resolvedForSla > 0 ? round2((double) onTime * 100d / resolvedForSla) : 0d;

        long[] ledger = sttMaterialLedger(sttId);
        long returnedQty = returns != null ? returns[0] : 0;
        long damagedMissing = returns != null ? returns[1] : 0;

        return new SttPerformance(
                sttId, name, email,
                assigned, completed, Math.max(0, assigned - completed), late,
                avgResolutionMinutes, slaRespectPercent,
                ledger[0], ledger[1], returnedQty, damagedMissing);
    }

    /** [received, distributedToTechs] from the STT's stock ledger. */
    private long[] sttMaterialLedger(String sttId) {
        if (sttId == null) return new long[]{0, 0};
        return sttStockRepository.findBySttId(sttId)
                .map(stock -> {
                    long received = 0, distributed = 0;
                    for (SttStockItem item : safeStockItems(stock)) {
                        received += item.getTotalReceived();
                        distributed += item.getTotalDistributedToTechs();
                    }
                    return new long[]{received, distributed};
                })
                .orElse(new long[]{0, 0});
    }

    // ── Stock overview ────────────────────────────────────────────────────────────

    private StockOverview buildStockOverview(
            AppUser zm, List<DpmRequest> zmDpms, List<SttStock> teamStocks,
            List<MaterialReturnRequest> zmReturns, List<EquipmentInstance> trackedUnits) {

        long dpmCreated = zmDpms.size();
        long dpmConfirmed = zmDpms.stream().filter(d -> DPM_CONFIRMED.contains(d.getStatus())).count();

        long received = 0, distributed = 0;
        for (SttStock stock : teamStocks) {
            for (SttStockItem item : safeStockItems(stock)) {
                received += item.getTotalReceived();
                distributed += item.getTotalDistributedToTechs();
            }
        }

        long consumed = caseStockConsumptionRepository.findByZmIdOrderByConsumedAtDesc(zm.getId()).stream()
                .flatMap(c -> safeConsumed(c).stream())
                .mapToLong(ConsumedEquipmentItem::getQuantityUsed)
                .sum();

        long returned = 0, recoverable = 0;
        for (MaterialReturnRequest r : zmReturns) {
            for (ReturnedItem item : safeItems(r)) {
                returned += item.getQuantity();
                if (isRecoverable(r, item)) recoverable += item.getQuantity();
            }
        }

        long missingImeiSerial = trackedUnits.stream().filter(this::isMissingIdentifiers).count();

        return new StockOverview(
                dpmCreated, dpmConfirmed, received, distributed, consumed,
                returned, recoverable, trackedUnits.size(), missingImeiSerial);
    }

    // ── Action items ──────────────────────────────────────────────────────────────

    private ActionItems buildActionItems(
            String email, Date now, List<DpmRequest> zmDpms, List<MaterialReturnRequest> zmReturns,
            List<EquipmentInstance> trackedUnits, List<SttStock> teamStocks, AppUser zm) {

        ActionGroup waitingAssignment = casePreview(
                email, Criteria.where("status").is("ASSIGNED_TO_ZM"), Sort.by(Sort.Direction.ASC, "createdAt"), "gouvernorat");
        ActionGroup waitingValidation = casePreview(
                email, Criteria.where("status").in(WAITING_VALIDATION), Sort.by(Sort.Direction.DESC, "updatedAt"), "status");
        ActionGroup overdue = casePreview(email, overdueCriteria(now), Sort.by(Sort.Direction.ASC, "slaTracking.deadlineAt"), "gouvernorat");

        List<ActionPreview> returnsPreview = zmReturns.stream()
                .filter(r -> RETURN_PENDING_ZM.contains(r.getStatus()))
                .limit(PREVIEW_LIMIT)
                .map(r -> new ActionPreview(r.getId(), r.getReturnCode(), safe(r.getSttName(), r.getSttEmail()),
                        r.getStatus() != null ? r.getStatus().name() : null))
                .toList();
        long pendingReturnsCount = zmReturns.stream().filter(r -> RETURN_PENDING_ZM.contains(r.getStatus())).count();

        List<ActionPreview> dpmPreview = zmDpms.stream()
                .filter(d -> DPM_FOLLOW_UP.contains(d.getStatus()))
                .limit(PREVIEW_LIMIT)
                .map(d -> new ActionPreview(d.getId(), d.getDpmNumber(), safe(d.getSttName(), d.getSttEmail()),
                        d.getStatus() != null ? d.getStatus().name() : null))
                .toList();
        long dpmFollowUpCount = zmDpms.stream().filter(d -> DPM_FOLLOW_UP.contains(d.getStatus())).count();

        List<EquipmentInstance> missing = trackedUnits.stream().filter(this::isMissingIdentifiers).toList();
        List<ActionPreview> missingPreview = missing.stream()
                .limit(PREVIEW_LIMIT)
                .map(i -> new ActionPreview(i.getId(), i.getArticleCode(), safe(i.getEquipmentName(), i.getEquipmentId()),
                        i.getCurrentOwnerName()))
                .toList();

        ActionGroup stockAnomalies = buildStockAnomalies(teamStocks, zm);

        return new ActionItems(
                waitingAssignment, waitingValidation, overdue,
                new ActionGroup(pendingReturnsCount, returnsPreview),
                new ActionGroup(dpmFollowUpCount, dpmPreview),
                new ActionGroup(missing.size(), missingPreview),
                stockAnomalies);
    }

    /** Low-stock scan across the ZM's STT stocks against the ZM-configured thresholds. */
    private ActionGroup buildStockAnomalies(List<SttStock> teamStocks, AppUser zm) {
        List<StockThreshold> thresholds = stockThresholdRepository.findByZmIdAndEnabledTrue(zm.getId());
        Map<String, StockThreshold> byItem = new LinkedHashMap<>();
        for (StockThreshold t : thresholds) if (t.getItemId() != null) byItem.put(t.getItemId(), t);

        List<ActionPreview> preview = new ArrayList<>();
        long count = 0;
        for (SttStock stock : teamStocks) {
            for (SttStockItem item : safeStockItems(stock)) {
                StockThreshold t = byItem.get(item.getEquipmentId());
                if (t != null && Boolean.FALSE.equals(t.getSttAlertsEnabled())) continue;
                double warningAt = t != null && t.getSttWarningThreshold() != null
                        ? t.getSttWarningThreshold()
                        : (t != null ? t.getWarningThreshold() : DEFAULT_WARNING_THRESHOLD);
                int qty = item.getAvailableQuantity();
                if (qty <= warningAt) {
                    count++;
                    if (preview.size() < PREVIEW_LIMIT) {
                        preview.add(new ActionPreview(stock.getSttId(), item.getEquipmentId(),
                                item.getEquipmentName(), qty + " " + safe(item.getUnit(), "units") + " left"));
                    }
                }
            }
        }
        return new ActionGroup(count, preview);
    }

    private ActionGroup casePreview(String email, Criteria extra, Sort sort, String metaField) {
        Query query = new Query(scopedCaseCriteria(email).andOperator(extra));
        query.fields().include("crmCase").include("gouvernorat").include("status").include("sttName");
        query.with(sort).limit(PREVIEW_LIMIT);
        long total = mongoTemplate.count(new Query(scopedCaseCriteria(email).andOperator(extra)), CASES_COLLECTION);
        List<Document> rows = mongoTemplate.find(query, Document.class, CASES_COLLECTION);
        List<ActionPreview> preview = rows.stream()
                .map(d -> new ActionPreview(
                        objId(d), d.getString("crmCase"),
                        safe(d.getString("crmCase"), objId(d)),
                        d.getString(metaField)))
                .toList();
        return new ActionGroup(total, preview);
    }

    private Criteria scopedCaseCriteria(String email) {
        return Criteria.where("zmEmail").regex("^" + Pattern.quote(email) + "$", "i")
                .and("type").in(SCOPED_TYPES);
    }

    private Criteria overdueCriteria(Date now) {
        return new Criteria().orOperator(
                Criteria.where("slaTracking.status").in(SLA_LATE),
                new Criteria().andOperator(
                        Criteria.where("slaTracking.deadlineAt").lt(now),
                        Criteria.where("status").nin(NOT_OVERDUE)));
    }

    // ── Imports + alerts ─────────────────────────────────────────────────────────

    private List<TodoImportStat> buildImports(String email) {
        return importAuditRepository.findTop12ByUploadedByIgnoreCaseOrderByUploadedAtDesc(email).stream()
                .map(this::toImportStat)
                .toList();
    }

    private TodoImportStat toImportStat(ZmFieldImportAuditEntity a) {
        long invalid = nz(a.getInvalidRowsCount()) + nz(a.getFailedRows());
        String status = invalid > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
        return new TodoImportStat(
                a.getBusinessType() != null ? a.getBusinessType().name() : "UNKNOWN",
                a.getSourceFileName(),
                a.getUploadedAt(),
                nz(a.getTotalRows()),
                nz(a.getCreatedCaseCount()),
                nz(a.getDuplicateCount()),
                invalid,
                status);
    }

    private List<AlertItem> buildAlerts(String email) {
        return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email).stream()
                .limit(10)
                .map(this::toAlert)
                .toList();
    }

    private AlertItem toAlert(Notification n) {
        return new AlertItem(
                n.getTitle(),
                n.getMessage(),
                n.getLevel() != null ? n.getLevel().name() : "INFO",
                n.getCategory() != null ? n.getCategory().name() : null,
                n.getCreatedAt(),
                n.getRelatedCaseId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private AppUser currentZm() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private List<UserDTO> safeStts(String email) {
        try {
            return userService.findMyStts(email);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private boolean isDamagedOrMissing(MaterialReturnRequest r, ReturnedItem item) {
        return DAMAGED_OR_MISSING.contains(item.getCondition()) || DAMAGED_OR_MISSING.contains(r.getCondition());
    }

    private boolean isRecoverable(MaterialReturnRequest r, ReturnedItem item) {
        ReturnCondition c = item.getCondition() != null ? item.getCondition() : r.getCondition();
        return RECOVERABLE.contains(c);
    }

    private boolean isMissingIdentifiers(EquipmentInstance i) {
        return isBlank(i.getImei()) && isBlank(i.getSerialNumber());
    }

    private static Map<String, Long> toCountMap(List<Document> docs) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Document d : safe(docs)) {
            Object id = d.get("_id");
            map.put(id != null ? id.toString() : "UNKNOWN", num(d.get("count")));
        }
        return map;
    }

    private static long firstCount(List<Document> docs) {
        List<Document> list = safe(docs);
        return list.isEmpty() ? 0 : num(list.get(0).get("count"));
    }

    private static long countIn(Map<String, Long> counts, List<String> statuses) {
        long total = 0;
        for (String s : statuses) total += counts.getOrDefault(s, 0L);
        return total;
    }

    private static long sum(Collection<Long> values) {
        long total = 0;
        for (Long v : values) total += v != null ? v : 0;
        return total;
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static long nz(Integer value) {
        return value != null ? value : 0L;
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static String objId(Document d) {
        Object id = d.get("_id");
        return id != null ? id.toString() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static List<ReturnedItem> safeItems(MaterialReturnRequest r) {
        return r.getItems() != null ? r.getItems() : List.of();
    }

    private static List<SttStockItem> safeStockItems(SttStock stock) {
        return stock.getItems() != null ? stock.getItems() : List.of();
    }

    private static List<ConsumedEquipmentItem> safeConsumed(CaseStockConsumption c) {
        return c.getConsumedItems() != null ? c.getConsumedItems() : List.of();
    }
}

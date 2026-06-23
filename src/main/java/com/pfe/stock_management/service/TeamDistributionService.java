package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.UserService;
import com.pfe.stock_management.dto.TeamDistributionDto;
import com.pfe.stock_management.entity.CaseStockConsumption;
import com.pfe.stock_management.entity.DpmAllocation;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.enums.DistributionStatus;
import com.pfe.stock_management.model.ConsumedEquipmentItem;
import com.pfe.stock_management.model.DistributionItem;
import com.pfe.stock_management.model.TechStockItem;
import com.pfe.stock_management.repository.CaseStockConsumptionRepository;
import com.pfe.stock_management.repository.DpmAllocationRepository;
import com.pfe.stock_management.repository.SttDistributionRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only, role-scoped team distribution tracking. Surfaces every STT→TECH
 * distribution — including new pooled distributions with a null {@code dpmId} —
 * enriched with usage/remaining (FIFO drawdown against the TECH's aggregated
 * stock), the DPM source breakdown, related cases and a derived lifecycle status.
 */
@Service
@RequiredArgsConstructor
public class TeamDistributionService {

    private final SttDistributionRepository distributionRepository;
    private final DpmAllocationRepository allocationRepository;
    private final TechStockRepository techStockRepository;
    private final CaseStockConsumptionRepository consumptionRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /** Filter bag for the team distribution query. All fields optional. */
    public record TeamDistributionFilter(
            String zmId,
            String sttId,
            String techId,
            String equipmentId,
            Instant from,
            Instant to,
            String status
    ) {}

    /**
     * @param scopedSttIds STT ids the caller is allowed to see; {@code null} = unrestricted (ADMIN).
     */
    public List<TeamDistributionDto> getTeamDistributions(List<String> scopedSttIds,
                                                          TeamDistributionFilter filter) {
        TeamDistributionFilter f = filter != null
                ? filter : new TeamDistributionFilter(null, null, null, null, null, null, null);

        // ── Effective STT scope = caller scope ∩ optional ZM filter ∩ optional STT filter
        Set<String> effectiveScope = resolveEffectiveScope(scopedSttIds, f);

        List<SttDistribution> all = (scopedSttIds == null && effectiveScope == null)
                ? distributionRepository.findAll()
                : distributionRepository.findBySttIdInOrderByDistributedAtDesc(
                        new ArrayList<>(effectiveScope != null ? effectiveScope : Set.of()));

        // Display set after scope + simple field/date filters (equipment & status applied later).
        List<SttDistribution> display = all.stream()
                .filter(d -> effectiveScope == null || effectiveScope.contains(d.getSttId()))
                .filter(d -> f.techId() == null || f.techId().equals(d.getTechId()))
                .filter(d -> withinRange(d.getDistributedAt(), f.from(), f.to()))
                .sorted(Comparator.comparing(SttDistribution::getDistributedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());

        if (display.isEmpty()) return List.of();

        Set<String> techIds = display.stream().map(SttDistribution::getTechId)
                .filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
        Set<String> sttIds = display.stream().map(SttDistribution::getSttId)
                .filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
        List<String> displayIds = display.stream().map(SttDistribution::getId).collect(Collectors.toList());

        // FIFO used per (distributionId, equipmentId) computed over each TECH's full history.
        Map<String, Map<String, Integer>> usedPerDist = computeUsedPerDistribution(techIds);

        // Allocations (source breakdown) for the displayed distributions.
        Map<String, List<DpmAllocation>> allocationsByDist = displayIds.isEmpty()
                ? Map.of()
                : allocationRepository.findByDistributionIdIn(displayIds).stream()
                    .collect(Collectors.groupingBy(DpmAllocation::getDistributionId));

        // Consumptions per TECH (for related cases).
        Map<String, List<CaseStockConsumption>> consumptionsByTech = techIds.isEmpty()
                ? Map.of()
                : consumptionRepository.findByTechnicianIdInOrderByConsumedAtDesc(new ArrayList<>(techIds))
                    .stream().collect(Collectors.groupingBy(CaseStockConsumption::getTechnicianId));

        // ZM display resolution: STT → assignedZmId → ZM name.
        Map<String, AppUser> sttUsers = loadUsers(sttIds);
        Set<String> zmIds = sttUsers.values().stream()
                .map(AppUser::getAssignedZmId).filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, AppUser> zmUsers = loadUsers(zmIds);

        List<TeamDistributionDto> rows = new ArrayList<>();
        for (SttDistribution d : display) {
            TeamDistributionDto dto = toDto(d, usedPerDist.getOrDefault(d.getId(), Map.of()),
                    allocationsByDist.getOrDefault(d.getId(), List.of()),
                    consumptionsByTech.getOrDefault(d.getTechId(), List.of()),
                    sttUsers, zmUsers);

            // equipment filter: keep rows that contain the requested equipment
            if (f.equipmentId() != null && dto.getItems().stream()
                    .noneMatch(i -> f.equipmentId().equals(i.getEquipmentId()))) {
                continue;
            }
            // status filter: match the derived lifecycle status
            if (f.status() != null && !f.status().isBlank()
                    && !f.status().equalsIgnoreCase(dto.getDerivedStatus())) {
                continue;
            }
            rows.add(dto);
        }
        return rows;
    }

    // ── Scope resolution ──────────────────────────────────────────────────────

    private Set<String> resolveEffectiveScope(List<String> scopedSttIds, TeamDistributionFilter f) {
        Set<String> scope = scopedSttIds != null ? new HashSet<>(scopedSttIds) : null;

        if (f.zmId() != null && !f.zmId().isBlank()) {
            Set<String> zmStts = userRepository.findById(f.zmId())
                    .map(userService::findResponsibleSttIds)
                    .map(HashSet::new)
                    .orElseGet(HashSet::new);
            scope = (scope == null) ? zmStts : intersect(scope, zmStts);
        }
        if (f.sttId() != null && !f.sttId().isBlank()) {
            Set<String> single = Set.of(f.sttId());
            scope = (scope == null) ? new HashSet<>(single) : intersect(scope, single);
        }
        return scope;
    }

    private Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> r = new HashSet<>(a);
        r.retainAll(b);
        return r;
    }

    // ── FIFO usage attribution over a TECH's confirmed distributions ──────────

    private Map<String, Map<String, Integer>> computeUsedPerDistribution(Set<String> techIds) {
        Map<String, Map<String, Integer>> usedPerDist = new HashMap<>();

        for (String techId : techIds) {
            List<SttDistribution> techDists =
                    distributionRepository.findByTechIdOrderByDistributedAtDesc(techId);

            // Oldest-first, confirmed only (only confirmed receipts are in TechStock).
            List<SttDistribution> confirmedAsc = techDists.stream()
                    .filter(d -> d.getStatus() == DistributionStatus.CONFIRMED_BY_TECH)
                    .sorted(Comparator.comparing(SttDistribution::getDistributedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            Map<String, Integer> usedByEquip = techUsedByEquipment(techId);

            for (Map.Entry<String, Integer> equip : usedByEquip.entrySet()) {
                String equipmentId = equip.getKey();
                int remainingUsed = Math.max(0, equip.getValue());
                for (SttDistribution d : confirmedAsc) {
                    if (remainingUsed <= 0) break;
                    if (d.getItems() == null) continue;
                    for (DistributionItem item : d.getItems()) {
                        if (!equipmentId.equals(item.getEquipmentId())) continue;
                        int take = Math.min(remainingUsed, item.getQuantity());
                        if (take <= 0) continue;
                        usedPerDist.computeIfAbsent(d.getId(), k -> new HashMap<>())
                                .merge(equipmentId, take, Integer::sum);
                        remainingUsed -= take;
                    }
                }
            }
        }
        return usedPerDist;
    }

    private Map<String, Integer> techUsedByEquipment(String techId) {
        Optional<TechStock> stock = techStockRepository.findByTechId(techId);
        if (stock.isEmpty() || stock.get().getItems() == null) return Map.of();
        Map<String, Integer> used = new HashMap<>();
        for (TechStockItem item : stock.get().getItems()) {
            if (item.getEquipmentId() != null) {
                used.merge(item.getEquipmentId(), item.getUsedQuantity(), Integer::sum);
            }
        }
        return used;
    }

    // ── DTO assembly ──────────────────────────────────────────────────────────

    private TeamDistributionDto toDto(SttDistribution d,
                                      Map<String, Integer> usedByEquip,
                                      List<DpmAllocation> allocations,
                                      List<CaseStockConsumption> techConsumptions,
                                      Map<String, AppUser> sttUsers,
                                      Map<String, AppUser> zmUsers) {

        boolean confirmed = d.getStatus() == DistributionStatus.CONFIRMED_BY_TECH;

        // Source breakdown grouped by equipment then DPM.
        Map<String, Map<String, TeamDistributionDto.SourceAllocation>> srcByEquip = new HashMap<>();
        for (DpmAllocation a : allocations) {
            var perDpm = srcByEquip.computeIfAbsent(a.getEquipmentId(), k -> new LinkedHashMap<>());
            perDpm.merge(a.getDpmId(),
                    TeamDistributionDto.SourceAllocation.builder()
                            .dpmId(a.getDpmId())
                            .dpmNumber(a.getDpmNumber())
                            .allocatedQuantity(a.getAllocatedQuantity())
                            .build(),
                    (existing, incoming) -> {
                        existing.setAllocatedQuantity(
                                existing.getAllocatedQuantity() + incoming.getAllocatedQuantity());
                        return existing;
                    });
        }

        Set<String> equipmentIds = new HashSet<>();
        List<TeamDistributionDto.Item> items = new ArrayList<>();
        int totalDistributed = 0, totalUsed = 0, totalRemaining = 0;

        if (d.getItems() != null) {
            for (DistributionItem item : d.getItems()) {
                equipmentIds.add(item.getEquipmentId());
                int distributed = item.getQuantity();
                int used = confirmed ? usedByEquip.getOrDefault(item.getEquipmentId(), 0) : 0;
                int remaining = Math.max(0, distributed - used);

                List<TeamDistributionDto.SourceAllocation> sources = new ArrayList<>(
                        srcByEquip.getOrDefault(item.getEquipmentId(), Map.of()).values());

                items.add(TeamDistributionDto.Item.builder()
                        .equipmentId(item.getEquipmentId())
                        .equipmentName(item.getEquipmentName())
                        .unit(item.getUnit())
                        .distributedQuantity(distributed)
                        .usedQuantity(used)
                        .remainingQuantity(remaining)
                        .sources(sources)
                        .build());

                totalDistributed += distributed;
                totalUsed += used;
                totalRemaining += remaining;
            }
        }

        boolean historical = isBlank(d.getDpmId()) && allocations.isEmpty();

        AppUser sttUser = sttUsers.get(d.getSttId());
        String zmId = sttUser != null ? sttUser.getAssignedZmId() : null;
        String zmName = zmId != null && zmUsers.get(zmId) != null ? zmUsers.get(zmId).getFullName() : null;

        return TeamDistributionDto.builder()
                .distributionId(d.getId())
                .sttId(d.getSttId())
                .sttName(d.getSttName())
                .techId(d.getTechId())
                .techName(d.getTechName())
                .zmId(zmId)
                .zmName(zmName)
                .distributedAt(d.getDistributedAt())
                .confirmedAt(d.getConfirmedAt())
                .notes(d.getNotes())
                .status(d.getStatus())
                .derivedStatus(deriveStatus(d.getStatus(), totalDistributed, totalUsed))
                .historical(historical)
                .items(items)
                .totalDistributed(totalDistributed)
                .totalUsed(totalUsed)
                .totalRemaining(totalRemaining)
                .relatedCases(relatedCases(techConsumptions, equipmentIds))
                .build();
    }

    private String deriveStatus(DistributionStatus status, int totalDistributed, int totalUsed) {
        if (status == DistributionStatus.CANCELLED) return "CANCELLED";
        if (status == DistributionStatus.PENDING_TECH_CONFIRMATION) return "PENDING";
        // CONFIRMED_BY_TECH
        if (totalUsed <= 0) return "CONFIRMED";
        if (totalUsed >= totalDistributed) return "FULLY_USED";
        return "PARTIALLY_USED";
    }

    private List<TeamDistributionDto.RelatedCase> relatedCases(
            List<CaseStockConsumption> techConsumptions, Set<String> equipmentIds) {
        if (techConsumptions == null || techConsumptions.isEmpty()) return List.of();
        Map<String, TeamDistributionDto.RelatedCase> byCase = new LinkedHashMap<>();
        for (CaseStockConsumption c : techConsumptions) {
            if (c.getConsumedItems() == null) continue;
            boolean touchesEquipment = c.getConsumedItems().stream()
                    .map(ConsumedEquipmentItem::getEquipmentId)
                    .anyMatch(equipmentIds::contains);
            if (!touchesEquipment) continue;
            byCase.putIfAbsent(c.getCaseId(), TeamDistributionDto.RelatedCase.builder()
                    .caseId(c.getCaseId())
                    .crmCase(c.getCrmCase())
                    .caseType(c.getCaseType() != null ? c.getCaseType().name() : null)
                    .consumedAt(c.getConsumedAt())
                    .build());
        }
        return new ArrayList<>(byCase.values());
    }

    private Map<String, AppUser> loadUsers(Set<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<String, AppUser> map = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private boolean withinRange(Instant value, Instant from, Instant to) {
        if (from != null && (value == null || value.isBefore(from))) return false;
        if (to != null && (value == null || value.isAfter(to))) return false;
        return true;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

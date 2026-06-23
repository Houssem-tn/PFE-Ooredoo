package com.pfe.stock_management.service;

import com.pfe.stock_management.dto.DpmAllocationDto;
import com.pfe.stock_management.dto.DpmTechRollupDto;
import com.pfe.stock_management.entity.DpmAllocation;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.DpmSourceBalance;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.model.DistributionItem;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.TechStockItem;
import com.pfe.stock_management.repository.DpmAllocationRepository;
import com.pfe.stock_management.repository.DpmSourceBalanceRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns DPM source traceability for the pooled-stock distribution workflow:
 * <ol>
 *   <li>creates a per-confirmed-DPM-item {@link DpmSourceBalance} at DPM reception,</li>
 *   <li>automatically allocates each STT→TECH distribution back to those balances
 *       (FIFO by DPM confirmation date) using atomic conditional decrements so a
 *       source quantity can never be double-allocated,</li>
 *   <li>rebuilds per-TECH distributed/used/remaining rollups for the DPM detail page.</li>
 * </ol>
 * The STT never selects a DPM — allocation is entirely backend-driven.
 */
@Service
@RequiredArgsConstructor
public class DpmAllocationService {

    private static final Logger log = LoggerFactory.getLogger(DpmAllocationService.class);

    private final DpmSourceBalanceRepository balanceRepository;
    private final DpmAllocationRepository allocationRepository;
    private final TechStockRepository techStockRepository;
    private final MongoTemplate mongoTemplate;

    // ── 1. Source balance creation (called at DPM confirmation) ───────────────

    /**
     * Materialises one {@link DpmSourceBalance} per confirmed DPM item. Idempotent:
     * if balances already exist for this DPM it does nothing, so re-confirmation or
     * retries never inflate the source pool.
     */
    public void createSourceBalances(DpmRequest dpm) {
        if (dpm == null || dpm.getId() == null || dpm.getSttId() == null) return;
        if (balanceRepository.existsByDpmId(dpm.getId())) return;

        Instant confirmedAt = dpm.getConfirmedAt() != null ? dpm.getConfirmedAt() : Instant.now();
        Instant now = Instant.now();

        List<DpmSourceBalance> balances = new ArrayList<>();
        for (DpmItem item : dpm.getItems()) {
            Integer confirmed = item.getConfirmedQuantity();
            if (confirmed == null || confirmed <= 0) continue;
            balances.add(DpmSourceBalance.builder()
                    .dpmId(dpm.getId())
                    .dpmNumber(dpm.getDpmNumber())
                    .sttId(dpm.getSttId())
                    .zmId(dpm.getZmId())
                    .zmName(dpm.getZmName())
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .unit(item.getUnit())
                    .confirmedQuantity(confirmed)
                    .allocatedQuantity(0)
                    .remainingQuantity(confirmed)
                    .confirmedAt(confirmedAt)
                    .createdAt(now)
                    .build());
        }
        if (!balances.isEmpty()) {
            balanceRepository.saveAll(balances);
            log.info("dpm_source_balances_created dpmId={} count={}", dpm.getId(), balances.size());
        }
    }

    // ── 2. FIFO allocation (called after a distribution is recorded) ──────────

    /**
     * Allocates the just-recorded distribution back to confirmed DPM sources, FIFO
     * by DPM confirmation date, within the STT's own pool. Best-effort and fully
     * defensive: any shortfall (e.g. stock received before this feature existed) is
     * simply left unattributed — it is never forced onto a wrong DPM, and a failure
     * here never breaks the distribution workflow.
     */
    public void allocateForDistribution(SttDistribution distribution) {
        if (distribution == null || distribution.getId() == null) return;
        try {
            Instant now = Instant.now();
            for (DistributionItem item : distribution.getItems()) {
                int need = item.getQuantity();
                if (need <= 0) continue;
                need = allocateItem(distribution, item, need, now);
                if (need > 0) {
                    log.info("dpm_allocation_unattributed distributionId={} equipmentId={} qty={}",
                            distribution.getId(), item.getEquipmentId(), need);
                }
            }
        } catch (Exception ex) {
            log.error("dpm_allocation_failed distributionId={} : {}",
                    distribution.getId(), ex.getMessage(), ex);
        }
    }

    /** Returns the quantity left unattributed after FIFO allocation. */
    private int allocateItem(SttDistribution distribution, DistributionItem item, int need, Instant now) {
        int guard = 0;
        while (need > 0 && guard < 500) {
            guard++;
            List<DpmSourceBalance> sources = balanceRepository
                    .findBySttIdAndEquipmentIdAndRemainingQuantityGreaterThanOrderByConfirmedAtAsc(
                            distribution.getSttId(), item.getEquipmentId(), 0);
            if (sources.isEmpty()) break;

            boolean progressed = false;
            for (DpmSourceBalance balance : sources) {
                if (need <= 0) break;
                int take = Math.min(need, balance.getRemainingQuantity());
                if (take <= 0) continue;

                // Atomic conditional decrement: only succeeds if the source still has >= take.
                Query query = new Query(Criteria.where("_id").is(balance.getId())
                        .and("remainingQuantity").gte(take));
                Update update = new Update()
                        .inc("remainingQuantity", -take)
                        .inc("allocatedQuantity", take)
                        .set("lastAllocatedAt", now);
                UpdateResult result = mongoTemplate.updateFirst(query, update, DpmSourceBalance.class);

                if (result.getModifiedCount() == 1) {
                    int before = balance.getRemainingQuantity();
                    allocationRepository.save(DpmAllocation.builder()
                            .distributionId(distribution.getId())
                            .dpmId(balance.getDpmId())
                            .dpmNumber(balance.getDpmNumber())
                            .sttId(distribution.getSttId())
                            .technicianId(distribution.getTechId())
                            .technicianName(distribution.getTechName())
                            .equipmentId(item.getEquipmentId())
                            .equipmentName(item.getEquipmentName())
                            .unit(item.getUnit())
                            .allocatedQuantity(take)
                            .sourceBalanceBefore(before)
                            .sourceBalanceAfter(before - take)
                            .allocatedAt(now)
                            .build());
                    need -= take;
                    progressed = true;
                }
                // else: another thread won the race for this source — refetch and retry.
            }
            if (!progressed) {
                // Contention with no progress in a full pass; refetched view will reflect it.
                // The guard bound prevents an unbounded spin.
                if (guard > 50) break;
            }
        }
        return need;
    }

    // ── 3. DPM detail enrichment ──────────────────────────────────────────────

    public List<DpmAllocationDto> getAllocationDtosForDpm(String dpmId,
                                                          Map<String, SttDistribution> distributionsById) {
        return allocationRepository.findByDpmIdOrderByAllocatedAtAsc(dpmId).stream()
                .map(a -> toDto(a, distributionsById))
                .collect(Collectors.toList());
    }

    public List<DpmAllocation> getAllocationsForDpm(String dpmId) {
        return allocationRepository.findByDpmIdOrderByAllocatedAtAsc(dpmId);
    }

    /**
     * Per-TECH rollup for a DPM. {@code used} is attributed by drawing the TECH's
     * aggregated used quantity (from {@link TechStock}) down across that TECH's
     * allocations FIFO; only the portion landing on this DPM's allocations is counted,
     * so the figures reconcile with real consumption without over-attributing.
     */
    public List<DpmTechRollupDto> getTechRollupsForDpm(String dpmId) {
        List<DpmAllocation> dpmAllocations = allocationRepository.findByDpmIdOrderByAllocatedAtAsc(dpmId);
        if (dpmAllocations.isEmpty()) return List.of();

        Set<String> techIds = dpmAllocations.stream()
                .map(DpmAllocation::getTechnicianId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        // All allocations for the involved techs (across every DPM) → correct FIFO drawdown.
        List<DpmAllocation> allForTechs = techIds.isEmpty()
                ? List.of() : allocationRepository.findByTechnicianIdIn(techIds);

        // techId → equipmentId → total used (from aggregated TechStock)
        Map<String, Map<String, Integer>> usedByTechEquip = new HashMap<>();
        for (String techId : techIds) {
            usedByTechEquip.put(techId, techUsedByEquipment(techId));
        }

        // allocationId → used portion attributed to that allocation
        Map<String, Integer> usedPerAllocation =
                attributeUsageToAllocations(allForTechs, usedByTechEquip);

        // Build rollups from THIS dpm's allocations only.
        Map<String, DpmTechRollupDto> rollupByTech = new LinkedHashMap<>();
        Map<String, Map<String, DpmTechRollupDto.Item>> itemIndex = new HashMap<>();

        for (DpmAllocation a : dpmAllocations) {
            DpmTechRollupDto rollup = rollupByTech.computeIfAbsent(a.getTechnicianId(), tid ->
                    DpmTechRollupDto.builder()
                            .technicianId(tid)
                            .technicianName(a.getTechnicianName())
                            .items(new ArrayList<>())
                            .build());
            Map<String, DpmTechRollupDto.Item> perEquip =
                    itemIndex.computeIfAbsent(a.getTechnicianId(), k -> new LinkedHashMap<>());

            DpmTechRollupDto.Item item = perEquip.get(a.getEquipmentId());
            if (item == null) {
                item = DpmTechRollupDto.Item.builder()
                        .equipmentId(a.getEquipmentId())
                        .equipmentName(a.getEquipmentName())
                        .unit(a.getUnit())
                        .build();
                perEquip.put(a.getEquipmentId(), item);
                rollup.getItems().add(item);
            }

            int used = usedPerAllocation.getOrDefault(a.getId(), 0);
            item.setDistributedQuantity(item.getDistributedQuantity() + a.getAllocatedQuantity());
            item.setUsedQuantity(item.getUsedQuantity() + used);
            // returnedQuantity stays 0 for now — return→source linkage is a future chain step.
            item.setRemainingQuantity(item.getDistributedQuantity()
                    - item.getUsedQuantity() - item.getReturnedQuantity());
        }

        // Totals
        List<DpmTechRollupDto> result = new ArrayList<>(rollupByTech.values());
        for (DpmTechRollupDto r : result) {
            int td = 0, tu = 0, tr = 0, trem = 0;
            for (DpmTechRollupDto.Item it : r.getItems()) {
                td += it.getDistributedQuantity();
                tu += it.getUsedQuantity();
                tr += it.getReturnedQuantity();
                trem += it.getRemainingQuantity();
            }
            r.setTotalDistributed(td);
            r.setTotalUsed(tu);
            r.setTotalReturned(tr);
            r.setTotalRemaining(trem);
        }
        return result;
    }

    // ── Shared attribution utility ────────────────────────────────────────────

    /**
     * Distributes each TECH's aggregated used quantity across that TECH's allocations,
     * oldest allocation first (FIFO), per equipment. Returns allocationId → used.
     * Used by both the DPM rollup and (indirectly) reconciles with TechStock totals.
     */
    public Map<String, Integer> attributeUsageToAllocations(
            List<DpmAllocation> allocations,
            Map<String, Map<String, Integer>> usedByTechEquip) {

        Map<String, Integer> usedPerAllocation = new HashMap<>();

        // group allocations by tech + equipment, ordered by allocatedAt asc
        Map<String, Map<String, List<DpmAllocation>>> grouped = new HashMap<>();
        for (DpmAllocation a : allocations) {
            grouped.computeIfAbsent(a.getTechnicianId(), k -> new HashMap<>())
                    .computeIfAbsent(a.getEquipmentId(), k -> new ArrayList<>())
                    .add(a);
        }

        for (Map.Entry<String, Map<String, List<DpmAllocation>>> techEntry : grouped.entrySet()) {
            String techId = techEntry.getKey();
            Map<String, Integer> equipUsed = usedByTechEquip.getOrDefault(techId, Map.of());
            for (Map.Entry<String, List<DpmAllocation>> equipEntry : techEntry.getValue().entrySet()) {
                List<DpmAllocation> ordered = equipEntry.getValue();
                ordered.sort(Comparator.comparing(
                        DpmAllocation::getAllocatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
                int remainingUsed = equipUsed.getOrDefault(equipEntry.getKey(), 0);
                for (DpmAllocation a : ordered) {
                    int u = Math.min(Math.max(remainingUsed, 0), a.getAllocatedQuantity());
                    usedPerAllocation.put(a.getId(), u);
                    remainingUsed -= u;
                }
            }
        }
        return usedPerAllocation;
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

    private DpmAllocationDto toDto(DpmAllocation a, Map<String, SttDistribution> distributionsById) {
        SttDistribution dist = distributionsById != null ? distributionsById.get(a.getDistributionId()) : null;
        return DpmAllocationDto.builder()
                .id(a.getId())
                .distributionId(a.getDistributionId())
                .dpmId(a.getDpmId())
                .dpmNumber(a.getDpmNumber())
                .sttId(a.getSttId())
                .technicianId(a.getTechnicianId())
                .technicianName(a.getTechnicianName())
                .equipmentId(a.getEquipmentId())
                .equipmentName(a.getEquipmentName())
                .unit(a.getUnit())
                .allocatedQuantity(a.getAllocatedQuantity())
                .sourceBalanceBefore(a.getSourceBalanceBefore())
                .sourceBalanceAfter(a.getSourceBalanceAfter())
                .allocatedAt(a.getAllocatedAt())
                .distributionStatus(dist != null && dist.getStatus() != null ? dist.getStatus().name() : null)
                .build();
    }
}

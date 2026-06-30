package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.dto.CaseConsumptionDto;
import com.pfe.stock_management.dto.CaseConsumptionSummaryDto;
import com.pfe.stock_management.entity.CaseStockConsumption;
import com.pfe.stock_management.model.ConsumedEquipmentItem;
import com.pfe.stock_management.repository.CaseStockConsumptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseStockConsumptionService {

    private final CaseStockConsumptionRepository repository;

    public CaseStockConsumption createOrUpdate(
            CaseEntity caseEntity,
            AppUser technician,
            List<ConsumedEquipmentItem> consumedItems,
            String performedBy
    ) {
        return createOrUpdate(caseEntity, technician, consumedItems, performedBy, null);
    }

    public CaseStockConsumption createOrUpdate(
            CaseEntity caseEntity,
            AppUser technician,
            List<ConsumedEquipmentItem> consumedItems,
            String performedBy,
            String usageContext
    ) {
        CaseStockConsumption record = repository.findByCaseId(caseEntity.getId())
                .orElseGet(() -> CaseStockConsumption.builder()
                        .caseId(caseEntity.getId())
                        .createdBy(performedBy)
                        .build());

        record.setCrmCase(caseEntity.getCrmCase());
        record.setCaseType(caseEntity.getType());
        if (usageContext != null) {
            record.setUsageContext(usageContext);
        }
        record.setTechnicianId(technician.getId());
        record.setTechnicianName(technician.getFullName());
        record.setSttId(caseEntity.getSttId());
        record.setSttName(caseEntity.getSttName());
        record.setZmId(caseEntity.getZmId());
        record.setZmName(caseEntity.getZmName());
        record.setConsumedItems(consumedItems);
        record.setConsumedAt(Instant.now());

        return repository.save(record);
    }

    public List<CaseConsumptionDto> getAll(
            String caseType,
            String crmCase,
            String zmId,
            String sttId,
            String technicianId,
            String equipmentId,
            Instant from,
            Instant to
    ) {
        List<CaseStockConsumption> records = repository.findAllByOrderByConsumedAtDesc();
        return records.stream()
                .filter(r -> caseType == null || (r.getCaseType() != null && r.getCaseType().name().equalsIgnoreCase(caseType)))
                .filter(r -> crmCase == null || (r.getCrmCase() != null && r.getCrmCase().equalsIgnoreCase(crmCase)))
                .filter(r -> zmId == null || zmId.equals(r.getZmId()))
                .filter(r -> sttId == null || sttId.equals(r.getSttId()))
                .filter(r -> technicianId == null || technicianId.equals(r.getTechnicianId()))
                .filter(r -> equipmentId == null || (r.getConsumedItems() != null
                        && r.getConsumedItems().stream().anyMatch(i -> equipmentId.equals(i.getEquipmentId()))))
                .filter(r -> from == null || (r.getConsumedAt() != null && !r.getConsumedAt().isBefore(from)))
                .filter(r -> to == null || (r.getConsumedAt() != null && !r.getConsumedAt().isAfter(to)))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<CaseConsumptionDto> getForTech(String technicianId) {
        return repository.findByTechnicianIdOrderByConsumedAtDesc(technicianId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<CaseConsumptionDto> getForStt(String sttId) {
        return repository.findBySttIdOrderByConsumedAtDesc(sttId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<CaseConsumptionDto> getForZm(String zmId) {
        return repository.findByZmIdOrderByConsumedAtDesc(zmId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public CaseConsumptionDto getById(String id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    public CaseConsumptionDto getByCaseId(String caseId) {
        return repository.findByCaseId(caseId)
                .map(this::toDto)
                .orElse(null);
    }

    public CaseConsumptionSummaryDto getSummary() {
        List<CaseStockConsumption> all = repository.findAllByOrderByConsumedAtDesc();

        int totalConsumptions = all.size();
        int totalQuantityConsumed = 0;
        int cableTotalMeters = 0;
        int totalBoxesUsed = 0;
        int totalItemTypes = 0;

        Map<String, Long> techCounts = all.stream()
                .filter(r -> r.getTechnicianId() != null)
                .collect(Collectors.groupingBy(CaseStockConsumption::getTechnicianId, Collectors.counting()));

        for (CaseStockConsumption r : all) {
            if (r.getConsumedItems() == null) continue;
            totalItemTypes += r.getConsumedItems().size();
            for (ConsumedEquipmentItem item : r.getConsumedItems()) {
                totalQuantityConsumed += item.getQuantityUsed();
                if ("METERS".equalsIgnoreCase(item.getUnit())) {
                    cableTotalMeters += item.getQuantityUsed();
                } else {
                    totalBoxesUsed += item.getQuantityUsed();
                }
            }
        }

        String topTechId = null;
        String topTechName = null;
        int topTechConsumptions = 0;
        if (!techCounts.isEmpty()) {
            Map.Entry<String, Long> top = techCounts.entrySet().stream()
                    .max(Comparator.comparingLong(Map.Entry::getValue))
                    .orElse(null);
            if (top != null) {
                final String resolvedTopTechId = top.getKey();
                topTechId = resolvedTopTechId;
                topTechConsumptions = top.getValue().intValue();
                topTechName = all.stream()
                        .filter(r -> resolvedTopTechId.equals(r.getTechnicianId()))
                        .map(CaseStockConsumption::getTechnicianName)
                        .filter(n -> n != null && !n.isBlank())
                        .findFirst()
                        .orElse(resolvedTopTechId);
            }
        }

        return CaseConsumptionSummaryDto.builder()
                .totalConsumptions(totalConsumptions)
                .totalItemTypes(totalItemTypes)
                .totalQuantityConsumed(totalQuantityConsumed)
                .cableTotalMeters(cableTotalMeters)
                .totalBoxesUsed(totalBoxesUsed)
                .topTechnicianId(topTechId)
                .topTechnicianName(topTechName)
                .topTechnicianConsumptions(topTechConsumptions)
                .casesWithoutStockDeclaration(0)
                .build();
    }

    private CaseConsumptionDto toDto(CaseStockConsumption r) {
        int totalQty = 0;
        if (r.getConsumedItems() != null) {
            totalQty = r.getConsumedItems().stream().mapToInt(ConsumedEquipmentItem::getQuantityUsed).sum();
        }
        return CaseConsumptionDto.builder()
                .id(r.getId())
                .caseId(r.getCaseId())
                .crmCase(r.getCrmCase())
                .caseType(r.getCaseType())
                .usageContext(r.getUsageContext())
                .technicianId(r.getTechnicianId())
                .technicianName(r.getTechnicianName())
                .sttId(r.getSttId())
                .sttName(r.getSttName())
                .zmId(r.getZmId())
                .zmName(r.getZmName())
                .consumedItems(r.getConsumedItems())
                .totalQuantityUsed(totalQty)
                .totalItemTypes(r.getConsumedItems() != null ? r.getConsumedItems().size() : 0)
                .consumedAt(r.getConsumedAt())
                .notes(r.getNotes())
                .build();
    }
}

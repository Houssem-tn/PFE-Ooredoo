package com.pfe.stock_management.service;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import com.pfe.Service.NotificationEventService;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.stock_management.dto.DeclareTechUsageRequest;
import com.pfe.stock_management.dto.DeclareTechUsageResponse;
import com.pfe.stock_management.dto.DistributableDpmDto;
import com.pfe.stock_management.dto.DistributableDpmItemDto;
import com.pfe.stock_management.dto.DistributeToTechRequest;
import com.pfe.stock_management.dto.TechUsageItemRequest;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.entity.TechStockUsage;
import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.model.DistributionItem;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.model.TechStockItem;
import com.pfe.stock_management.model.UsageItem;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.SttDistributionRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import com.pfe.stock_management.repository.TechStockUsageRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SttStockService {

    private final SttStockRepository sttStockRepository;
    private final TechStockRepository techStockRepository;
    private final SttDistributionRepository distributionRepository;
    private final DpmRepository dpmRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final StockMovementService stockMovementService;
    private final NotificationEventService notificationEventService;
    private final TechStockUsageRepository techStockUsageRepository;

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

        List<DistributionItem> distributionItems = new ArrayList<>();

        for (var itemReq : request.getItems()) {
            SttStockItem si = stockMap.get(itemReq.getEquipmentId());
            if (si == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Equipment not in your stock: " + itemReq.getEquipmentId());
            }
            if (si.getAvailableQuantity() < itemReq.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock for " + si.getEquipmentName()
                                + ": available=" + si.getAvailableQuantity()
                                + ", requested=" + itemReq.getQuantity());
            }
            distributionItems.add(DistributionItem.builder()
                    .equipmentId(si.getEquipmentId())
                    .equipmentName(si.getEquipmentName())
                    .unit(si.getUnit())
                    .quantity(itemReq.getQuantity())
                    .build());
        }

        for (DistributionItem di : distributionItems) {
            SttStockItem si = stockMap.get(di.getEquipmentId());
            si.setTotalDistributedToTechs(si.getTotalDistributedToTechs() + di.getQuantity());
            si.setLastDistributedAt(Instant.now());
        }
        sttStock.setLastUpdatedAt(Instant.now());
        sttStockRepository.save(sttStock);

        updateTechStock(tech, stt.getId(), distributionItems);

        SttDistribution distribution = SttDistribution.builder()
                .sttId(stt.getId())
                .techId(tech.getId())
                .techName(tech.getFullName())
                .techEmail(tech.getEmail())
                .dpmId(request.getDpmId())
                .items(distributionItems)
                .distributedAt(Instant.now())
                .notes(request.getNotes())
                .distributedBy(callerEmail)
                .build();

        SttDistribution saved = distributionRepository.save(distribution);

        stockMovementService.recordDistribution(saved, callerEmail);
        notificationEventService.onStockDistributedToTech(saved);

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

    public DeclareTechUsageResponse declareUsage(AppUser tech, DeclareTechUsageRequest request) {
        CaseEntity caseEntity = caseRepository.findById(request.getCaseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));

        if (!tech.getId().equals(caseEntity.getTechId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This case is not assigned to you");
        }

        TechStock techStock = techStockRepository.findByTechId(tech.getId())
                .orElseGet(() -> TechStock.builder()
                        .techId(tech.getId())
                        .sttId(firstNonBlank(tech.getAssignedSttId(), caseEntity.getSttId()))
                        .items(new ArrayList<>())
                        .build());

        ensureTechStockItems(techStock);
        if (isBlank(techStock.getSttId())) {
            techStock.setSttId(firstNonBlank(tech.getAssignedSttId(), caseEntity.getSttId()));
        }

        Map<String, TechStockItem> techStockMap = techStock.getItems().stream()
                .collect(Collectors.toMap(TechStockItem::getEquipmentId, ti -> ti));

        List<String> warnings = new ArrayList<>();
        Instant declaredAt = Instant.now();
        List<UsageItem> usageItems = new ArrayList<>();

        for (UsageItem requestedItem : mergeUsageItems(request.getItems()).values()) {
            TechStockItem stockItem = techStockMap.get(requestedItem.getEquipmentId());
            if (stockItem == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Equipment is not part of your stock: " + requestedItem.getEquipmentId()
                );
            }

            int remainingBefore = stockItem.getRemainingQuantity();
            stockItem.setEquipmentName(firstNonBlank(stockItem.getEquipmentName(), requestedItem.getEquipmentName()));
            stockItem.setUnit(firstNonBlank(stockItem.getUnit(), requestedItem.getUnit()));
            stockItem.setUsedQuantity(stockItem.getUsedQuantity() + requestedItem.getQuantity());

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

        techStock.setLastUpdatedAt(declaredAt);
        TechStock savedStock = techStockRepository.save(techStock);

        TechStockUsage usage = techStockUsageRepository.save(TechStockUsage.builder()
                .techId(tech.getId())
                .caseId(caseEntity.getId())
                .caseReference(firstNonBlank(caseEntity.getCrmCase(), caseEntity.getId()))
                .items(usageItems)
                .declaredAt(declaredAt)
                .notes(trimToNull(request.getNotes()))
                .build());

        return DeclareTechUsageResponse.builder()
                .stock(savedStock)
                .usage(usage)
                .hasWarnings(!warnings.isEmpty())
                .warnings(warnings)
                .build();
    }

    public List<SttDistribution> getDistributionHistoryForStt(String sttId) {
        return distributionRepository.findBySttIdOrderByDistributedAtDesc(sttId);
    }

    public List<DistributableDpmDto> getDistributableDpms(String sttId) {
        List<DpmRequest> dpms = dpmRepository.findBySttIdOrderByCreatedAtDesc(sttId).stream()
                .filter(d -> d.getStatus() == DpmStatus.FULLY_CONFIRMED
                          || d.getStatus() == DpmStatus.PARTIALLY_CONFIRMED)
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

    private AppUser findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private void ensureTechStockItems(TechStock techStock) {
        if (techStock.getItems() == null) {
            techStock.setItems(new ArrayList<>());
        }
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

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "system";
    }
}

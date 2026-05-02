package com.pfe.stock_management.service;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import com.pfe.Service.NotificationEventService;
import com.pfe.stock_management.dto.DistributeToTechRequest;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.model.DistributionItem;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.model.TechStockItem;
import com.pfe.stock_management.repository.SttDistributionRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
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
    private final UserRepository userRepository;
    private final StockMovementService stockMovementService;
    private final NotificationEventService notificationEventService;

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
            } else {
                stock.getItems().add(SttStockItem.builder()
                        .equipmentId(item.getEquipmentId())
                        .equipmentName(item.getEquipmentName())
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
        if (stt.isSelfExecutor() && request.getTechId().equals(stt.getId())) {
            tech = stt;
        } else {
            tech = userRepository.findByIdAndRoleAndStatusAndEnabled(
                    request.getTechId(), UserRole.ROLE_TECH, AccountStatus.APPROVED, true)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "TECH not found or not active"));
            if (!stt.getId().equals(tech.getAssignedSttId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This TECH is not assigned to you");
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
        return techStockRepository.findByTechId(techId)
                .orElseGet(() -> TechStock.builder().techId(techId).items(new ArrayList<>()).build());
    }

    public List<TechStock> getTechStocksForStt(String sttId) {
        return techStockRepository.findBySttId(sttId);
    }

    public List<SttDistribution> getDistributionHistoryForStt(String sttId) {
        return distributionRepository.findBySttIdOrderByDistributedAtDesc(sttId);
    }

    public List<SttStock> getAllSttStocks() {
        return sttStockRepository.findAll();
    }

    public List<TechStock> getAllTechStocks() {
        return techStockRepository.findAll();
    }

    private void updateTechStock(AppUser tech, String sttId, List<DistributionItem> items) {
        TechStock techStock = techStockRepository.findByTechId(tech.getId())
                .orElseGet(() -> TechStock.builder()
                        .techId(tech.getId())
                        .sttId(sttId)
                        .items(new ArrayList<>())
                        .build());

        Map<String, TechStockItem> techStockMap = techStock.getItems().stream()
                .collect(Collectors.toMap(TechStockItem::getEquipmentId, ti -> ti));

        for (DistributionItem di : items) {
            TechStockItem ti = techStockMap.get(di.getEquipmentId());
            if (ti != null) {
                ti.setReceivedQuantity(ti.getReceivedQuantity() + di.getQuantity());
                ti.setLastReceivedAt(Instant.now());
            } else {
                techStock.getItems().add(TechStockItem.builder()
                        .equipmentId(di.getEquipmentId())
                        .equipmentName(di.getEquipmentName())
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

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "system";
    }
}

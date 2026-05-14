package com.pfe.stock_management.service;

import com.pfe.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.StockMovement;
import com.pfe.stock_management.enums.StockMovementType;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.DistributionItem;
import com.pfe.stock_management.model.UsageItem;
import com.pfe.stock_management.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository movementRepository;

    public void recordDpmCreated(DpmRequest dpm, String performedBy) {
        Instant now = Instant.now();
        for (DpmItem item : dpm.getItems()) {
            movementRepository.save(StockMovement.builder()
                    .movementType(StockMovementType.DPM_CREATED)
                    .fromActorId(dpm.getZmId())
                    .fromActorRole("ROLE_ZM")
                    .toActorId(dpm.getSttId())
                    .toActorRole("ROLE_STT")
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .quantity(item.getRequestedQuantity())
                    .referenceId(dpm.getId())
                    .createdAt(now)
                    .createdBy(performedBy)
                    .performedAt(now)
                    .performedBy(performedBy)
                    .build());
        }
    }

    public void recordDpmConfirmed(DpmRequest dpm, String performedBy) {
        Instant now = Instant.now();
        for (DpmItem item : dpm.getItems()) {
            if (item.getConfirmedQuantity() == null || item.getConfirmedQuantity() <= 0) continue;
            movementRepository.save(StockMovement.builder()
                    .movementType(StockMovementType.DPM_CONFIRMED)
                    .fromActorId(dpm.getZmId())
                    .fromActorRole("ROLE_ZM")
                    .toActorId(dpm.getSttId())
                    .toActorRole("ROLE_STT")
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .quantity(item.getConfirmedQuantity())
                    .referenceId(dpm.getId())
                    .createdAt(now)
                    .createdBy(performedBy)
                    .performedAt(now)
                    .performedBy(performedBy)
                    .build());
        }
    }

    public void recordDistribution(SttDistribution distribution, String performedBy) {
        Instant now = Instant.now();
        for (DistributionItem item : distribution.getItems()) {
            movementRepository.save(StockMovement.builder()
                    .movementType(StockMovementType.STT_TO_TECH_DISTRIBUTION)
                    .fromActorId(distribution.getSttId())
                    .fromActorRole("ROLE_STT")
                    .toActorId(distribution.getTechId())
                    .toActorRole("ROLE_TECH")
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .quantity(item.getQuantity())
                    .referenceId(distribution.getId())
                    .createdAt(now)
                    .createdBy(performedBy)
                    .performedAt(now)
                    .performedBy(performedBy)
                    .build());
        }
    }

    public void recordTechConfirmation(SttDistribution distribution, String performedBy) {
        Instant now = Instant.now();
        for (DistributionItem item : distribution.getItems()) {
            movementRepository.save(StockMovement.builder()
                    .movementType(StockMovementType.TECH_RECEIVED_FROM_STT)
                    .fromActorId(distribution.getSttId())
                    .fromActorRole("ROLE_STT")
                    .toActorId(distribution.getTechId())
                    .toActorRole("ROLE_TECH")
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .quantity(item.getQuantity())
                    .referenceId(distribution.getId())
                    .createdAt(now)
                    .createdBy(performedBy)
                    .performedAt(now)
                    .performedBy(performedBy)
                    .build());
        }
    }

    public void recordCaseUsage(CaseEntity caseEntity, AppUser technician, UsageItem item, int quantity, String performedBy) {
        if (caseEntity == null || technician == null || item == null || quantity <= 0) {
            return;
        }

        Instant now = Instant.now();
        movementRepository.save(StockMovement.builder()
                .movementType(StockMovementType.CASE_USAGE)
                .fromActorId(technician.getId())
                .fromActorRole("ROLE_TECH")
                .toActorId(caseEntity.getId())
                .toActorRole("CASE")
                .technicianId(technician.getId())
                .caseId(caseEntity.getId())
                .equipmentId(item.getEquipmentId())
                .equipmentName(item.getEquipmentName())
                .quantity(quantity)
                .referenceId(caseEntity.getId())
                .comment("Equipment used in case execution")
                .createdAt(now)
                .createdBy(performedBy)
                .performedAt(now)
                .performedBy(performedBy)
                .build());
    }

    public List<StockMovement> getAll() {
        return movementRepository.findAll();
    }

    public List<StockMovement> getByReference(String referenceId) {
        return movementRepository.findByReferenceIdOrderByPerformedAtDesc(referenceId);
    }

    public List<StockMovement> getByActor(String actorId) {
        return movementRepository.findByFromActorIdOrToActorIdOrderByPerformedAtDesc(actorId, actorId);
    }
}

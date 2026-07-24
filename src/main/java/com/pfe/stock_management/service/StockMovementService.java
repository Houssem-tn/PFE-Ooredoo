package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.StockMovement;
import com.pfe.stock_management.enums.StockMovementType;
import com.pfe.stock_management.material_return.MaterialReturnRequest;
import com.pfe.stock_management.material_return.ReturnDestination;
import com.pfe.stock_management.material_return.ReturnedItem;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.DistributionItem;
import com.pfe.stock_management.model.UsageItem;
import com.pfe.stock_management.repository.StockMovementRepository;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItem;
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
                    .distributorZmId(dpm.getZmId())
                    .distributorZmName(dpm.getZmName())
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

    public void recordMaterialReturn(MaterialReturnRequest req, String performedBy) {
        Instant now = Instant.now();
        for (ReturnedItem item : req.getItems()) {
            movementRepository.save(StockMovement.builder()
                    .movementType(StockMovementType.MATERIAL_RETURN_SUBMITTED)
                    .fromActorId(req.getSttId())
                    .fromActorRole("ROLE_STT")
                    .toActorId(req.getZmId())
                    .toActorRole("ROLE_ZM")
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .quantity(item.getQuantity())
                    .referenceId(req.getId())
                    .caseId(req.getRelatedCaseId())
                    .comment("Material return submitted: " + req.getReturnCode())
                    .createdAt(now)
                    .createdBy(performedBy)
                    .performedAt(now)
                    .performedBy(performedBy)
                    .build());
        }
    }

    public void recordMaterialReturnApproved(MaterialReturnRequest req, String performedBy) {
        Instant now = Instant.now();
        StockMovementType destinationType = resolveDestinationMovementType(req.getDestination());
        for (ReturnedItem item : req.getItems()) {
            movementRepository.save(StockMovement.builder()
                    .movementType(StockMovementType.MATERIAL_RETURN_APPROVED)
                    .fromActorId(req.getSttId())
                    .fromActorRole("ROLE_STT")
                    .toActorId(req.getZmId())
                    .toActorRole("ROLE_ZM")
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .quantity(item.getQuantity())
                    .referenceId(req.getId())
                    .caseId(req.getRelatedCaseId())
                    .comment("Material return approved: " + req.getReturnCode())
                    .createdAt(now)
                    .createdBy(performedBy)
                    .performedAt(now)
                    .performedBy(performedBy)
                    .build());
            movementRepository.save(StockMovement.builder()
                    .movementType(destinationType)
                    .fromActorId(req.getSttId())
                    .fromActorRole("ROLE_STT")
                    .toActorId(req.getDestination() != null ? req.getDestination().name() : "UNKNOWN")
                    .toActorRole("DESTINATION")
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .quantity(item.getQuantity())
                    .referenceId(req.getId())
                    .caseId(req.getRelatedCaseId())
                    .comment("Returned to " + req.getDestination())
                    .createdAt(now)
                    .createdBy(performedBy)
                    .performedAt(now)
                    .performedBy(performedBy)
                    .build());
        }
    }

    public void recordMaterialReturnRejected(MaterialReturnRequest req, String performedBy) {
        Instant now = Instant.now();
        for (ReturnedItem item : req.getItems()) {
            movementRepository.save(StockMovement.builder()
                    .movementType(StockMovementType.MATERIAL_RETURN_REJECTED)
                    .fromActorId(req.getSttId())
                    .fromActorRole("ROLE_STT")
                    .toActorId(req.getZmId())
                    .toActorRole("ROLE_ZM")
                    .equipmentId(item.getEquipmentId())
                    .equipmentName(item.getEquipmentName())
                    .quantity(item.getQuantity())
                    .referenceId(req.getId())
                    .caseId(req.getRelatedCaseId())
                    .comment("Material return rejected: " + req.getReturnCode()
                            + (req.getReviewComment() != null ? " — " + req.getReviewComment() : ""))
                    .createdAt(now)
                    .createdBy(performedBy)
                    .performedAt(now)
                    .performedBy(performedBy)
                    .build());
        }
    }

    /** Technician declares equipment retrieved from a client (PLT/RES Retrieved-Stock workflow). */
    public void recordRetrievedStockDeclared(RetrievedStockItem item, String performedBy) {
        if (item == null) {
            return;
        }
        Instant now = Instant.now();
        movementRepository.save(StockMovement.builder()
                .movementType(StockMovementType.RETRIEVED_STOCK_DECLARED)
                .fromActorId(item.getTechnicianId())
                .fromActorRole("ROLE_TECH")
                .toActorId(item.getZmId())
                .toActorRole("ROLE_ZM")
                .technicianId(item.getTechnicianId())
                .caseId(item.getCaseId())
                .equipmentId(item.getEquipmentId())
                .equipmentName(item.getEquipmentName())
                .quantity(item.getQuantity())
                .referenceId(item.getId())
                .comment("Retrieved from client (" + item.getDisposition() + ") — case " + item.getCrmCase())
                .createdAt(now)
                .createdBy(performedBy)
                .performedAt(now)
                .performedBy(performedBy)
                .build());
    }

    /** ZM confirms physical collection of a technician's retrieved-stock item. */
    public void recordRetrievedStockCollected(RetrievedStockItem item, String performedBy) {
        if (item == null) {
            return;
        }
        Instant now = Instant.now();
        movementRepository.save(StockMovement.builder()
                .movementType(StockMovementType.RETRIEVED_STOCK_COLLECTED)
                .fromActorId(item.getTechnicianId())
                .fromActorRole("ROLE_TECH")
                .toActorId(item.getCollectedByZmId())
                .toActorRole("ROLE_ZM")
                .technicianId(item.getTechnicianId())
                .caseId(item.getCaseId())
                .equipmentId(item.getEquipmentId())
                .equipmentName(item.getEquipmentName())
                .quantity(item.getQuantity())
                .referenceId(item.getId())
                .comment("Collected by ZM from technician retrieved stock — case " + item.getCrmCase())
                .createdAt(now)
                .createdBy(performedBy)
                .performedAt(now)
                .performedBy(performedBy)
                .build());
    }

    private StockMovementType resolveDestinationMovementType(ReturnDestination destination) {
        if (destination == null) return StockMovementType.RETURN_TO_USABLE_STOCK;
        return switch (destination) {
            case USABLE_STOCK -> StockMovementType.RETURN_TO_USABLE_STOCK;
            case DAMAGED_STOCK -> StockMovementType.RETURN_TO_DAMAGED_STOCK;
            case REPAIR_CENTER -> StockMovementType.RETURN_TO_REPAIR;
            case RECONDITIONING -> StockMovementType.RETURN_TO_RECONDITIONING;
            case CENTRAL_WAREHOUSE -> StockMovementType.RETURN_TO_CENTRAL_WAREHOUSE;
            case SCRAP -> StockMovementType.RETURN_TO_SCRAP;
        };
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

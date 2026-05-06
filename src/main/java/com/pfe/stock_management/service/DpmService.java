package com.pfe.stock_management.service;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import com.pfe.Service.NotificationEventService;
import com.pfe.stock_management.dto.ConfirmDpmRequest;
import com.pfe.stock_management.dto.CreateDpmRequest;
import com.pfe.stock_management.dto.DpmItemConfirmation;
import com.pfe.stock_management.dto.DpmTrackingDto;
import com.pfe.stock_management.dto.ZmDpmAuditItem;
import com.pfe.stock_management.dto.ZmStockAuditDto;
import com.pfe.stock_management.model.DistributionItem;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.enums.StockMovementType;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.DpmTimelineEvent;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttDistributionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DpmService {

    private final DpmRepository dpmRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final SttDistributionRepository distributionRepository;
    private final SttStockService sttStockService;
    private final StockMovementService stockMovementService;
    private final NotificationEventService notificationEventService;

    public DpmRequest createDpm(CreateDpmRequest request) {
        String callerEmail = currentUser();
        AppUser zm = findByEmail(callerEmail);

        if (!zm.isCanCreateDpm()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to create DPM requests");
        }

        AppUser stt = userRepository.findByIdAndRoleAndStatusAndEnabled(
                request.getSttId(), UserRole.ROLE_STT, AccountStatus.APPROVED, true)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "STT not found or not active"));

        List<DpmItem> items = buildDpmItems(request);
        String dpmNumber = generateDpmNumber();

        DpmRequest dpm = DpmRequest.builder()
                .dpmNumber(dpmNumber)
                .zmId(zm.getId())
                .zmName(zm.getFullName())
                .zmEmail(zm.getEmail())
                .sttId(stt.getId())
                .sttName(stt.getFullName())
                .sttEmail(stt.getEmail())
                .status(DpmStatus.PENDING_STT_CONFIRMATION)
                .items(items)
                .notes(request.getNotes())
                .deliveryPersonName(request.getDeliveryPersonName())
                .timeline(new ArrayList<>())
                .build();

        addTimelineEvent(dpm, "DPM_CREATED",
                "DPM " + dpmNumber + " created by ZM " + zm.getFullName(),
                callerEmail, "ROLE_ZM");

        DpmRequest saved = dpmRepository.save(dpm);

        stockMovementService.recordDpmCreated(saved, callerEmail);
        notificationEventService.onDpmCreated(saved);

        return saved;
    }

    public DpmRequest confirmDpm(String dpmId, ConfirmDpmRequest request) {
        String callerEmail = currentUser();
        AppUser stt = findByEmail(callerEmail);

        DpmRequest dpm = getById(dpmId);

        if (!dpm.getSttId().equals(stt.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the assigned STT for this DPM");
        }

        if (dpm.getStatus() != DpmStatus.PENDING_STT_CONFIRMATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "DPM is not pending confirmation, current status: " + dpm.getStatus());
        }

        Map<String, DpmItemConfirmation> confirmationMap = request.getItems().stream()
                .collect(Collectors.toMap(DpmItemConfirmation::getEquipmentId, Function.identity()));

        boolean allZero = true;
        boolean anyShortage = false;

        for (DpmItem item : dpm.getItems()) {
            DpmItemConfirmation conf = confirmationMap.get(item.getEquipmentId());
            if (conf == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Missing confirmation for equipment: " + item.getEquipmentId());
            }
            item.setConfirmedQuantity(conf.getConfirmedQuantity());
            item.setConfirmationNote(conf.getConfirmationNote());

            if (conf.getConfirmedQuantity() > 0) allZero = false;
            if (conf.getConfirmedQuantity() < item.getRequestedQuantity()) anyShortage = true;
        }

        if (allZero && request.getRejectReason() != null && !request.getRejectReason().isBlank()) {
            dpm.setStatus(DpmStatus.REJECTED);
            addTimelineEvent(dpm, "DPM_REJECTED",
                    "DPM rejected by STT: " + request.getRejectReason(),
                    callerEmail, "ROLE_STT");
            DpmRequest saved = dpmRepository.save(dpm);
            notificationEventService.onDpmRejected(saved);
            return saved;
        }

        dpm.setStatus(anyShortage ? DpmStatus.PARTIALLY_CONFIRMED : DpmStatus.FULLY_CONFIRMED);
        dpm.setConfirmedAt(Instant.now());

        String eventDesc = anyShortage
                ? "DPM partially confirmed by STT " + stt.getFullName()
                : "DPM fully confirmed by STT " + stt.getFullName();
        addTimelineEvent(dpm, "DPM_CONFIRMED", eventDesc, callerEmail, "ROLE_STT");

        DpmRequest saved = dpmRepository.save(dpm);

        sttStockService.applyDpmConfirmation(saved);
        stockMovementService.recordDpmConfirmed(saved, callerEmail);
        notificationEventService.onDpmConfirmed(saved);

        return saved;
    }

    public DpmRequest cancelDpm(String dpmId) {
        String callerEmail = currentUser();
        DpmRequest dpm = getById(dpmId);

        if (!dpm.getZmEmail().equalsIgnoreCase(callerEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the ZM who created this DPM can cancel it");
        }

        if (dpm.getStatus() != DpmStatus.PENDING_STT_CONFIRMATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING DPMs can be cancelled");
        }

        dpm.setStatus(DpmStatus.CANCELLED);
        addTimelineEvent(dpm, "DPM_CANCELLED", "DPM cancelled by ZM", callerEmail, "ROLE_ZM");
        return dpmRepository.save(dpm);
    }

    public DpmRequest getById(String id) {
        return dpmRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DPM not found: " + id));
    }

    public List<DpmRequest> getAllDpms() {
        return dpmRepository.findAll();
    }

    public List<DpmRequest> getDpmsForZm(String zmId) {
        return dpmRepository.findByZmIdOrderByCreatedAtDesc(zmId);
    }

    public List<DpmRequest> getIncomingDpmsForStt(String sttId) {
        return dpmRepository.findBySttIdAndStatusOrderByCreatedAtDesc(sttId, DpmStatus.PENDING_STT_CONFIRMATION);
    }

    public List<DpmRequest> getAllDpmsForStt(String sttId) {
        return dpmRepository.findBySttIdOrderByCreatedAtDesc(sttId);
    }

    public DpmTrackingDto getDpmTracking(String dpmId) {
        DpmRequest dpm = getById(dpmId);
        List<SttDistribution> distributions =
                distributionRepository.findByDpmIdOrderByDistributedAtAsc(dpmId);
        return DpmTrackingDto.builder()
                .dpm(dpm)
                .techDistributions(distributions)
                .build();
    }

    public ZmStockAuditDto getZmStockAudit(String zmId) {
        AppUser zm = userRepository.findById(zmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ZM not found: " + zmId));

        List<DpmRequest> dpms = dpmRepository.findByZmIdOrderByCreatedAtDesc(zmId);

        List<ZmDpmAuditItem> auditItems = dpms.stream().map(dpm -> {
            List<SttDistribution> dists = distributionRepository.findByDpmIdOrderByDistributedAtAsc(dpm.getId());

            // Per-equipment distributed quantities
            java.util.Map<String, Integer> distributedByEq = new java.util.HashMap<>();
            for (SttDistribution d : dists) {
                for (DistributionItem di : d.getItems()) {
                    distributedByEq.merge(di.getEquipmentId(), di.getQuantity(), Integer::sum);
                }
            }
            // overDistributed = any equipment has distributed > confirmed
            boolean overDistributed = dpm.getItems().stream().anyMatch(item -> {
                int conf = item.getConfirmedQuantity() != null ? item.getConfirmedQuantity() : 0;
                int dist = distributedByEq.getOrDefault(item.getEquipmentId(), 0);
                return dist > conf;
            });

            return ZmDpmAuditItem.builder()
                    .dpm(dpm)
                    .distributions(dists)
                    .partiallyConfirmed(dpm.getStatus() == DpmStatus.PARTIALLY_CONFIRMED)
                    .overDistributed(overDistributed)
                    .build();
        }).collect(Collectors.toList());

        return ZmStockAuditDto.builder()
                .zmId(zm.getId())
                .zmName(zm.getFullName())
                .zmEmail(zm.getEmail())
                .totalDpms(dpms.size())
                .pendingCount(dpms.stream().filter(d -> d.getStatus() == DpmStatus.PENDING_STT_CONFIRMATION).count())
                .confirmedCount(dpms.stream().filter(d -> d.getStatus() == DpmStatus.FULLY_CONFIRMED
                        || d.getStatus() == DpmStatus.PARTIALLY_CONFIRMED).count())
                .rejectedCount(dpms.stream().filter(d -> d.getStatus() == DpmStatus.REJECTED).count())
                .cancelledCount(dpms.stream().filter(d -> d.getStatus() == DpmStatus.CANCELLED).count())
                .warningCount(auditItems.stream().filter(i -> i.isPartiallyConfirmed() || i.isOverDistributed()).count())
                .dpms(auditItems)
                .build();
    }

    private List<DpmItem> buildDpmItems(CreateDpmRequest request) {
        return request.getItems().stream().map(itemReq -> {
            Equipment eq = equipmentRepository.findById(itemReq.getEquipmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Equipment not found: " + itemReq.getEquipmentId()));
            return DpmItem.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .equipmentImageUrl(eq.getImageFileId() != null ? "/api/v1/files/" + eq.getImageFileId() + "/view" : null)
                    .unit(eq.getUnit() != null ? eq.getUnit().name() : null)
                    .requestedQuantity(itemReq.getRequestedQuantity())
                    .build();
        }).collect(Collectors.toList());
    }

    private String generateDpmNumber() {
        int year = Year.now().getValue();
        String prefix = "DPM-" + year + "-";
        long count = dpmRepository.countByDpmNumberStartingWith(prefix);
        return String.format("%s%04d", prefix, count + 1);
    }

    private void addTimelineEvent(DpmRequest dpm, String eventType, String description,
                                   String performedBy, String role) {
        if (dpm.getTimeline() == null) {
            dpm.setTimeline(new ArrayList<>());
        }
        dpm.getTimeline().add(DpmTimelineEvent.builder()
                .eventType(eventType)
                .description(description)
                .performedBy(performedBy)
                .performedByRole(role)
                .occurredAt(Instant.now())
                .build());
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

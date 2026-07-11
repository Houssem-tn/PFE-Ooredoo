package com.pfe.stock_management.service;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationService;
import com.pfe.Notification.WebSocketNotificationSender;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseTimelineService;
import com.pfe.case_management.dto.UsedEquipmentRequestItem;
import com.pfe.stock_management.dto.CreatePermissionRequest;
import com.pfe.stock_management.dto.PermissionRequestResponse;
import com.pfe.stock_management.dto.RejectPermissionRequest;
import com.pfe.stock_management.dto.ReviewPermissionRequest;
import com.pfe.stock_management.entity.StockThreshold;
import com.pfe.stock_management.entity.StockUsagePermissionRequest;
import com.pfe.stock_management.model.PermissionItem;
import com.pfe.stock_management.repository.StockThresholdRepository;
import com.pfe.stock_management.repository.StockUsagePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages the over-limit stock usage permission workflow.
 *
 * Limits are ZM-configured per-item via StockThreshold.caseUsageLimit.
 * Fallback constants are used only when no ZM config exists (backward compatibility).
 * Only STT can approve/reject. ZM sees read-only tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockUsagePermissionService {

    /**
     * Fallback cable limit when no ZM threshold is configured for the item.
     * TODO: Remove once all ZMs have configured per-item case usage limits.
     */
    private static final double FALLBACK_CABLE_LIMIT_METERS = 20.0;

    /**
     * Fallback piece limit when no ZM threshold is configured for the item.
     * TODO: Remove once all ZMs have configured per-item case usage limits.
     */
    private static final int FALLBACK_ITEM_LIMIT_PIECES = 1;

    private final StockUsagePermissionRepository permissionRepository;
    private final StockThresholdRepository stockThresholdRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseTimelineService caseTimelineService;
    private final NotificationService notificationService;
    private final WebSocketNotificationSender wsNotificationSender;

    // ── TECH actions ──────────────────────────────────────────────────────────

    public PermissionRequestResponse createRequest(CreatePermissionRequest req) {
        AppUser tech = currentUser();
        CaseEntity caseEntity = caseRepository.findById(req.getCaseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));

        if (!tech.getId().equals(caseEntity.getTechId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only request permission for cases assigned to you");
        }

        // Reject if a non-terminal request already exists for this case+TECH
        permissionRepository.findByCaseIdAndTechIdAndStatusIn(
                req.getCaseId(), tech.getId(), List.of("PENDING", "APPROVED", "PARTIALLY_APPROVED")
        ).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A permission request already exists for this case with status: " + existing.getStatus());
        });

        AppUser stt = resolveAssignedStt(tech);

        List<PermissionItem> requestedItems = mapRequestedItems(req.getRequestedItems());
        double requestedCableMeters = requestedCableMeters(req.getRequestedCableMeters(), requestedItems);

        StockUsagePermissionRequest permission = StockUsagePermissionRequest.builder()
                .caseId(caseEntity.getId())
                .caseReference(caseEntity.getCrmCase())
                .techId(tech.getId())
                .techName(tech.getFullName())
                .techEmail(tech.getEmail())
                .sttId(stt.getId())
                .sttName(stt.getFullName())
                .sttEmail(stt.getEmail())
                .zmId(stt.getAssignedZmId())
                .zmName(resolveZmName(stt))
                .status("PENDING")
                .reason(req.getReason())
                .requestedCableMeters(requestedCableMeters)
                .requestedItems(requestedItems)
                .createdAt(Instant.now())
                .build();

        permission = permissionRepository.save(permission);

        // Timeline: append to case
        Map<String, Object> timelineData = new HashMap<>();
        timelineData.put("permissionId", permission.getId());
        timelineData.put("requestedCableMeters", requestedCableMeters);
        timelineData.put("techName", tech.getFullName());
        caseTimelineService.append(caseEntity, CaseTimelineEventType.STOCK_USAGE_PERMISSION_REQUESTED,
                "Over-limit stock usage requested",
                "TECH " + tech.getFullName() + " requested permission to use " +
                        formatRequest(req) + " — pending STT review.",
                tech.getEmail(), caseEntity.getStatus(), caseEntity.getStatus(), timelineData);
        caseRepository.save(caseEntity);

        // Notify STT with CRITICAL alert
        notifyPermissionAlert(stt.getEmail(), "CRITICAL",
                "STOCK_USAGE_PERMISSION_REQUIRED",
                "Stock Usage Permission Required",
                "TECH " + tech.getFullName() + " requested " + formatRequest(req) +
                        " for case " + caseEntity.getCrmCase() + ". Review and approve/reject.",
                permission.getId(), caseEntity.getId(), "OPEN_STOCK_PERMISSION_REQUEST");

        return PermissionRequestResponse.from(permission);
    }

    public List<PermissionRequestResponse> getMyPendingRequests() {
        AppUser stt = currentUser();
        return permissionRepository.findBySttIdAndStatus(stt.getId(), "PENDING")
                .stream()
                .map(PermissionRequestResponse::from)
                .collect(Collectors.toList());
    }

    public List<PermissionRequestResponse> getByCase(String caseId) {
        return permissionRepository.findByCaseId(caseId)
                .stream()
                .map(PermissionRequestResponse::from)
                .collect(Collectors.toList());
    }

    public List<PermissionRequestResponse> getMyRequests() {
        AppUser tech = currentUser();
        return permissionRepository.findByTechId(tech.getId())
                .stream()
                .map(PermissionRequestResponse::from)
                .collect(Collectors.toList());
    }

    // ── STT actions ───────────────────────────────────────────────────────────

    public PermissionRequestResponse approve(String id, ReviewPermissionRequest req) {
        AppUser stt = currentUser();
        StockUsagePermissionRequest permission = loadAndValidateStt(id, stt);

        List<PermissionItem> approvedItems = resolveApprovedItems(
                permission.getRequestedItems(), req.getApprovedItems());

        double approvedCable = hasDetailedMeterItems(permission.getRequestedItems())
                ? Math.min(sumApprovedMeterQuantity(approvedItems), permission.getRequestedCableMeters())
                : Math.min(req.getApprovedCableMeters(), permission.getRequestedCableMeters());

        String newStatus = determineStatus(permission, approvedCable, approvedItems);

        permission.setApprovedCableMeters(approvedCable);
        permission.setApprovedItems(approvedItems);
        permission.setStatus(newStatus);
        permission.setReviewComment(req.getReviewComment());
        permission.setReviewedAt(Instant.now());
        permission.setReviewedBySttId(stt.getId());

        permission = permissionRepository.save(permission);

        // Timeline
        CaseEntity caseEntity = caseRepository.findById(permission.getCaseId()).orElse(null);
        if (caseEntity != null) {
            CaseTimelineEventType tlType = "APPROVED".equals(newStatus)
                    ? CaseTimelineEventType.STOCK_USAGE_PERMISSION_APPROVED
                    : CaseTimelineEventType.STOCK_USAGE_PERMISSION_PARTIALLY_APPROVED;

            Map<String, Object> data = Map.of(
                    "permissionId", permission.getId(),
                    "approvedCableMeters", approvedCable,
                    "sttName", stt.getFullName()
            );
            caseTimelineService.append(caseEntity, tlType,
                    "Over-limit usage " + newStatus.toLowerCase().replace('_', ' '),
                    "Approved by STT " + stt.getFullName() + ": " + formatApproval(approvedCable, approvedItems) +
                            " — ZM visibility only.",
                    stt.getEmail(), caseEntity.getStatus(), caseEntity.getStatus(), data);
            caseRepository.save(caseEntity);
        }

        // Notify TECH — include full approval details so the page updates without an extra HTTP call
        Map<String, Object> techApprovalData = new HashMap<>();
        techApprovalData.put("permissionStatus", newStatus);
        techApprovalData.put("approvedCableMeters", approvedCable);
        if (req.getReviewComment() != null) techApprovalData.put("reviewComment", req.getReviewComment());
        if (approvedItems != null) {
            techApprovalData.put("approvedItems", approvedItems.stream()
                    .map(i -> Map.of(
                            "itemId", i.getItemId(),
                            "itemName", i.getItemName(),
                            "unit", i.getUnit(),
                            "approvedQuantity", i.getApprovedQuantity()))
                    .collect(Collectors.toList()));
        }
        notifyPermissionAlert(permission.getTechEmail(), "INFO",
                "STOCK_USAGE_PERMISSION_APPROVED",
                "Stock Permission " + newStatus.replace('_', ' '),
                "STT " + stt.getFullName() + " approved your usage request for case " +
                        permission.getCaseReference() + ".",
                permission.getId(), permission.getCaseId(), "OPEN_CASE", techApprovalData);

        // Notify ZM (info)
        if (permission.getZmId() != null) {
            AppUser zm = userRepository.findById(permission.getZmId()).orElse(null);
            if (zm != null && zm.getEmail() != null) {
                notifyPermissionAlert(zm.getEmail(), "INFO",
                        "STOCK_USAGE_PERMISSION_APPROVED",
                        "Over-limit Usage Approved — ZM Tracking",
                        "Case " + permission.getCaseReference() + ": TECH " + permission.getTechName() +
                                " was approved by STT " + stt.getFullName() + " for " +
                                formatApproval(approvedCable, approvedItems) + ".",
                        permission.getId(), permission.getCaseId(), "OPEN_CASE");
            }
        }

        return PermissionRequestResponse.from(permission);
    }

    public PermissionRequestResponse reject(String id, RejectPermissionRequest req) {
        AppUser stt = currentUser();
        StockUsagePermissionRequest permission = loadAndValidateStt(id, stt);

        permission.setStatus("REJECTED");
        permission.setReviewComment(req.getReviewComment());
        permission.setReviewedAt(Instant.now());
        permission.setReviewedBySttId(stt.getId());

        permission = permissionRepository.save(permission);

        // Timeline
        CaseEntity caseEntity = caseRepository.findById(permission.getCaseId()).orElse(null);
        if (caseEntity != null) {
            Map<String, Object> data = Map.of("permissionId", permission.getId());
            caseTimelineService.append(caseEntity,
                    CaseTimelineEventType.STOCK_USAGE_PERMISSION_REJECTED,
                    "Over-limit usage request rejected",
                    "STT " + stt.getFullName() + " rejected the usage request. Reason: " +
                            req.getReviewComment(),
                    stt.getEmail(), caseEntity.getStatus(), caseEntity.getStatus(), data);
            caseRepository.save(caseEntity);
        }

        // Notify TECH — include reason so the page updates without an extra HTTP call
        Map<String, Object> techRejectData = new HashMap<>();
        techRejectData.put("permissionStatus", "REJECTED");
        techRejectData.put("reviewComment", req.getReviewComment());
        notifyPermissionAlert(permission.getTechEmail(), "WARNING",
                "STOCK_USAGE_PERMISSION_REJECTED",
                "Stock Permission Rejected",
                "STT " + stt.getFullName() + " rejected your over-limit usage request for case " +
                        permission.getCaseReference() + ". Reason: " + req.getReviewComment(),
                permission.getId(), permission.getCaseId(), "OPEN_CASE", techRejectData);

        return PermissionRequestResponse.from(permission);
    }

    // ── Enforcement API (called by SttStockService) ────────────────────────────

    /**
     * Enforces per-item case usage limits configured by the ZM.
     * Throws 403 if any item exceeds its ZM-configured limit and no approved STT permission exists.
     *
     * @param zmId          ZM responsible for this TECH (null falls back to hardcoded defaults)
     * @param requestedItems all items the TECH wants to consume
     */
    public void enforceUsageLimits(String caseId, String techId, String zmId,
                                    List<UsedEquipmentRequestItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) return;

        List<String> violationMessages = new ArrayList<>();
        double totalViolatingCableMeters = 0;
        Map<String, Double> itemViolations = new java.util.LinkedHashMap<>();
        Map<String, String> itemViolationNames = new HashMap<>();
        Map<String, Boolean> itemViolationMeters = new HashMap<>();

        for (UsedEquipmentRequestItem item : requestedItems) {
            boolean isCable = isMeterUnit(item.unit());
            double qty = item.quantity();
            Double limit = resolveItemLimit(zmId, item.equipmentId(), isCable);

            if (limit != null && qty > limit) {
                String unitLabel = isCable ? "m" : "pcs";
                String name = (item.equipmentName() != null && !item.equipmentName().isBlank())
                        ? item.equipmentName() : item.equipmentId();
                violationMessages.add(name + " requested: " + fmtQty(qty, isCable) +
                        " " + unitLabel + ", allowed without permission: " + fmtQty(limit, isCable) + " " + unitLabel);
                itemViolations.put(item.equipmentId(), qty);
                itemViolationNames.put(item.equipmentId(), name);
                itemViolationMeters.put(item.equipmentId(), isCable);
                if (isCable) {
                    totalViolatingCableMeters += qty;
                }
            }
        }

        if (violationMessages.isEmpty()) return; // all items within limits

        // At least one item exceeds its limit — require an approved STT permission
        StockUsagePermissionRequest permission = permissionRepository
                .findByCaseIdAndTechIdAndStatusIn(caseId, techId,
                        List.of("APPROVED", "PARTIALLY_APPROVED"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This case uses more than the allowed limit configured by your ZM. " +
                        "Please request STT permission before finishing. " +
                        String.join("; ", violationMessages)));

        boolean hasDetailedMeterApprovals = hasDetailedMeterItems(permission.getApprovedItems())
                || hasDetailedMeterItems(permission.getRequestedItems());

        // Validate approved cable amount covers violating cable
        if (!hasDetailedMeterApprovals
                && totalViolatingCableMeters > 0
                && totalViolatingCableMeters > permission.getApprovedCableMeters()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cable usage " + fmtQty(totalViolatingCableMeters, true) +
                    "m exceeds the approved amount of " +
                    fmtQty(permission.getApprovedCableMeters(), true) + "m.");
        }

        // Validate approved item quantities
        if (!itemViolations.isEmpty() && permission.getApprovedItems() != null) {
            Map<String, Double> approvedMap = permission.getApprovedItems().stream()
                    .collect(Collectors.toMap(PermissionItem::getItemId, PermissionItem::getApprovedQuantity));
            for (Map.Entry<String, Double> entry : itemViolations.entrySet()) {
                boolean isMeterItem = itemViolationMeters.getOrDefault(entry.getKey(), false);
                if (isMeterItem && !hasDetailedMeterApprovals) {
                    continue;
                }
                double approved = approvedMap.getOrDefault(entry.getKey(), 0.0);
                if (entry.getValue() > approved) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            itemViolationNames.getOrDefault(entry.getKey(), entry.getKey()) +
                            " quantity " + fmtQty(entry.getValue(), isMeterItem) +
                            " " + (isMeterItem ? "m" : "pcs") +
                            " exceeds the approved amount of " +
                            fmtQty(approved, isMeterItem) + " " + (isMeterItem ? "m" : "pcs") + ".");
                }
            }
        }
    }

    /**
     * Resolves the case usage limit for a specific item under the given ZM.
     * Returns null when no limit applies (ZM disabled the limit for this item).
     */
    private Double resolveItemLimit(String zmId, String itemId, boolean isCable) {
        if (zmId != null) {
            java.util.Optional<StockThreshold> cfg =
                    stockThresholdRepository.findByZmIdAndItemId(zmId, itemId);
            if (cfg.isPresent()) {
                StockThreshold t = cfg.get();
                // ZM configured this item: respect their setting (null when disabled = no limit)
                return t.isCaseUsageLimitEnabled() ? t.getCaseUsageLimit() : null;
            }
        }
        // TODO: Fallback exists only for backward compatibility — remove once all ZMs configure limits.
        return isCable ? FALLBACK_CABLE_LIMIT_METERS : (double) FALLBACK_ITEM_LIMIT_PIECES;
    }

    private String fmtQty(double qty, boolean isCable) {
        if (!isCable) return String.valueOf((int) qty);
        return qty == Math.floor(qty) ? String.valueOf((int) qty) : String.valueOf(qty);
    }

    private boolean isMeterUnit(String unit) {
        return unit != null && "METERS".equalsIgnoreCase(unit.trim());
    }

    private boolean hasDetailedMeterItems(List<PermissionItem> items) {
        return items != null && items.stream().anyMatch(item -> isMeterUnit(item.getUnit()));
    }

    private double sumApprovedMeterQuantity(List<PermissionItem> items) {
        if (items == null) return 0;
        return items.stream()
                .filter(item -> isMeterUnit(item.getUnit()))
                .mapToDouble(PermissionItem::getApprovedQuantity)
                .sum();
    }

    private double requestedCableMeters(double legacyRequestedCableMeters, List<PermissionItem> requestedItems) {
        if (hasDetailedMeterItems(requestedItems)) {
            return requestedItems.stream()
                    .filter(item -> isMeterUnit(item.getUnit()))
                    .mapToDouble(PermissionItem::getRequestedQuantity)
                    .sum();
        }
        return legacyRequestedCableMeters;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AppUser currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private AppUser resolveAssignedStt(AppUser tech) {
        if (tech.getAssignedSttId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You are not assigned to any STT — cannot submit permission request");
        }
        return userRepository.findById(tech.getAssignedSttId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned STT not found"));
    }

    private String resolveZmName(AppUser stt) {
        if (stt.getAssignedZmId() == null) return null;
        return userRepository.findById(stt.getAssignedZmId())
                .map(AppUser::getFullName)
                .orElse(null);
    }

    private StockUsagePermissionRequest loadAndValidateStt(String id, AppUser stt) {
        StockUsagePermissionRequest permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission request not found"));

        if (!stt.getId().equals(permission.getSttId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only review permission requests from your technicians");
        }

        if (!"PENDING".equals(permission.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Permission request is already in status: " + permission.getStatus());
        }

        return permission;
    }

    private String determineStatus(StockUsagePermissionRequest perm,
                                   double approvedCable,
                                   List<PermissionItem> approvedItems) {
        boolean cablePartial = perm.getRequestedCableMeters() > 0
                && approvedCable < perm.getRequestedCableMeters();
        boolean itemPartial = false;
        if (perm.getRequestedItems() != null && approvedItems != null) {
            for (PermissionItem req : perm.getRequestedItems()) {
                double approved = approvedItems.stream()
                        .filter(a -> a.getItemId().equals(req.getItemId()))
                        .findFirst()
                        .map(PermissionItem::getApprovedQuantity)
                        .orElse(0.0);
                if (approved < req.getRequestedQuantity()) {
                    itemPartial = true;
                    break;
                }
            }
        }
        return (cablePartial || itemPartial) ? "PARTIALLY_APPROVED" : "APPROVED";
    }

    private List<PermissionItem> mapRequestedItems(
            List<CreatePermissionRequest.PermissionItemRequest> items) {
        if (items == null) return new ArrayList<>();
        return items.stream().map(i -> PermissionItem.builder()
                .itemId(i.getItemId())
                .itemName(i.getItemName())
                .requestedQuantity(i.getRequestedQuantity())
                .unit(i.getUnit())
                .build()
        ).collect(Collectors.toList());
    }

    private List<PermissionItem> resolveApprovedItems(
            List<PermissionItem> requestedItems,
            List<ReviewPermissionRequest.ApprovedItemRequest> approvals) {
        if (requestedItems == null) return new ArrayList<>();
        Map<String, Double> approvalMap = approvals == null ? Map.of() :
                approvals.stream().collect(Collectors.toMap(
                        ReviewPermissionRequest.ApprovedItemRequest::getItemId,
                        ReviewPermissionRequest.ApprovedItemRequest::getApprovedQuantity));

        return requestedItems.stream().map(req -> {
            double approved = Math.min(
                    approvalMap.getOrDefault(req.getItemId(), 0.0),
                    req.getRequestedQuantity());
            return PermissionItem.builder()
                    .itemId(req.getItemId())
                    .itemName(req.getItemName())
                    .requestedQuantity(req.getRequestedQuantity())
                    .approvedQuantity(approved)
                    .unit(req.getUnit())
                    .build();
        }).collect(Collectors.toList());
    }

    private void notifyPermissionAlert(String recipientEmail, String level, String eventType,
                                        String title, String message, String permissionId,
                                        String caseId, String actionType) {
        notifyPermissionAlert(recipientEmail, level, eventType, title, message,
                permissionId, caseId, actionType, null);
    }

    private void notifyPermissionAlert(String recipientEmail, String level, String eventType,
                                        String title, String message, String permissionId,
                                        String caseId, String actionType, Map<String, Object> extraData) {
        if (recipientEmail == null) return;
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("permissionId", permissionId);
            data.put("caseId", caseId);
            data.put("routeHint", "/stt/stock/usage-approvals");
            if (extraData != null) data.putAll(extraData);

            NotificationMessageDto payload = new NotificationMessageDto();
            payload.setEventType(eventType);
            payload.setTitle(title);
            payload.setMessage(message);
            payload.setEntityType("STOCK_PERMISSION");
            payload.setEntityId(permissionId);
            payload.setStatus("PENDING");
            payload.setTriggeredBy("SYSTEM");
            payload.setCreatedAt(Instant.now());
            payload.setLevel(level);
            payload.setActionType(actionType);
            payload.setRelatedPermissionRequestId(permissionId);
            payload.setRelatedCaseId(caseId);
            payload.setData(data);

            Notification saved = notificationService.createForRecipient(recipientEmail, payload);
            if (saved != null) {
                wsNotificationSender.sendToUser(recipientEmail, saved);
            }
        } catch (Exception e) {
            log.warn("Failed to send stock permission alert to {} (eventType={})", recipientEmail, eventType, e);
        }
    }

    private String formatRequest(CreatePermissionRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getRequestedItems() != null && !req.getRequestedItems().isEmpty()) {
            sb.append(req.getRequestedItems().stream()
                    .map(i -> formatItemQuantity(i.getRequestedQuantity(), i.getUnit()) + " " + i.getItemName())
                    .collect(Collectors.joining(", ")));
        } else if (req.getRequestedCableMeters() > 0) {
            sb.append(fmtQty(req.getRequestedCableMeters(), true)).append("m cable");
        }
        return sb.toString();
    }

    private String formatApproval(double approvedCable, List<PermissionItem> items) {
        StringBuilder sb = new StringBuilder();
        if (items != null && !items.isEmpty()) {
            sb.append(items.stream()
                    .map(i -> formatItemQuantity(i.getApprovedQuantity(), i.getUnit()) + " " + i.getItemName())
                    .collect(Collectors.joining(", ")));
        } else if (approvedCable > 0) {
            sb.append(fmtQty(approvedCable, true)).append("m cable");
        }
        return sb.toString();
    }

    private String formatItemQuantity(double quantity, String unit) {
        boolean meter = isMeterUnit(unit);
        return fmtQty(quantity, meter) + (meter ? " m" : " pcs");
    }
}

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
import com.pfe.stock_management.dto.CreatePermissionRequest;
import com.pfe.stock_management.dto.PermissionRequestResponse;
import com.pfe.stock_management.dto.RejectPermissionRequest;
import com.pfe.stock_management.dto.ReviewPermissionRequest;
import com.pfe.stock_management.entity.StockUsagePermissionRequest;
import com.pfe.stock_management.model.PermissionItem;
import com.pfe.stock_management.repository.StockUsagePermissionRepository;
import lombok.RequiredArgsConstructor;
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
 * Business limits enforced by the backend:
 *   - cable  > 20 m  → permission required
 *   - any item > 1 piece → permission required
 *
 * Only STT can approve/reject. ZM sees read-only tracking.
 */
@Service
@RequiredArgsConstructor
public class StockUsagePermissionService {

    /** Cable threshold in metres. Do NOT change without also updating the frontend constant. */
    public static final double CABLE_LIMIT_METERS = 20.0;

    /** Per-item quantity threshold. */
    public static final int ITEM_LIMIT_PIECES = 1;

    private final StockUsagePermissionRepository permissionRepository;
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
                .requestedCableMeters(req.getRequestedCableMeters())
                .requestedItems(requestedItems)
                .createdAt(Instant.now())
                .build();

        permission = permissionRepository.save(permission);

        // Timeline: append to case
        Map<String, Object> timelineData = new HashMap<>();
        timelineData.put("permissionId", permission.getId());
        timelineData.put("requestedCableMeters", req.getRequestedCableMeters());
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

        double approvedCable = Math.min(req.getApprovedCableMeters(), permission.getRequestedCableMeters());

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

        // Notify TECH
        notifyPermissionAlert(permission.getTechEmail(), "INFO",
                "STOCK_USAGE_PERMISSION_APPROVED",
                "Stock Permission " + newStatus.replace('_', ' '),
                "STT " + stt.getFullName() + " approved your usage request for case " +
                        permission.getCaseReference() + ".",
                permission.getId(), permission.getCaseId(), "OPEN_CASE");

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

        // Notify TECH
        notifyPermissionAlert(permission.getTechEmail(), "WARNING",
                "STOCK_USAGE_PERMISSION_REJECTED",
                "Stock Permission Rejected",
                "STT " + stt.getFullName() + " rejected your over-limit usage request for case " +
                        permission.getCaseReference() + ". Reason: " + req.getReviewComment(),
                permission.getId(), permission.getCaseId(), "OPEN_CASE");

        return PermissionRequestResponse.from(permission);
    }

    // ── Enforcement API (called by SttStockService) ────────────────────────────

    /**
     * Returns the effective approved cable metres for a given case+TECH.
     * Throws if the operation exceeds limits and no valid permission exists.
     *
     * @param requestedCableMeters total cable the TECH wants to consume
     * @param itemQuantities map of itemId → requested quantity
     */
    public void enforceUsageLimits(String caseId, String techId,
                                    double requestedCableMeters,
                                    Map<String, Integer> itemQuantities) {
        boolean cableExceeds = requestedCableMeters > CABLE_LIMIT_METERS;
        boolean itemExceeds = itemQuantities.values().stream().anyMatch(q -> q > ITEM_LIMIT_PIECES);

        if (!cableExceeds && !itemExceeds) {
            return; // within automatic limits — no permission needed
        }

        StockUsagePermissionRequest permission = permissionRepository
                .findByCaseIdAndTechIdAndStatusIn(caseId, techId,
                        List.of("APPROVED", "PARTIALLY_APPROVED"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This operation requires STT permission because it exceeds the allowed stock usage limit. " +
                                "Please submit a permission request first."));

        // Enforce approved cable amount
        if (cableExceeds && requestedCableMeters > permission.getApprovedCableMeters()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cable usage " + requestedCableMeters + "m exceeds the approved amount of " +
                            permission.getApprovedCableMeters() + "m.");
        }

        // Enforce approved item quantities
        if (itemExceeds && permission.getApprovedItems() != null) {
            Map<String, Double> approvedMap = permission.getApprovedItems().stream()
                    .collect(Collectors.toMap(PermissionItem::getItemId, PermissionItem::getApprovedQuantity));
            for (Map.Entry<String, Integer> entry : itemQuantities.entrySet()) {
                if (entry.getValue() > ITEM_LIMIT_PIECES) {
                    double approved = approvedMap.getOrDefault(entry.getKey(), 0.0);
                    if (entry.getValue() > approved) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Item quantity " + entry.getValue() +
                                        " exceeds the approved amount of " + (int) approved + ".");
                    }
                }
            }
        }
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
        if (recipientEmail == null) return;
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("permissionId", permissionId);
            data.put("caseId", caseId);
            data.put("routeHint", "/stt/stock/usage-approvals");

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
            // Non-critical — log and continue
        }
    }

    private String formatRequest(CreatePermissionRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getRequestedCableMeters() > 0) {
            sb.append(req.getRequestedCableMeters()).append("m cable");
        }
        if (req.getRequestedItems() != null && !req.getRequestedItems().isEmpty()) {
            if (sb.length() > 0) sb.append(" and ");
            sb.append(req.getRequestedItems().stream()
                    .map(i -> (int) i.getRequestedQuantity() + "x " + i.getItemName())
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    private String formatApproval(double approvedCable, List<PermissionItem> items) {
        StringBuilder sb = new StringBuilder();
        if (approvedCable > 0) sb.append(approvedCable).append("m cable");
        if (items != null && !items.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(items.stream()
                    .map(i -> (int) i.getApprovedQuantity() + "x " + i.getItemName())
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }
}

package com.pfe.stock_management.service;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Repository.NotificationRepository;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationService;
import com.pfe.MainPackage.Service.SttZmCoverageService;
import com.pfe.Notification.WebSocketNotificationSender;
import com.pfe.stock_management.entity.StockThreshold;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.model.TechStockItem;
import com.pfe.stock_management.repository.StockThresholdRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates STT and TECH stock levels against ZM-configured thresholds and
 * creates CRITICAL / WARNING alerts for the responsible ZM(s) and STT when
 * stock falls below the thresholds.
 *
 * <p><b>Recipient resolution</b> follows the current model {@code ZM → STT → TECH}:
 * the one-to-one {@code assignedZm} link was removed, so the responsible ZM(s) are
 * derived from shared governorate + module coverage via {@link SttZmCoverageService}.
 * A stock owned by a TECH is escalated to that TECH's STT and to every ZM whose
 * territory covers the STT.</p>
 *
 * <p><b>Lifecycle / deduplication:</b> a new alert is only created when no active
 * (unread) alert of the same type+entity already exists for the recipient. When the
 * stock recovers to {@code NORMAL}, the active alert for that entity is resolved
 * (marked read + status {@code RESOLVED}) so a later drop can raise a fresh alert.
 * Reading an alert and resolving a stock condition are handled here as one action
 * only on recovery — reading in the UI never suppresses an ongoing condition.</p>
 *
 * <p>Units are never mixed: each stock item is classified against its own unit's
 * quantity; pieces are evaluated independently from meters or any other unit.</p>
 */
@Service
@RequiredArgsConstructor
public class StockAlertEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(StockAlertEvaluationService.class);

    /** Fallback warning threshold when no ZM threshold is configured for an item. */
    private static final double DEFAULT_WARNING_THRESHOLD = 3;

    private final StockThresholdRepository thresholdRepository;
    private final SttStockRepository sttStockRepository;
    private final TechStockRepository techStockRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final WebSocketNotificationSender wsNotificationSender;
    private final SttZmCoverageService sttZmCoverageService;

    // ── Public trigger methods ────────────────────────────────────────────────

    /**
     * Evaluate all stock items for a single STT and notify every responsible ZM
     * (resolved from coverage) if any item falls below the configured threshold.
     */
    public void evaluateSttStock(String sttId) {
        AppUser stt = userRepository.findById(sttId).orElse(null);
        if (stt == null) return;

        List<AppUser> responsibleZms = sttZmCoverageService.findApprovingZmsForStt(stt);
        if (responsibleZms.isEmpty()) {
            log.debug("[StockAlert] STT {} ({}) has no responsible ZM via coverage — no STT stock alert generated",
                    sttId, stt.getFullName());
            return;
        }

        sttStockRepository.findBySttId(sttId).ifPresent(stock -> {
            for (AppUser zm : responsibleZms) {
                List<StockThreshold> thresholds = thresholdRepository.findByZmIdAndEnabledTrue(zm.getId());
                for (SttStockItem item : stock.getItems()) {
                    evaluateSttItem(stt, zm, item, thresholds);
                }
            }
        });
    }

    /**
     * Evaluate all stock items for a single TECH and notify the managing STT plus
     * every responsible ZM (resolved from the STT's coverage) if any item falls
     * below the configured threshold.
     */
    public void evaluateTechStock(String techId) {
        AppUser tech = userRepository.findById(techId).orElse(null);
        if (tech == null || tech.getAssignedSttId() == null) return;

        AppUser stt = userRepository.findById(tech.getAssignedSttId()).orElse(null);
        if (stt == null) return;

        List<AppUser> responsibleZms = sttZmCoverageService.findApprovingZmsForStt(stt);

        // Thresholds for classifying the TECH item: use the primary responsible ZM's
        // configuration (falls back to defaults when none is configured / no ZM).
        List<StockThreshold> thresholds = responsibleZms.isEmpty()
                ? List.of()
                : thresholdRepository.findByZmIdAndEnabledTrue(responsibleZms.get(0).getId());

        techStockRepository.findByTechId(techId).ifPresent(stock -> {
            for (TechStockItem item : stock.getItems()) {
                evaluateTechItem(tech, stt, responsibleZms, item, thresholds);
            }
        });
    }

    // ── Private evaluation logic ──────────────────────────────────────────────

    private void evaluateSttItem(AppUser stt, AppUser zm, SttStockItem item, List<StockThreshold> thresholds) {
        StockThreshold threshold = findThreshold(thresholds, item.getEquipmentId());

        // Skip if STT alerts are explicitly disabled for this item
        if (threshold != null && Boolean.FALSE.equals(threshold.getSttAlertsEnabled())) return;

        double qty = item.getAvailableQuantity();
        double warningAt  = resolveWarning(threshold, threshold != null ? threshold.getSttWarningThreshold() : null);
        double criticalAt = resolveCritical(threshold, threshold != null ? threshold.getSttCriticalThreshold() : null);
        String unit = item.getUnit() != null ? item.getUnit() : "PIECES";

        String entityId = "STT:" + stt.getId() + ":" + item.getEquipmentId();

        if (qty <= criticalAt) {
            sendStockAlert(zm.getEmail(), entityId, stt.getId(), "STT", stt.getFullName(),
                    item.getEquipmentId(), item.getEquipmentName(), qty, unit, warningAt, criticalAt,
                    "CRITICAL", "STOCK_CRITICAL", "Critical Stock Risk",
                    "STT " + stt.getFullName() + " has " + qty + " " + unit.toLowerCase()
                            + " of " + item.getEquipmentName() + " remaining. Stock is critically low.",
                    zm, null);
        } else if (qty <= warningAt) {
            sendStockAlert(zm.getEmail(), entityId, stt.getId(), "STT", stt.getFullName(),
                    item.getEquipmentId(), item.getEquipmentName(), qty, unit, warningAt, criticalAt,
                    "WARNING", "STOCK_LOW_WARNING", "Low Stock Warning",
                    "STT " + stt.getFullName() + " has only " + qty + " " + unit.toLowerCase()
                            + " of " + item.getEquipmentName() + " left.",
                    zm, null);
        } else {
            // Recovered above threshold — resolve any open alert for this ZM+entity.
            resolveActiveAlerts(zm.getEmail(), entityId);
        }
    }

    private void evaluateTechItem(AppUser tech, AppUser stt, List<AppUser> responsibleZms,
                                  TechStockItem item, List<StockThreshold> thresholds) {
        StockThreshold threshold = findThreshold(thresholds, item.getEquipmentId());

        // Skip if TECH alerts are explicitly disabled for this item
        if (threshold != null && Boolean.FALSE.equals(threshold.getTechAlertsEnabled())) return;

        double qty = item.getRemainingQuantity();
        double warningAt  = resolveWarning(threshold, threshold != null ? threshold.getTechWarningThreshold() : null);
        double criticalAt = resolveCritical(threshold, threshold != null ? threshold.getTechCriticalThreshold() : null);
        String unit = item.getUnit() != null ? item.getUnit() : "PIECES";

        String entityId = "TECH:" + tech.getId() + ":" + item.getEquipmentId();

        String level;
        String eventType;
        String title;
        String message;
        if (qty <= criticalAt) {
            level = "CRITICAL";
            eventType = "STOCK_CRITICAL";
            title = "Critical Stock Risk — TECH";
            message = "TECH " + tech.getFullName() + " has " + qty + " " + unit.toLowerCase()
                    + " of " + item.getEquipmentName() + " left. Immediate attention required.";
        } else if (qty <= warningAt) {
            level = "WARNING";
            eventType = "STOCK_LOW_WARNING";
            title = "Low Stock Warning — TECH";
            message = "TECH " + tech.getFullName() + " has only " + qty + " " + unit.toLowerCase()
                    + " of " + item.getEquipmentName() + " remaining.";
        } else {
            // Recovered — resolve open TECH alert for the STT and every responsible ZM.
            resolveActiveAlerts(stt.getEmail(), entityId);
            for (AppUser zm : responsibleZms) {
                resolveActiveAlerts(zm.getEmail(), entityId);
            }
            return;
        }

        // Notify the managing STT (both WARNING and CRITICAL).
        sendStockAlert(stt.getEmail(), entityId, tech.getId(), "TECH", tech.getFullName(),
                item.getEquipmentId(), item.getEquipmentName(), qty, unit, warningAt, criticalAt,
                level, eventType, title, message, null, stt);

        // Notify every responsible ZM (both WARNING and CRITICAL, per requirement).
        for (AppUser zm : responsibleZms) {
            sendStockAlert(zm.getEmail(), entityId, tech.getId(), "TECH", tech.getFullName(),
                    item.getEquipmentId(), item.getEquipmentName(), qty, unit, warningAt, criticalAt,
                    level, eventType, title, message, zm, stt);
        }
    }

    // ── Notification creation helpers ─────────────────────────────────────────

    /**
     * Creates (once, deduplicated) and pushes a stock alert to a single recipient.
     *
     * @param zm  responsible ZM when the recipient is a ZM (adds zone), else null
     * @param stt responsible STT when the owner is a TECH (adds sttId/sttName), else null
     */
    private void sendStockAlert(String recipientEmail, String entityId, String ownerId, String ownerType,
                                String ownerName, String itemId, String itemName, double qty, String unit,
                                double warningAt, double criticalAt, String level, String eventType,
                                String title, String message, AppUser zm, AppUser stt) {
        if (recipientEmail == null || recipientEmail.isBlank()) return;

        // Deduplicate: don't send the same active (unread) alert twice for this recipient+entity.
        if (notificationRepository.existsByRecipientEmailAndEventTypeAndEntityIdAndReadFalse(
                recipientEmail.toLowerCase(), eventType, entityId)) {
            log.debug("[StockAlert] {} alert already active for {} on {} — skipping duplicate",
                    level, recipientEmail, entityId);
            return;
        }

        Map<String, Object> data = buildStockAlertData(ownerId, ownerType, ownerName,
                itemId, itemName, qty, unit, level, warningAt, criticalAt);
        data.put("routeHint", ownerType.equals("STT") || zm != null
                ? "/zm/stock/team-stock" : "/stt/stock/my-stock");
        if (stt != null) {
            data.put("sttId", stt.getId());
            data.put("sttName", stt.getFullName());
        }
        if (zm != null) {
            data.put("responsibleZmId", zm.getId());
            data.put("responsibleZmName", zm.getFullName());
            if (zm.getCoveredGovernorates() != null && !zm.getCoveredGovernorates().isEmpty()) {
                data.put("zone", String.join(", ", zm.getCoveredGovernorates()));
            }
        }

        NotificationMessageDto payload = buildPayload(eventType, title, message, entityId, level,
                "OPEN_STOCK", data);

        try {
            Notification saved = notificationService.createForRecipient(recipientEmail, payload);
            if (saved != null) {
                wsNotificationSender.sendToUser(recipientEmail, saved);
                log.info("[StockAlert] {} {} alert delivered to {} (owner {} {} / item {})",
                        level, ownerType, recipientEmail, ownerType, ownerName, itemName);
            }
        } catch (Exception e) {
            log.warn("[StockAlert] Failed to send alert to {}: {}", recipientEmail, e.getMessage());
        }
    }

    /** Marks any active (unread) alert for this recipient+entity as read+RESOLVED. */
    private void resolveActiveAlerts(String recipientEmail, String entityId) {
        if (recipientEmail == null || recipientEmail.isBlank()) return;
        List<Notification> active = notificationRepository
                .findByRecipientEmailAndEntityIdAndReadFalse(recipientEmail.toLowerCase(), entityId);
        if (active.isEmpty()) return;

        for (Notification n : active) {
            n.setRead(true);
            n.setStatus("RESOLVED");
        }
        notificationRepository.saveAll(active);
        log.info("[StockAlert] Resolved {} active alert(s) for {} on {} (stock recovered above threshold)",
                active.size(), recipientEmail, entityId);
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("deprecation") // legacy flat thresholds are the documented fallback for un-migrated items
    private double resolveWarning(StockThreshold threshold, Double scopedWarning) {
        if (scopedWarning != null) return scopedWarning;
        if (threshold != null) return threshold.getWarningThreshold();
        return DEFAULT_WARNING_THRESHOLD;
    }

    @SuppressWarnings("deprecation") // legacy flat thresholds are the documented fallback for un-migrated items
    private double resolveCritical(StockThreshold threshold, Double scopedCritical) {
        if (scopedCritical != null) return scopedCritical;
        if (threshold != null) return threshold.getCriticalThreshold();
        return 0;
    }

    private StockThreshold findThreshold(List<StockThreshold> thresholds, String itemId) {
        return thresholds.stream()
                .filter(t -> itemId.equals(t.getItemId()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildStockAlertData(String ownerId, String ownerType,
                                                     String ownerName, String itemId,
                                                     String itemName, double qty,
                                                     String unit, String level,
                                                     double warningAt, double criticalAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("ownerId", ownerId);
        data.put("ownerType", ownerType);
        data.put("ownerName", ownerName);
        data.put("itemId", itemId);
        data.put("equipmentId", itemId);
        data.put("itemName", itemName);
        data.put("equipmentName", itemName);
        data.put("remainingQuantity", qty);
        data.put("currentQuantity", qty);
        data.put("unit", unit);
        data.put("alertLevel", level);
        data.put("warningThreshold", warningAt);
        data.put("criticalThreshold", criticalAt);
        return data;
    }

    private NotificationMessageDto buildPayload(String eventType, String title, String message,
                                                 String entityId, String level,
                                                 String actionType, Map<String, Object> data) {
        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType(eventType);
        dto.setTitle(title);
        dto.setMessage(message);
        dto.setEntityType("STOCK");
        dto.setEntityId(entityId);
        dto.setStatus("ACTIVE");
        dto.setTriggeredBy("SYSTEM");
        dto.setCreatedAt(Instant.now());
        dto.setLevel(level);
        dto.setCategory("STOCK");
        dto.setActionType(actionType);
        dto.setData(data);
        return dto;
    }
}

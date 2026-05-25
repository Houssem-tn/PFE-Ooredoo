package com.pfe.stock_management.service;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.NotificationRepository;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationService;
import com.pfe.Notification.WebSocketNotificationSender;
import com.pfe.stock_management.entity.StockThreshold;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
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
import java.util.Optional;

/**
 * Evaluates STT and TECH stock levels against ZM-configured thresholds and
 * creates CRITICAL / WARNING alerts when stock falls below the thresholds.
 *
 * Deduplication: a new alert is only created when no unread alert of the same
 * type + entity already exists for the target recipient.
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

    // ── Public trigger methods ────────────────────────────────────────────────

    /**
     * Evaluate all stock items for a single STT and notify ZM if any item
     * falls below the configured threshold.
     */
    public void evaluateSttStock(String sttId) {
        Optional<AppUser> sttOpt = userRepository.findById(sttId);
        if (sttOpt.isEmpty()) return;
        AppUser stt = sttOpt.get();

        if (stt.getAssignedZmId() == null) return;

        List<StockThreshold> thresholds = thresholdRepository.findByZmIdAndEnabledTrue(stt.getAssignedZmId());

        sttStockRepository.findBySttId(sttId).ifPresent(stock -> {
            for (SttStockItem item : stock.getItems()) {
                evaluateSttItem(stt, item, thresholds);
            }
        });
    }

    /**
     * Evaluate all stock items for a single TECH and notify STT + ZM if any
     * item falls below the configured threshold.
     */
    public void evaluateTechStock(String techId) {
        Optional<AppUser> techOpt = userRepository.findById(techId);
        if (techOpt.isEmpty()) return;
        AppUser tech = techOpt.get();

        if (tech.getAssignedSttId() == null) return;

        Optional<AppUser> sttOpt = userRepository.findById(tech.getAssignedSttId());
        if (sttOpt.isEmpty()) return;
        AppUser stt = sttOpt.get();

        if (stt.getAssignedZmId() == null) return;

        List<StockThreshold> thresholds = thresholdRepository.findByZmIdAndEnabledTrue(stt.getAssignedZmId());

        techStockRepository.findByTechId(techId).ifPresent(stock -> {
            for (TechStockItem item : stock.getItems()) {
                evaluateTechItem(tech, stt, item, thresholds);
            }
        });
    }

    // ── Private evaluation logic ──────────────────────────────────────────────

    private void evaluateSttItem(AppUser stt, SttStockItem item, List<StockThreshold> thresholds) {
        StockThreshold threshold = findThreshold(thresholds, item.getEquipmentId());

        // Skip if STT alerts are explicitly disabled for this item
        if (threshold != null && Boolean.FALSE.equals(threshold.getSttAlertsEnabled())) return;

        double qty = item.getAvailableQuantity();
        // Use STT-specific thresholds; fall back to legacy if not yet configured
        double warningAt  = threshold != null && threshold.getSttWarningThreshold()  != null
                ? threshold.getSttWarningThreshold()  : (threshold != null ? threshold.getWarningThreshold()  : DEFAULT_WARNING_THRESHOLD);
        double criticalAt = threshold != null && threshold.getSttCriticalThreshold() != null
                ? threshold.getSttCriticalThreshold() : (threshold != null ? threshold.getCriticalThreshold() : 0);
        String unit = item.getUnit() != null ? item.getUnit() : "PIECES";

        if (qty <= criticalAt) {
            sendSttStockAlert(stt, item, qty, unit, "CRITICAL",
                    "STOCK_CRITICAL",
                    "Critical Stock Risk",
                    "STT " + stt.getFullName() + " has " + qty + " " + unit.toLowerCase() +
                            " of " + item.getEquipmentName() + " remaining. Stock is critically low.");
        } else if (qty <= warningAt) {
            sendSttStockAlert(stt, item, qty, unit, "WARNING",
                    "STOCK_LOW_WARNING",
                    "Low Stock Warning",
                    "STT " + stt.getFullName() + " has only " + qty + " " + unit.toLowerCase() +
                            " of " + item.getEquipmentName() + " left.");
        }
    }

    private void evaluateTechItem(AppUser tech, AppUser stt, TechStockItem item, List<StockThreshold> thresholds) {
        StockThreshold threshold = findThreshold(thresholds, item.getEquipmentId());

        // Skip if TECH alerts are explicitly disabled for this item
        if (threshold != null && Boolean.FALSE.equals(threshold.getTechAlertsEnabled())) return;

        double qty = item.getRemainingQuantity();
        // Use TECH-specific thresholds; fall back to legacy if not yet configured
        double warningAt  = threshold != null && threshold.getTechWarningThreshold()  != null
                ? threshold.getTechWarningThreshold()  : (threshold != null ? threshold.getWarningThreshold()  : DEFAULT_WARNING_THRESHOLD);
        double criticalAt = threshold != null && threshold.getTechCriticalThreshold() != null
                ? threshold.getTechCriticalThreshold() : (threshold != null ? threshold.getCriticalThreshold() : 0);
        String unit = item.getUnit() != null ? item.getUnit() : "PIECES";

        if (qty <= criticalAt) {
            sendTechStockAlert(tech, stt, item, qty, unit, "CRITICAL",
                    "STOCK_CRITICAL",
                    "Critical Stock Risk — TECH",
                    "TECH " + tech.getFullName() + " has " + qty + " " + unit.toLowerCase() +
                            " of " + item.getEquipmentName() + " left. Immediate attention required.");
        } else if (qty <= warningAt) {
            sendTechStockAlert(tech, stt, item, qty, unit, "WARNING",
                    "STOCK_LOW_WARNING",
                    "Low Stock Warning — TECH",
                    "TECH " + tech.getFullName() + " has only " + qty + " " + unit.toLowerCase() +
                            " of " + item.getEquipmentName() + " remaining.");
        }
    }

    // ── Notification creation helpers ─────────────────────────────────────────

    /** Sends STT-level stock alert to the responsible ZM. */
    private void sendSttStockAlert(AppUser stt, SttStockItem item, double qty,
                                   String unit, String level, String eventType,
                                   String title, String message) {
        if (stt.getAssignedZmEmail() == null) return;

        String entityId = "STT:" + stt.getId() + ":" + item.getEquipmentId();

        // Deduplicate: don't send the same unread alert twice
        if (notificationRepository.existsByRecipientEmailAndEventTypeAndEntityIdAndReadFalse(
                stt.getAssignedZmEmail().toLowerCase(), eventType, entityId)) {
            return;
        }

        Map<String, Object> data = buildStockAlertData(stt.getId(), "STT",
                stt.getFullName(), item.getEquipmentId(), item.getEquipmentName(),
                qty, unit, level);
        data.put("routeHint", "/zm/stock/team-stock");

        NotificationMessageDto payload = buildPayload(eventType, title, message,
                entityId, level, "OPEN_STOCK", data);

        pushToRecipient(stt.getAssignedZmEmail(), payload);
    }

    /** Sends TECH-level stock alert to the responsible STT and ZM. */
    private void sendTechStockAlert(AppUser tech, AppUser stt, TechStockItem item,
                                    double qty, String unit, String level,
                                    String eventType, String title, String message) {
        String entityId = "TECH:" + tech.getId() + ":" + item.getEquipmentId();

        // Notify STT
        if (stt.getEmail() != null) {
            if (!notificationRepository.existsByRecipientEmailAndEventTypeAndEntityIdAndReadFalse(
                    stt.getEmail().toLowerCase(), eventType, entityId)) {

                Map<String, Object> dataStt = buildStockAlertData(tech.getId(), "TECH",
                        tech.getFullName(), item.getEquipmentId(), item.getEquipmentName(),
                        qty, unit, level);
                dataStt.put("routeHint", "/stt/stock/my-stock");

                NotificationMessageDto payloadStt = buildPayload(eventType, title, message,
                        entityId, level, "OPEN_STOCK", dataStt);
                pushToRecipient(stt.getEmail(), payloadStt);
            }
        }

        // Notify ZM (info/tracking only for CRITICAL)
        if ("CRITICAL".equals(level) && stt.getAssignedZmEmail() != null) {
            if (!notificationRepository.existsByRecipientEmailAndEventTypeAndEntityIdAndReadFalse(
                    stt.getAssignedZmEmail().toLowerCase(), eventType, entityId)) {

                Map<String, Object> dataZm = buildStockAlertData(tech.getId(), "TECH",
                        tech.getFullName(), item.getEquipmentId(), item.getEquipmentName(),
                        qty, unit, level);
                dataZm.put("sttId", stt.getId());
                dataZm.put("sttName", stt.getFullName());
                dataZm.put("routeHint", "/zm/stock/team-stock");

                NotificationMessageDto payloadZm = buildPayload(eventType, title, message,
                        entityId, level, "OPEN_STOCK", dataZm);
                pushToRecipient(stt.getAssignedZmEmail(), payloadZm);
            }
        }
    }

    private void pushToRecipient(String email, NotificationMessageDto payload) {
        try {
            Notification saved = notificationService.createForRecipient(email, payload);
            if (saved != null) {
                wsNotificationSender.sendToUser(email, saved);
            }
        } catch (Exception e) {
            log.warn("[StockAlert] Failed to send alert to {}: {}", email, e.getMessage());
        }
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    private StockThreshold findThreshold(List<StockThreshold> thresholds, String itemId) {
        return thresholds.stream()
                .filter(t -> itemId.equals(t.getItemId()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildStockAlertData(String ownerId, String ownerType,
                                                     String ownerName, String itemId,
                                                     String itemName, double qty,
                                                     String unit, String level) {
        Map<String, Object> data = new HashMap<>();
        data.put("ownerId", ownerId);
        data.put("ownerType", ownerType);
        data.put("ownerName", ownerName);
        data.put("itemId", itemId);
        data.put("itemName", itemName);
        data.put("remainingQuantity", qty);
        data.put("unit", unit);
        data.put("alertLevel", level);
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

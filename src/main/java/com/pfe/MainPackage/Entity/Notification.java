package com.pfe.MainPackage.Entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private String id;

    private String eventType;
    private String title;
    private String message;

    private String entityType;
    private String entityId;

    private String status;

    @Indexed
    private String targetRole;

    @Indexed
    private String recipientEmail;

    private String triggeredBy;

    @Indexed
    private boolean read;

    @Indexed
    private Instant createdAt;

    private Map<String, Object> data;

    /** Alert severity level for the Alert Center. Null means legacy notification (treated as INFO). */
    private AlertLevel level;

    /**
     * Business category for the Alert Center navigation.
     * Values: DPM, STOCK, CASE, PERMISSION, USER, SLA, SYSTEM
     * Null on legacy records — inferred at read time from eventType/actionType.
     */
    private AlertCategory category;

    /**
     * Action hint for the Alert Center action button.
     * Values: OPEN_CASE, OPEN_DPM, OPEN_STOCK, OPEN_PERMISSION_REQUEST,
     *         OPEN_USER, OPEN_THRESHOLDS, NONE
     */
    private String actionType;

    /** Related entity IDs for deep-link navigation from the Alert Center. */
    private String relatedCaseId;
    private String relatedStockRequestId;
    private String relatedPermissionRequestId;
}
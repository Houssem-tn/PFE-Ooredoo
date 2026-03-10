package com.pfe.Entity;

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
}
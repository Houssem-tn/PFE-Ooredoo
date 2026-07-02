package com.pfe.case_management.messaging;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One chat message inside a case discussion between the case ZM and the assigned STT.
 * Every message is permanently linked to its case so the whole exchange stays traceable
 * and auditable (admins can read any thread from the case detail).
 */
@Document(collection = "case_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseMessage {

    @Id
    private String id;

    private String caseId;
    /** Human-readable case reference (CRM case number) snapshot for audit/notifications. */
    private String caseReference;
    /** Case type snapshot (ACTIVATION / PLAINTE) so unread badges can route to the right board. */
    private String caseType;
    /** Module snapshot (PLT / RES) distinguishing complaint vs resiliation boards. */
    private String caseModule;

    private String senderId;
    private String senderName;
    private String senderEmail;
    /** ROLE_ZM or ROLE_STT */
    private String senderRole;

    private String receiverId;
    private String receiverName;
    private String receiverEmail;
    /** ROLE_ZM or ROLE_STT */
    private String receiverRole;

    private String content;

    // Optional photo attachment (stored through FileStorageService, image-only).
    private String attachmentFileId;
    private String attachmentFilename;
    private String attachmentContentType;

    private boolean read;
    private Instant readAt;

    private Instant createdAt;
}

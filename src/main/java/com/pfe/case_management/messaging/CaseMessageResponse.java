package com.pfe.case_management.messaging;

import java.time.Instant;

public record CaseMessageResponse(
        String id,
        String caseId,
        String caseReference,
        String senderId,
        String senderName,
        String senderEmail,
        String senderRole,
        String receiverId,
        String receiverName,
        String receiverRole,
        String content,
        String attachmentFilename,
        String attachmentContentType,
        String attachmentViewUrl,
        String attachmentDownloadUrl,
        boolean read,
        Instant readAt,
        Instant createdAt,
        /** True when the current authenticated user is the sender (drives chat bubble alignment). */
        boolean mine
) {
}

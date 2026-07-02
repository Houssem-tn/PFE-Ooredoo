package com.pfe.case_management.messaging;

import java.time.Instant;
import java.util.List;

public record CaseMessageUnreadSummaryResponse(
        long totalUnread,
        List<CaseUnread> cases
) {
    public record CaseUnread(
            String caseId,
            String caseReference,
            String caseType,
            String caseModule,
            long unreadCount,
            Instant lastMessageAt
    ) {
    }
}

package com.pfe.case_management.messaging;

public record MarkMessagesReadResponse(
        String caseId,
        long markedRead
) {
}

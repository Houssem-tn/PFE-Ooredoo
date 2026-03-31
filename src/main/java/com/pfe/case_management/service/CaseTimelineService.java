package com.pfe.case_management.service;

import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.model.CaseTimelineEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CaseTimelineService {

    public void append(
            CaseEntity caseEntity,
            CaseTimelineEventType eventType,
            String title,
            String details,
            String actor,
            CaseStatus previousStatus,
            CaseStatus newStatus,
            Map<String, Object> data
    ) {
        if (caseEntity == null || eventType == null) {
            return;
        }

        if (caseEntity.getTimeline() == null) {
            caseEntity.setTimeline(new ArrayList<>());
        }

        CaseTimelineEvent event = CaseTimelineEvent.builder()
                .id(UUID.randomUUID().toString())
                .occurredAt(Instant.now())
                .eventType(eventType)
                .title(safe(title))
                .details(safe(details))
                .actor(safe(actor))
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .data(copyData(data))
                .build();

        caseEntity.getTimeline().add(event);
    }

    private Map<String, Object> copyData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }

        return new LinkedHashMap<>(data);
    }

    private String safe(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

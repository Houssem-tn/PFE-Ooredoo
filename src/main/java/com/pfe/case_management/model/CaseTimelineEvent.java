package com.pfe.case_management.model;

import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseTimelineEvent {

    private String id;
    private Instant occurredAt;
    private CaseTimelineEventType eventType;
    private String title;
    private String details;
    private String actor;
    private CaseStatus previousStatus;
    private CaseStatus newStatus;
    private Map<String, Object> data;
}

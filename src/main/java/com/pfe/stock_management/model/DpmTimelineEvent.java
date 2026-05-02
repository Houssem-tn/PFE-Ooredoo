package com.pfe.stock_management.model;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpmTimelineEvent {

    private String eventType;
    private String description;
    private String performedBy;
    private String performedByRole;
    private Instant occurredAt;
}

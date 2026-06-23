package com.pfe.stock_management.material_return;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnTimelineEvent {

    private String eventType;    // SUBMITTED, SCHEDULE_PROPOSED, RESCHEDULE_REQUESTED, SCHEDULE_CONFIRMED, REJECTED
    private String description;
    private String actor;        // email of the user who triggered the event
    private String actorName;
    private Instant timestamp;
    private String note;         // optional extra context (e.g., rejection comment)
}

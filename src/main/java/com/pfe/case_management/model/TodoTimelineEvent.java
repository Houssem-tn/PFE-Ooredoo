package com.pfe.case_management.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoTimelineEvent {
    private String action;
    private String note;
    private Instant timestamp;
    private String actorEmail;
}

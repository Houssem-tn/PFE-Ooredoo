package com.pfe.stock_management.dto;

import com.pfe.stock_management.model.InstanceMovementEvent;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/** One movement step in a tracked unit's history, projected for the audit UI. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstanceMovementDto {

    private String type;
    private String fromOwnerType;
    private String fromOwnerName;
    private String toOwnerType;
    private String toOwnerName;
    private String resultingStatus;
    private String caseReference;
    private String dpmNumber;
    private String returnCode;
    private String note;
    private Instant occurredAt;
    private String performedBy;

    public static InstanceMovementDto from(InstanceMovementEvent m) {
        return InstanceMovementDto.builder()
                .type(m.getType())
                .fromOwnerType(m.getFromOwnerType() != null ? m.getFromOwnerType().name() : null)
                .fromOwnerName(m.getFromOwnerName())
                .toOwnerType(m.getToOwnerType() != null ? m.getToOwnerType().name() : null)
                .toOwnerName(m.getToOwnerName())
                .resultingStatus(m.getResultingStatus() != null ? m.getResultingStatus().name() : null)
                .caseReference(m.getCaseReference())
                .dpmNumber(m.getDpmNumber())
                .returnCode(m.getReturnCode())
                .note(m.getNote())
                .occurredAt(m.getOccurredAt())
                .performedBy(m.getPerformedBy())
                .build();
    }

    public static List<InstanceMovementDto> from(List<InstanceMovementEvent> events) {
        if (events == null) {
            return List.of();
        }
        return events.stream().map(InstanceMovementDto::from).collect(Collectors.toList());
    }
}

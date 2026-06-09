package com.pfe.stock_management.material_return;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialReturnResponseDto {

    private String id;
    private String returnCode;

    // STT
    private String sttId;
    private String sttName;
    private String sttEmail;

    // ZM
    private String zmId;
    private String zmName;
    private String zmEmail;

    private String sourceDpmId;

    // Case link
    private String relatedCaseId;
    private String relatedCaseType;
    private String crmCase;

    // Return classification
    private ReturnSource returnSource;
    private ReturnReason reason;
    private ReturnCondition condition;
    private ReturnDestination destination;

    private List<ReturnedItem> items;
    private String comment;
    private List<String> proofImageUrls;

    // Status
    private MaterialReturnStatus status;

    // ZM: proposed schedule
    private String proposedHandoverDate;
    private String proposedHandoverTime;
    private String proposedHandoverNote;
    private Instant scheduleProposedAt;

    // STT: schedule response
    private String sttScheduleResponseComment;
    private Instant scheduleConfirmedAt;
    private Instant scheduleRejectedAt;

    // ZM: rejection of return itself
    private String reviewComment;
    private String reviewedByZmId;
    private String reviewedByZmName;
    private Instant reviewedAt;

    // Physical handover completion
    private Instant handedOverAt;
    private String handedOverNote;

    // Timestamps
    private Instant createdAt;
    private Instant submittedAt;

    // Audit timeline
    private List<ReturnTimelineEvent> timeline;
}

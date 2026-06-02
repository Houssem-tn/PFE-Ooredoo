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

    private String sttId;
    private String sttName;
    private String sttEmail;

    private String zmId;
    private String zmName;
    private String zmEmail;

    private String relatedCaseId;
    private String relatedCaseType;
    private String crmCase;

    private ReturnSource returnSource;
    private String plannedReturnDate;

    private ReturnReason reason;
    private ReturnCondition condition;
    private ReturnDestination destination;

    private List<ReturnedItem> items;
    private String comment;
    private List<String> proofImageUrls;

    private MaterialReturnStatus status;

    private Instant createdAt;
    private Instant submittedAt;
    private Instant reviewedAt;
    private Instant closedAt;

    private String reviewedByZmId;
    private String reviewedByZmName;
    private String reviewComment;
}

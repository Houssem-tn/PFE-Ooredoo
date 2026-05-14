package com.pfe.stock_management.dto;

import com.pfe.stock_management.enums.DistributionStatus;
import com.pfe.stock_management.model.DistributionItem;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class PendingDistributionDto {
    private String id;
    private String sttId;
    private String sttName;
    private String sttEmail;
    private String techId;
    private String techName;
    private String dpmId;
    private List<DistributionItem> items;
    private DistributionStatus status;
    private Instant distributedAt;
    private String notes;
}

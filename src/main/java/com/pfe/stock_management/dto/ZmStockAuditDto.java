package com.pfe.stock_management.dto;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZmStockAuditDto {
    private String zmId;
    private String zmName;
    private String zmEmail;

    private int totalDpms;
    private long pendingCount;
    private long confirmedCount;
    private long rejectedCount;
    private long cancelledCount;
    private long warningCount;

    private List<ZmDpmAuditItem> dpms;
}

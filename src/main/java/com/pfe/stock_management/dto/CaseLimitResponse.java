package com.pfe.stock_management.dto;

import com.pfe.stock_management.entity.StockThreshold;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CaseLimitResponse {

    private String itemId;
    private String itemName;
    private String unit;
    private double caseUsageLimit;
    private boolean caseUsageLimitEnabled;

    public static CaseLimitResponse from(StockThreshold t) {
        CaseLimitResponse r = new CaseLimitResponse();
        r.itemId = t.getItemId();
        r.itemName = t.getItemName();
        r.unit = t.getUnit();
        r.caseUsageLimit = t.getCaseUsageLimit() != null ? t.getCaseUsageLimit() : 0;
        r.caseUsageLimitEnabled = t.isCaseUsageLimitEnabled();
        return r;
    }
}

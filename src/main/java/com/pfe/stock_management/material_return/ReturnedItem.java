package com.pfe.stock_management.material_return;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnedItem {

    private String equipmentId;
    private String equipmentName;
    private String articleCode;
    private String description;
    private String unit;
    private int quantity;

    // Optional identifiers — not enforced in MVP
    private String serialNumber;
    private String macAddress;
    private String imei;

    private ReturnCondition condition;
}

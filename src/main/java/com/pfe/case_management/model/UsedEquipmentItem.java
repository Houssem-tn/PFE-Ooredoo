package com.pfe.case_management.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsedEquipmentItem {
    private String equipmentId;
    private String equipmentName;
    private String unit;
    private int quantity;
}

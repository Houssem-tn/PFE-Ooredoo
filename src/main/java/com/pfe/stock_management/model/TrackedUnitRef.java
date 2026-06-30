package com.pfe.stock_management.model;

import lombok.*;

/**
 * Denormalised reference to a tracked {@link com.pfe.stock_management.entity.EquipmentInstance},
 * embedded on distributions / usages so the IMEI / Serial of the exact unit moved is visible
 * for display and audit without a second lookup.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedUnitRef {

    private String instanceId;
    private String imei;
    private String serialNumber;
}

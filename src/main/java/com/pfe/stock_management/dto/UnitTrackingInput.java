package com.pfe.stock_management.dto;

import lombok.*;

/**
 * One physical-unit detail row entered by an STT while confirming reception of a tracked
 * equipment line on a DPM. Which of {@code imei} / {@code serialNumber} are required is driven
 * by the equipment's {@link com.pfe.stock_management.enums.TrackingMode} and validated
 * server-side in {@code EquipmentInstanceService}. All fields are optional at the wire level.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitTrackingInput {

    private String imei;
    private String serialNumber;
    private String note;
}

package com.pfe.stock_management.dto;

import com.pfe.stock_management.entity.EquipmentInstance;
import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * Read-only audit projection of a tracked {@link EquipmentInstance} for the Admin / ZM
 * "Unit Tracking" page. Carries the per-unit identifiers, source DPM, current owner, status and
 * related case/return. The full {@code movements} history is populated only for the detail view
 * (see {@link #fromDetail}); the list view leaves it empty for a lighter payload.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentInstanceDto {

    private String id;
    private String equipmentId;
    private String equipmentName;
    private String articleCode;
    private String trackingMode;

    private String imei;
    private String serialNumber;

    private String sourceDpmId;
    private String sourceDpmNumber;
    private String originSttId;

    private String currentOwnerType;
    private String currentOwnerId;
    private String currentOwnerName;
    private String status;

    private String caseId;
    private String caseReference;
    private String returnId;
    private String returnCode;

    private Instant createdAt;
    private Instant updatedAt;

    /** Full movement history — populated only for the single-instance detail endpoint. */
    private List<InstanceMovementDto> movements;

    public static EquipmentInstanceDto from(EquipmentInstance i) {
        return base(i).build();
    }

    public static EquipmentInstanceDto fromDetail(EquipmentInstance i) {
        return base(i)
                .movements(InstanceMovementDto.from(i.getMovements()))
                .build();
    }

    private static EquipmentInstanceDtoBuilder base(EquipmentInstance i) {
        return EquipmentInstanceDto.builder()
                .id(i.getId())
                .equipmentId(i.getEquipmentId())
                .equipmentName(i.getEquipmentName())
                .articleCode(i.getArticleCode())
                .trackingMode(i.getTrackingMode() != null ? i.getTrackingMode().name() : null)
                .imei(i.getImei())
                .serialNumber(i.getSerialNumber())
                .sourceDpmId(i.getSourceDpmId())
                .sourceDpmNumber(i.getSourceDpmNumber())
                .originSttId(i.getOriginSttId())
                .currentOwnerType(i.getCurrentOwnerType() != null ? i.getCurrentOwnerType().name() : null)
                .currentOwnerId(i.getCurrentOwnerId())
                .currentOwnerName(i.getCurrentOwnerName())
                .status(i.getStatus() != null ? i.getStatus().name() : null)
                .caseId(i.getCaseId())
                .caseReference(i.getCaseReference())
                .returnId(i.getReturnId())
                .returnCode(i.getReturnCode())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt());
    }
}

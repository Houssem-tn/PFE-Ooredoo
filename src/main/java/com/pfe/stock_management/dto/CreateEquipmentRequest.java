package com.pfe.stock_management.dto;

import com.pfe.stock_management.enums.EquipmentCategory;
import com.pfe.stock_management.enums.StockUnit;
import com.pfe.stock_management.enums.TrackingMode;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateEquipmentRequest {

    @NotBlank
    private String name;

    private String articleCode;

    private String description;
    private StockUnit unit;
    private EquipmentCategory category;

    // ── Business usage classification ────────────────────────────────────────
    // Defaults preserve legacy behaviour when an older client omits them:
    // usable in ACT and PLT, not a pack item, not recoverable.
    private boolean packItem = false;
    private boolean usableInAct = true;
    private boolean usableInPlt = true;
    private boolean recoverableFromClient = false;

    // ── Per-unit tracking ────────────────────────────────────────────────────
    // Defaults to NONE so older clients that omit it keep creating quantity-based items.
    private TrackingMode trackingMode = TrackingMode.NONE;
}

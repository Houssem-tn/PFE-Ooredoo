package com.pfe.stock_management.material_return;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProposeHandoverDto {

    @NotBlank(message = "Handover date is required (YYYY-MM-DD)")
    private String handoverDate; // "YYYY-MM-DD"

    @NotBlank(message = "Handover time is required (HH:mm)")
    private String handoverTime; // "HH:mm"

    private String handoverNote; // optional instructions for STT
}

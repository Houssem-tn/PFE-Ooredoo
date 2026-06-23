package com.pfe.stock_management.material_return;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SttScheduleResponseDto {

    /** true = STT accepts the proposed schedule; false = STT requests reschedule */
    private Boolean accepted;

    /** Required when accepted=false; optional when accepted=true */
    private String comment;

    public boolean isAccepted() {
        return Boolean.TRUE.equals(accepted);
    }
}

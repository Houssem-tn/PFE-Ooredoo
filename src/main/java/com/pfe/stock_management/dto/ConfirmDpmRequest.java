package com.pfe.stock_management.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmDpmRequest {

    @NotEmpty
    @Valid
    private List<DpmItemConfirmation> items;

    private String rejectReason;
}

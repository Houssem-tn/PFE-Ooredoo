package com.pfe.stock_management.dto;

import com.pfe.stock_management.enums.DpmStatus;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributableDpmDto {
    private String id;
    private String dpmNumber;
    private String zmName;
    private DpmStatus status;
    private Instant createdAt;
    private Instant confirmedAt;
    private List<DistributableDpmItemDto> items;
}

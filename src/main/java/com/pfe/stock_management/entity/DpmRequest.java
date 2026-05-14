package com.pfe.stock_management.entity;

import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.DpmTimelineEvent;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "stock_dpm_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpmRequest {

    @Id
    private String id;

    @Indexed(unique = true)
    private String dpmNumber;

    private String zmId;
    private String zmName;
    private String zmEmail;

    private String sttId;
    private String sttName;
    private String sttEmail;

    @Builder.Default
    private DpmStatus status = DpmStatus.PENDING_STT_CONFIRMATION;

    @Builder.Default
    private List<DpmItem> items = new ArrayList<>();

    private String notes;
    private String deliveryPersonName;

    @CreatedDate
    private Instant createdAt;

    private Instant confirmedAt;

    @Builder.Default
    private List<DpmTimelineEvent> timeline = new ArrayList<>();
}

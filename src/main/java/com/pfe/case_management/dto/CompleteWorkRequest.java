package com.pfe.case_management.dto;

import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;

public record CompleteWorkRequest(
        String repTravauxStt,
        Instant dateFinTrv,
        String cmtTrvStt,
        Double metrageCable,
        Integer nombrePv,
        Double debitDl,
        Double debitUl,
        Double rsrp,
        Double rsrq,
        Double sinr,
        Double locationLatitude,
        Double locationLongitude,
        Instant locationCapturedAt,
        Double locationAccuracyMeters,
        @Valid List<UsedEquipmentRequestItem> usedEquipment
) {
}

package com.pfe.case_management.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;

/**
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}: the field UI sends extra client-only fields
 * (e.g. {@code equipmentProofs}, used only to pre-fill the OCR-assisted photo step before those
 * photos are uploaded separately via {@code /work-proof}) that have no matching component here.
 * The app's {@code ObjectMapper} bean has no lenient-deserialization override, so without this
 * annotation Jackson's default (fail on unknown properties) would 400 every submission.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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

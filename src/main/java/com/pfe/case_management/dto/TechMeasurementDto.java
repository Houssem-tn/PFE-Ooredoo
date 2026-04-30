package com.pfe.case_management.dto;

public record TechMeasurementDto(
        String imsi,
        String imei,
        String pci,
        String sinr,
        String rsrp,
        String rssi,
        String rsrq,
        Double descendantMbps,
        Double ascendantMbps
) {}
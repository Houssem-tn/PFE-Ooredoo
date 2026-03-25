package com.pfe.case_management.dto;


import java.time.Instant;


public record SetRdvRequest(
        boolean joignable,
        Instant datePriseRdv,
        String causeInjoignabilite,
        String detail,
        String cmtRdv,
        boolean abandon,
        String causeAbandon
) {
}
package com.pfe.ActDto;

import java.time.Instant;

public record SetRdvRequest(
        boolean joignable,              // if false => causeInjoignabilite required
        Instant datePriseRdv,
        String causeInjoignabilite,
        String detail,
        String cmtRdv,
        boolean abandon,                // if true => causeAbandon required + close failed
        String causeAbandon)
{ }

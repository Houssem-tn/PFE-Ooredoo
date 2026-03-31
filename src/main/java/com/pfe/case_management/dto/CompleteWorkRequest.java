package com.pfe.case_management.dto;

import java.time.Instant;

public record CompleteWorkRequest(
        String repTravauxStt,
        Instant dateFinTrv,
        String cmtTrvStt,
        Double metrageCable,
        Integer nombrePv,
        Double debitDl,
        Double debitUl,
        Integer rsrp,
        Integer rsrq,
        Integer sinr
) {
}
package com.pfe.MainPackage.ActDto;

public record UpdateMetricsRequest(
        Double metrageCable,
        Integer nombreDePv,
        Double debitDl,
        Double debitUl,
        Double rsrp,
        Double rsrq,
        Double sinr,
        String siteSecteur) {
}

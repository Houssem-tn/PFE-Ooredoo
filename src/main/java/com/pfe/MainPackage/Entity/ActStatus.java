package com.pfe.MainPackage.Entity;

public enum ActStatus {
    CREATED,                 // OPENING_DATE_SUR_TIMOS set
    ASSIGNED_TO_STT,         // DATE_AFFECTATION_STT set
    RDV_SET,                 // DATE_PRISE_RDV set + REP_RDV = OUI
    RDV_FAILED,              // REP_RDV = NON (cause injoignabilité)
    IN_EXECUTION,            // first evidence
    SUBMITTED,               // DATE_FIN_TRV set (tech finished evidence)
    PREVALIDATED_STT,        // REP_TRAVAUX_STT = OK
    REJECTED_STT,            // REP_TRAVAUX_STT = KO
    VALIDATED_ZM,            // REP_ZM = OUI
    REJECTED_ZM,             // REP_ZM = NON
    CLOSED_SUCCESS,
    CLOSED_FAILED
}

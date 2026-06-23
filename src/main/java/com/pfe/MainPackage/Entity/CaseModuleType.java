package com.pfe.MainPackage.Entity;

import com.pfe.case_management.enums.CaseType;

public enum CaseModuleType {
    ACT,
    RES,
    PLT;

    public CaseType toCaseType() {
        return switch (this) {
            case ACT -> CaseType.ACTIVATION;
            case RES -> CaseType.RESILIATION;
            case PLT -> CaseType.PLAINTE;
        };
    }

    public static CaseModuleType fromCaseType(CaseType caseType) {
        return switch (caseType) {
            case ACTIVATION -> ACT;
            case RESILIATION -> RES;
            case PLAINTE -> PLT;
        };
    }
}

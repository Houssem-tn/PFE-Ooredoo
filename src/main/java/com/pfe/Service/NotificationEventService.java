package com.pfe.Service;

import com.pfe.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;

public interface NotificationEventService {
    void onUserRegistered(AppUser user);

    void onCaseAssignedToZm(CaseEntity caseEntity, String triggeredBy);

    void onCaseAssignedToStt(CaseEntity caseEntity, String triggeredBy);

    void onCaseAssignedToTech(CaseEntity caseEntity, String triggeredBy);

    void onSlaWarning(CaseEntity caseEntity);

    void onSlaBreached(CaseEntity caseEntity);

    void onSlaEscalated(CaseEntity caseEntity, long breachAgeMinutes);
}
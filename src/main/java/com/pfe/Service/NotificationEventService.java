package com.pfe.Service;

import com.pfe.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;

public interface NotificationEventService {
    void onUserRegistered(AppUser user);

    void onCaseAssignedToZm(CaseEntity caseEntity, String triggeredBy);

    void onCaseAssignedToStt(CaseEntity caseEntity, String triggeredBy);

    void onCaseAssignedToTech(CaseEntity caseEntity, String triggeredBy);

    void onComplaintImportCompleted(String recipientEmail, int processedRows, int importedRows, int skippedRows);

    void onSlaWarning(CaseEntity caseEntity);

    void onSlaBreached(CaseEntity caseEntity);

    void onSlaEscalated(CaseEntity caseEntity, long breachAgeMinutes);

    void onDpmCreated(DpmRequest dpm);

    void onDpmConfirmed(DpmRequest dpm);

    void onDpmRejected(DpmRequest dpm);

    void onStockDistributedToTech(SttDistribution distribution);
}

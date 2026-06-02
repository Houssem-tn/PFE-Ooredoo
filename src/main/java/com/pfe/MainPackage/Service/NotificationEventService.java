package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.material_return.MaterialReturnRequest;

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

    /** ZM recorded external approval and scheduled RDV → notify STT */
    void onRdvScheduled(DpmRequest dpm);

    /** STT confirmed they will attend the RDV → notify ZM */
    void onRdvConfirmedByStt(DpmRequest dpm);

    /** ZM recorded external rejection from Team Stock → notify STT */
    void onExternalRejected(DpmRequest dpm);

    void onStockDistributedToTech(SttDistribution distribution);

    void onMaterialReturnSubmitted(MaterialReturnRequest returnRequest);

    void onMaterialReturnApproved(MaterialReturnRequest returnRequest);

    void onMaterialReturnRejected(MaterialReturnRequest returnRequest);
}

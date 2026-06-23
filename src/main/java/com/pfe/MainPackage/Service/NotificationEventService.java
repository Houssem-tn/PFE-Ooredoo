package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.case_management.dto.BulkCaseAssignmentSummary;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.material_return.MaterialReturnRequest;

public interface NotificationEventService {
    void onUserRegistered(AppUser user);

    void onCaseAssignedToZm(CaseEntity caseEntity, String triggeredBy);

    /**
     * Emits ONE aggregated notification for a whole bulk case-assignment batch (PLT/RES injection
     * or admin activation distribution) targeting a single ZM, instead of one alert per case.
     */
    void onCasesBulkAssignedToZm(BulkCaseAssignmentSummary summary);

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

    /** ZM rejected the return request itself → notify STT */
    void onMaterialReturnRejected(MaterialReturnRequest returnRequest);

    /** ZM proposed a handover schedule → notify STT */
    void onHandoverScheduleProposed(MaterialReturnRequest returnRequest);

    /** STT accepted the proposed schedule → notify ZM */
    void onScheduleAcceptedByStt(MaterialReturnRequest returnRequest);

    /** STT rejected the proposed schedule (reschedule requested) → notify ZM */
    void onScheduleRejectedByStt(MaterialReturnRequest returnRequest);

    /** ZM confirmed physical receipt of returned material → notify STT */
    void onMaterialHandedOver(MaterialReturnRequest returnRequest);

    /** Return fully closed after ZM handover confirmation → notify ZM (audit closure in Alert Center) */
    void onReturnClosedForZm(MaterialReturnRequest returnRequest);
}

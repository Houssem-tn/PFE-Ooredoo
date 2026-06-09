package com.pfe.stock_management.material_return;

public enum MaterialReturnStatus {

    /** Legacy value kept for backward compatibility with existing MongoDB documents */
    SUBMITTED,

    /** Legacy value kept for backward compatibility with existing MongoDB documents */
    PENDING,

    /** STT submitted the request; waiting for ZM initial review */
    PENDING_ZM_REVIEW,

    /** ZM proposed a handover date/time; waiting for STT confirmation */
    SCHEDULE_PROPOSED,

    /** Alternate waiting-for-STT-confirmation values kept for compatibility */
    PENDING_STT_CONFIRMATION,

    /** Alternate waiting-for-STT-confirmation values kept for compatibility */
    WAITING_STT_CONFIRMATION,

    /** Alternate waiting-for-STT-confirmation values kept for compatibility */
    SCHEDULE_PROPOSED_BY_ZM,

    /** STT rejected the proposed schedule; ZM must re-propose */
    RESCHEDULE_REQUESTED,

    /** Alternate reschedule-requested value kept for compatibility */
    SCHEDULE_REJECTED_BY_STT,

    /** Legacy value kept for backward compatibility with existing MongoDB documents */
    APPROVED,

    /** STT accepted the proposed schedule; handover is confirmed */
    SCHEDULE_CONFIRMED,

    /** ZM rejected the return request itself (material not accepted) */
    REJECTED,

    /** Physical handover completed */
    HANDED_OVER,

    /** Post-handover lifecycle values kept for compatibility */
    RECEIVED,

    /** Post-handover lifecycle values kept for compatibility */
    COMPLETED,

    /** Legacy closed value kept for backward compatibility with existing MongoDB documents */
    CLOSED
}

package com.pfe.case_management.model;

public enum ZmTodoWorkflowStatus {
    PENDING_ZM_REVIEW,
    READY_FOR_STT_ASSIGNMENT,
    ASSIGNED_TO_STT,
    ASSIGNED_TO_TECH,
    IN_PROGRESS,
    TECH_COMPLETED,
    STT_VALIDATED,
    ZM_VALIDATED,
    CLOSED,
    BLOCKED,
    REJECTED,
    RESCHEDULE_REQUESTED
}

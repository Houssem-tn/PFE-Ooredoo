package com.pfe.case_management.mapper;

import com.pfe.case_management.dto.PlainteDTO;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.PlainteStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlainteMapper {

    @Mapping(target = "status", expression = "java(toPlainteStatus(entity.getStatus()))")
    PlainteDTO toDto(CaseEntity entity);

    default PlainteStatus toPlainteStatus(CaseStatus status) {
        if (status == null) {
            return null;
        }

        return switch (status) {
            case CREATED -> PlainteStatus.CREATED;
            case ASSIGNED_TO_ZM -> PlainteStatus.ASSIGNED_TO_ZM;
            case ASSIGNED_TO_STT -> PlainteStatus.ASSIGNED_TO_STT;
            case ASSIGNED_TO_TECH -> PlainteStatus.ASSIGNED_TO_TECH;
            case RDV_SET, NOT_REACHABLE, ABANDONED, DIAGNOSIS_STEP -> PlainteStatus.DIAGNOSIS_STEP;
            case WORK_DONE, RESOLUTION_SUBMITTED -> PlainteStatus.RESOLUTION_SUBMITTED;
            case ESCALATED -> PlainteStatus.ESCALATED;
            case RETURNED_TO_STT -> PlainteStatus.RETURNED_TO_STT;
            case REOPENED -> PlainteStatus.REOPENED;
            case VALIDATED_BY_ZM -> PlainteStatus.VALIDATED_BY_ZM;
            case REJECTED_BY_ZM -> PlainteStatus.REJECTED;
            case CLOSED -> PlainteStatus.CLOSED;
        };
    }
}

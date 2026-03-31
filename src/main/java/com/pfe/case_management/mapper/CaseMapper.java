package com.pfe.case_management.mapper;

import com.pfe.case_management.dto.CaseResponse;
import com.pfe.case_management.entity.CaseEntity;

public class CaseMapper {

    public static CaseResponse toResponse(CaseEntity c) {

        return new CaseResponse(
                c.getId(),
                c.getType(),
                c.getStatus(),

                c.getCrmCase(),
                c.getDateCreationCrm(),
                c.getOpeningDateTimos(),

                c.getClientName(),
                c.getClientPhone(),
                c.getMsisdn(),

                c.getLocalite(),
                c.getDelegation(),
                c.getGouvernorat(),

                c.getSttName(),

                c.getRdv() != null ? c.getRdv().getJoignable() : null,
                c.getRdv() != null ? c.getRdv().getDatePriseRdv() : null,
                c.getRdv() != null ? c.getRdv().getCauseInjoignabilite() : null,
                c.getRdv() != null ? c.getRdv().getCauseAbandon() : null,

                c.getWork() != null ? c.getWork().getRepTravauxStt() : null,
                c.getWork() != null ? c.getWork().getDateFinTrv() : null,

                c.getZmValidation() != null ? c.getZmValidation().getCmtZm() : null,

                c.getCreatedAt()
        );
    }
}
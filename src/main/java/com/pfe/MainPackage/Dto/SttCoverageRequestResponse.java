package com.pfe.MainPackage.Dto;

import java.util.List;

/**
 * One STT that is pending coverage confirmation for the calling ZM, together with the
 * exact governorates still awaiting <em>this</em> ZM's decision and the module(s) the ZM
 * covers them for (e.g. ACT / Sousse for one ZM, PLT / Sousse for another).
 */
public record SttCoverageRequestResponse(
        UserDTO stt,
        List<PendingGovernorateCoverage> pending
) {
    public record PendingGovernorateCoverage(
            String governorate,
            List<String> modules
    ) {
    }
}

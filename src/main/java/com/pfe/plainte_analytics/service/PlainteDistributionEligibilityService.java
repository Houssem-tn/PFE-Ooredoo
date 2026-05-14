package com.pfe.plainte_analytics.service;

import com.pfe.plainte_analytics.model.PlainteImportSharedRow;
import org.springframework.stereotype.Service;

@Service
public class PlainteDistributionEligibilityService {

    public boolean isActionable(PlainteImportSharedRow row) {
        return row != null && row.distributionCandidateRow();
    }

    public boolean isSourceCompleted(PlainteImportSharedRow row) {
        return row != null
                && row.distributionCandidateRow()
                && isEffectue(row);
    }

    public boolean isSourceTerminated(PlainteImportSharedRow row) {
        return row != null
                && row.distributionCandidateRow()
                && PlainteAnalyticsSupport.containsKeyword(row.statut(), "termine");
    }

    public boolean isEnCours(PlainteImportSharedRow row) {
        return row != null && PlainteAnalyticsSupport.containsKeyword(row.statut(), "en cours");
    }

    public boolean isEffectue(PlainteImportSharedRow row) {
        return row != null && PlainteAnalyticsSupport.containsKeyword(row.repTravauxStt(), "effectue");
    }
}

package com.pfe.activation_analytics.service;

import com.pfe.activation_analytics.model.ActivationImportSharedRow;
import org.springframework.stereotype.Service;

@Service
public class ActivationDistributionEligibilityService {

    public boolean isActionable(ActivationImportSharedRow row) {
        return row != null
                && row.distributionCandidateRow()
                && isEnCours(row)
                && !isEffectue(row);
    }

    public boolean isSourceCompleted(ActivationImportSharedRow row) {
        return row != null
                && row.distributionCandidateRow()
                && isEnCours(row)
                && isEffectue(row);
    }

    public boolean isSourceTerminated(ActivationImportSharedRow row) {
        return row != null
                && row.distributionCandidateRow()
                && ActivationAnalyticsSupport.containsKeyword(row.statut(), "termine");
    }

    public boolean isEnCours(ActivationImportSharedRow row) {
        return row != null && ActivationAnalyticsSupport.containsKeyword(row.statut(), "en cours");
    }

    public boolean isEffectue(ActivationImportSharedRow row) {
        return row != null && ActivationAnalyticsSupport.containsKeyword(row.repTravauxStt(), "effectue");
    }
}

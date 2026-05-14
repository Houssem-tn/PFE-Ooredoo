package com.pfe.activation_analytics.model;

public enum ActivationImportProcessingMode {
    ANALYTICS,
    DISTRIBUTION,
    ANALYTICS_AND_DISTRIBUTION;

    public boolean analyticsEnabled() {
        return this == ANALYTICS || this == ANALYTICS_AND_DISTRIBUTION;
    }

    public boolean distributionEnabled() {
        return this == DISTRIBUTION || this == ANALYTICS_AND_DISTRIBUTION;
    }
}

package com.pfe.merged_analytics.model;

import java.util.Locale;

public final class MergedSourceLabel {

    private MergedSourceLabel() {}

    public static String resolveLabel(String source) {
        if (source == null || source.isBlank()) return null;
        return switch (source.trim().toUpperCase(Locale.ROOT)) {
            case "MES", "MSE" -> "Activation";
            case "DMG"        -> "Déménagement";
            case "PLT"        -> "Plainte";
            case "RES"        -> "Résiliation";
            default           -> null;
        };
    }

    public static boolean isValid(String source) {
        return resolveLabel(source) != null;
    }

    public static String normalizeSource(String source) {
        if (source == null || source.isBlank()) return null;
        String upper = source.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "MSE" -> "MES";
            default    -> upper;
        };
    }
}

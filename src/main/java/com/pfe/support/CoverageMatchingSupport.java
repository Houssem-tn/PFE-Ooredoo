package com.pfe.support;

import com.pfe.MainPackage.Entity.AppUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Pure, dependency-free helpers that derive the STT ↔ ZM relationship from
 * <em>shared governorate + module coverage</em> instead of the removed one-to-one
 * {@code assignedZm} link.
 *
 * <p>Definitions used throughout the app:
 * <ul>
 *   <li><b>Effective ZM territory</b> = legacy {@code assignedGovernorates} (ACT default)
 *       ∪ every governorate list in {@code coverageByModule} (ACT / PLT / RES).</li>
 *   <li><b>Declared STT coverage</b> = {@code coveredGovernorates} (already approved by
 *       at least one ZM, or admin-assigned) ∪ {@code requestedGovernorates} (still pending).</li>
 *   <li>A ZM's decision for an STT is tracked per-ZM in
 *       {@code coverageApprovedByZm}/{@code coverageDeclinedByZm} so decisions stay independent.</li>
 * </ul>
 * All comparisons are canonical (accent/alias aware) via {@link TunisiaGovernorateSupport}.
 */
public final class CoverageMatchingSupport {

    /** Modules a governorate can be covered for, in display order. */
    public static final List<String> MODULE_ORDER = List.of("ACT", "PLT", "RES");

    private CoverageMatchingSupport() {
    }

    /** assignedGovernorates ∪ all coverageByModule values, canonicalised + deduped. */
    public static List<String> effectiveZmGovernorates(AppUser zm) {
        if (zm == null) {
            return List.of();
        }

        LinkedHashSet<String> combined = new LinkedHashSet<>(safe(zm.getAssignedGovernorates()));
        Map<String, List<String>> coverage = zm.getCoverageByModule();
        if (coverage != null) {
            for (List<String> moduleGovs : coverage.values()) {
                combined.addAll(safe(moduleGovs));
            }
        }
        return List.copyOf(combined);
    }

    /** The STT's approved coverage ({@code coveredGovernorates}), canonicalised. */
    public static List<String> safeCovered(AppUser stt) {
        return stt == null ? List.of() : safe(stt.getCoveredGovernorates());
    }

    /** coveredGovernorates ∪ requestedGovernorates, canonicalised + deduped. */
    public static List<String> declaredSttCoverage(AppUser stt) {
        if (stt == null) {
            return List.of();
        }

        LinkedHashSet<String> combined = new LinkedHashSet<>(safe(stt.getCoveredGovernorates()));
        combined.addAll(safe(stt.getRequestedGovernorates()));
        return List.copyOf(combined);
    }

    /** Governorates this ZM has already acted on (approved or declined) for the STT. */
    public static List<String> zmDecisionsForStt(AppUser stt, String zmId) {
        if (stt == null || zmId == null || zmId.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> decided = new LinkedHashSet<>(safe(mapValue(stt.getCoverageApprovedByZm(), zmId)));
        decided.addAll(safe(mapValue(stt.getCoverageDeclinedByZm(), zmId)));
        return List.copyOf(decided);
    }

    /**
     * Governorates that are still <b>pending confirmation for this specific ZM</b>:
     * inside the ZM's effective territory, declared by the STT, and not yet decided by this ZM.
     * Because filtering is per-ZM, one ZM approving/declining never removes the item from another ZM.
     */
    public static List<String> pendingGovernoratesForZm(AppUser zm, AppUser stt) {
        if (zm == null || stt == null) {
            return List.of();
        }

        List<String> territory = effectiveZmGovernorates(zm);
        if (territory.isEmpty()) {
            return List.of();
        }

        List<String> decided = zmDecisionsForStt(stt, zm.getId());

        return declaredSttCoverage(stt).stream()
                .filter(territory::contains)
                .filter(governorate -> !decided.contains(governorate))
                .toList();
    }

    public static boolean hasOverlap(Collection<String> left, Collection<String> right) {
        List<String> leftGovs = safe(left);
        if (leftGovs.isEmpty()) {
            return false;
        }
        return safe(right).stream().anyMatch(leftGovs::contains);
    }

    public static boolean zmCoversGovernorate(AppUser zm, String governorate) {
        return TunisiaGovernorateSupport.containsEquivalent(effectiveZmGovernorates(zm), governorate);
    }

    /**
     * Modules for which this ZM covers the given governorate (e.g. {@code [ACT]} for ZM X,
     * {@code [PLT]} for ZM Y). Legacy {@code assignedGovernorates} is treated as ACT coverage.
     */
    public static List<String> modulesForGovernorate(AppUser zm, String governorate) {
        if (zm == null || governorate == null || governorate.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> modules = new LinkedHashSet<>();
        Map<String, List<String>> coverage = zm.getCoverageByModule();
        if (coverage != null) {
            for (Map.Entry<String, List<String>> entry : coverage.entrySet()) {
                if (TunisiaGovernorateSupport.containsEquivalent(entry.getValue(), governorate)) {
                    modules.add(entry.getKey());
                }
            }
        }

        if (TunisiaGovernorateSupport.containsEquivalent(zm.getAssignedGovernorates(), governorate)) {
            modules.add("ACT");
        }

        List<String> ordered = new ArrayList<>();
        for (String module : MODULE_ORDER) {
            if (modules.remove(module)) {
                ordered.add(module);
            }
        }
        ordered.addAll(modules); // any custom module keys, kept last
        return ordered;
    }

    private static List<String> mapValue(Map<String, List<String>> map, String key) {
        if (map == null) {
            return List.of();
        }
        List<String> value = map.get(key);
        return value != null ? value : List.of();
    }

    private static List<String> safe(Collection<String> governorates) {
        return TunisiaGovernorateSupport.normalizeStoredValues(governorates);
    }
}

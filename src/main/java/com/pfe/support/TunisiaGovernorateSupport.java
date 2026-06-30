package com.pfe.support;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TunisiaGovernorateSupport {
    private static final List<String> TUNISIA_GOVERNORATES = List.of(
            "Tunis",
            "Ariana",
            "Ben Arous",
            "Manouba",
            "Nabeul",
            "Zaghouan",
            "Bizerte",
            "Beja",
            "Jendouba",
            "Kef",
            "Siliana",
            "Sousse",
            "Monastir",
            "Mahdia",
            "Sfax",
            "Kairouan",
            "Kasserine",
            "Sidi Bouzid",
            "Gabes",
            "Medenine",
            "Tataouine",
            "Gafsa",
            "Tozeur",
            "Kebili"
    );
    private static final Set<String> TUNISIA_GOVERNORATE_SET = Set.copyOf(TUNISIA_GOVERNORATES);
    private static final Map<String, String> TUNISIA_GOVERNORATE_ALIASES = buildAliases();
    private static final List<Map.Entry<String, String>> SORTED_ALIASES = TUNISIA_GOVERNORATE_ALIASES.entrySet()
            .stream()
            .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
            .toList();

    private TunisiaGovernorateSupport() {
    }

    public static List<String> orderedGovernorates() {
        return TUNISIA_GOVERNORATES;
    }

    public static String canonicalize(String value) {
        String normalized = normalizeLookupValue(value);
        if (normalized == null) {
            return null;
        }

        String exactMatch = TUNISIA_GOVERNORATE_ALIASES.get(normalized);
        if (exactMatch != null) {
            return exactMatch;
        }

        for (Map.Entry<String, String> entry : SORTED_ALIASES) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return toTitleCase(normalized);
    }

    public static String requireSupported(String value) {
        String canonical = canonicalize(value);
        if (canonical == null || !TUNISIA_GOVERNORATE_SET.contains(canonical)) {
            throw new IllegalArgumentException("Unsupported governorate: " + safeLabel(value));
        }
        return canonical;
    }

    public static boolean containsEquivalent(Collection<String> governorates, String governorate) {
        String canonicalTarget = canonicalize(governorate);
        if (canonicalTarget == null) {
            return false;
        }

        if (governorates == null || governorates.isEmpty()) {
            return false;
        }

        for (String candidate : governorates) {
            if (canonicalTarget.equals(canonicalize(candidate))) {
                return true;
            }
        }

        return false;
    }

    public static List<String> normalizeSelection(Collection<String> governorates) {
        if (governorates == null || governorates.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> selectedGovernorates = new LinkedHashSet<>();
        for (String governorate : governorates) {
            String cleaned = clean(governorate);
            if (cleaned == null) {
                throw new IllegalArgumentException("Governorate names cannot be blank");
            }
            selectedGovernorates.add(requireSupported(cleaned));
        }

        return TUNISIA_GOVERNORATES.stream()
                .filter(selectedGovernorates::contains)
                .toList();
    }

    public static List<String> normalizeStoredValues(Collection<String> governorates) {
        if (governorates == null || governorates.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> supportedGovernorates = new LinkedHashSet<>();
        LinkedHashSet<String> customGovernorates = new LinkedHashSet<>();

        for (String governorate : governorates) {
            String cleaned = clean(governorate);
            if (cleaned == null) {
                continue;
            }

            String canonical = canonicalize(cleaned);
            if (canonical == null) {
                continue;
            }

            if (TUNISIA_GOVERNORATE_SET.contains(canonical)) {
                supportedGovernorates.add(canonical);
            } else {
                customGovernorates.add(canonical);
            }
        }

        List<String> orderedGovernorates = new ArrayList<>(TUNISIA_GOVERNORATES.stream()
                .filter(supportedGovernorates::contains)
                .toList());
        orderedGovernorates.addAll(customGovernorates);
        return List.copyOf(orderedGovernorates);
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        registerAlias(aliases, "Tunis", "Tunis");
        registerAlias(aliases, "Ariana", "Ariana");
        registerAlias(aliases, "Ben Arous", "Ben Arous");
        registerAlias(aliases, "Manouba", "Manouba", "Mannouba");
        registerAlias(aliases, "Nabeul", "Nabeul");
        registerAlias(aliases, "Zaghouan", "Zaghouan");
        registerAlias(aliases, "Bizerte", "Bizerte");
        registerAlias(aliases, "Beja", "Beja", "Béja", "BÃ©ja");
        registerAlias(aliases, "Jendouba", "Jendouba");
        registerAlias(aliases, "Kef", "Kef", "Le Kef");
        registerAlias(aliases, "Siliana", "Siliana");
        registerAlias(aliases, "Sousse", "Sousse");
        registerAlias(aliases, "Monastir", "Monastir");
        registerAlias(aliases, "Mahdia", "Mahdia");
        registerAlias(aliases, "Sfax", "Sfax");
        registerAlias(aliases, "Kairouan", "Kairouan");
        registerAlias(aliases, "Kasserine", "Kasserine");
        registerAlias(aliases, "Sidi Bouzid", "Sidi Bouzid");
        registerAlias(aliases, "Gabes", "Gabes", "Gabès", "GabÃ¨s");
        registerAlias(aliases, "Medenine", "Medenine", "Mednine", "Médenine", "MÃ©denine");
        registerAlias(aliases, "Tataouine", "Tataouine");
        registerAlias(aliases, "Gafsa", "Gafsa");
        registerAlias(aliases, "Tozeur", "Tozeur");
        registerAlias(aliases, "Kebili", "Kebili", "Kébili", "KÃ©bili");
        return Map.copyOf(aliases);
    }

    private static void registerAlias(Map<String, String> aliases, String canonical, String... variants) {
        for (String variant : variants) {
            String normalizedVariant = normalizeLookupValue(variant);
            if (normalizedVariant != null) {
                aliases.put(normalizedVariant, canonical);
            }
        }
    }

    private static String normalizeLookupValue(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }

        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean previousWhitespace = false;

        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.getType(current) == Character.NON_SPACING_MARK) {
                continue;
            }

            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toLowerCase(current));
                previousWhitespace = false;
                continue;
            }

            if ((Character.isWhitespace(current) || current == '-' || current == '_' || current == '/')
                    && builder.length() > 0
                    && !previousWhitespace) {
                builder.append(' ');
                previousWhitespace = true;
            }
        }

        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == ' ') {
            builder.setLength(length - 1);
        }

        return builder.length() == 0 ? null : builder.toString();
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safeLabel(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "" : cleaned;
    }

    private static String toTitleCase(String value) {
        String[] segments = value.split(" ");
        StringBuilder builder = new StringBuilder(value.length());

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(segment.charAt(0)));
            if (segment.length() > 1) {
                builder.append(segment.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return builder.toString();
    }
}

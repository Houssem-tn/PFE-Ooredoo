package com.pfe.activation_analytics.service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ActivationAnalyticsSupport {

    static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATTERS = List.of(
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yyyy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MM-yyyy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MM-dd-yyyy").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yyyy H:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yyyy HH:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/yyyy h:mm a").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy H:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy HH:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/yyyy h:mm a").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd H:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd HH:mm").toFormatter(Locale.ROOT),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd HH:mm:ss").toFormatter(Locale.ROOT)
    );

    private static final Map<String, String> TUNISIA_GOVERNORATE_ALIASES = buildTunisiaGovernorateAliases();

    private ActivationAnalyticsSupport() {
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String cleanString(String value) {
        return hasText(value) ? value.trim().replaceAll("\\s+", " ") : null;
    }

    static String normalizeHeader(String value) {
        if (!hasText(value)) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .trim()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        return normalized;
    }

    static String normalizeLookupValue(String value) {
        if (!hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ");

        return normalized.isBlank() ? null : normalized;
    }

    static String normalizeSttName(String value) {
        String cleaned = cleanString(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    static String canonicalGovernorate(String value) {
        String normalized = normalizeLookupValue(value);
        if (!hasText(normalized)) {
            return null;
        }

        String exactMatch = TUNISIA_GOVERNORATE_ALIASES.get(normalized);
        if (exactMatch != null) {
            return exactMatch;
        }

        return TUNISIA_GOVERNORATE_ALIASES.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey()))
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(toTitleCase(normalized));
    }

    static LocalDate parseDate(String rawValue) {
        if (!hasText(rawValue)) {
            return null;
        }

        String trimmed = rawValue.trim();
        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Keep trying the known spreadsheet export patterns.
            }
        }

        String strippedTime = trimmed
                .replace('T', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        int firstSpaceIndex = strippedTime.indexOf(' ');
        if (firstSpaceIndex > 0) {
            String datePortion = strippedTime.substring(0, firstSpaceIndex);
            for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(datePortion, formatter);
                } catch (DateTimeParseException ignored) {
                    // Continue through the fallback patterns.
                }
            }
        }

        return null;
    }

    static Double parseCoordinate(String rawValue) {
        if (!hasText(rawValue)) {
            return null;
        }

        String cleaned = rawValue.trim()
                .replace("\u00B0", "")
                .replace("Â°", "")
                .replace(" ", "")
                .replace(',', '.');

        if (cleaned.endsWith("N") || cleaned.endsWith("n") || cleaned.endsWith("E") || cleaned.endsWith("e")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("S") || cleaned.endsWith("s") || cleaned.endsWith("W") || cleaned.endsWith("w")) {
            cleaned = "-" + cleaned.substring(0, cleaned.length() - 1);
        }

        return parseDouble(cleaned);
    }

    static Double parseDouble(String rawValue) {
        if (!hasText(rawValue)) {
            return null;
        }

        String cleaned = rawValue.trim()
                .replace("\u00A0", "")
                .replace(" ", "")
                .replaceAll("[^0-9,.-]+", "");

        if (!hasText(cleaned) || "-".equals(cleaned) || ".".equals(cleaned) || ",".equals(cleaned)) {
            return null;
        }

        if (cleaned.contains(",") && cleaned.contains(".")) {
            int lastComma = cleaned.lastIndexOf(',');
            int lastDot = cleaned.lastIndexOf('.');
            if (lastComma > lastDot) {
                cleaned = cleaned.replace(".", "").replace(',', '.');
            } else {
                cleaned = cleaned.replace(",", "");
            }
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(',', '.');
        }

        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Integer calculateBusinessDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return null;
        }

        int businessDays = 0;
        LocalDate cursor = startDate;
        while (cursor.isBefore(endDate)) {
            cursor = cursor.plusDays(1);
            if (cursor.isAfter(endDate)) {
                break;
            }

            if (isWorkingDay(cursor)) {
                businessDays += 1;
            }
        }

        return businessDays;
    }

    static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    static Double average(List<Double> values) {
        List<Double> filtered = values.stream()
                .filter(java.util.Objects::nonNull)
                .toList();

        if (filtered.isEmpty()) {
            return null;
        }

        double average = filtered.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0d);
        return round2(average);
    }

    static Double asDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    static boolean containsKeyword(String value, String... keywords) {
        String normalized = normalizeLookupValue(value);
        if (!hasText(normalized)) {
            return false;
        }

        for (String keyword : keywords) {
            String normalizedKeyword = normalizeLookupValue(keyword);
            if (hasText(normalizedKeyword) && normalized.contains(normalizedKeyword)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isWorkingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private static String toTitleCase(String rawValue) {
        String cleaned = cleanString(rawValue);
        if (!hasText(cleaned)) {
            return null;
        }

        String[] parts = cleaned.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return builder.isEmpty() ? null : builder.toString();
    }

    private static Map<String, String> buildTunisiaGovernorateAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        registerGovernorateAlias(aliases, "Ariana", "Ariana");
        registerGovernorateAlias(aliases, "Beja", "Beja", "Béja");
        registerGovernorateAlias(aliases, "Ben Arous", "Ben Arous");
        registerGovernorateAlias(aliases, "Bizerte", "Bizerte");
        registerGovernorateAlias(aliases, "Gabes", "Gabes", "Gabès");
        registerGovernorateAlias(aliases, "Gafsa", "Gafsa");
        registerGovernorateAlias(aliases, "Jendouba", "Jendouba");
        registerGovernorateAlias(aliases, "Kairouan", "Kairouan");
        registerGovernorateAlias(aliases, "Kasserine", "Kasserine");
        registerGovernorateAlias(aliases, "Kebili", "Kebili", "Kébili");
        registerGovernorateAlias(aliases, "Kef", "Kef", "Le Kef");
        registerGovernorateAlias(aliases, "Mahdia", "Mahdia");
        registerGovernorateAlias(aliases, "Manouba", "Manouba", "Mannouba");
        registerGovernorateAlias(aliases, "Medenine", "Medenine", "Médenine");
        registerGovernorateAlias(aliases, "Monastir", "Monastir");
        registerGovernorateAlias(aliases, "Nabeul", "Nabeul");
        registerGovernorateAlias(aliases, "Sfax", "Sfax");
        registerGovernorateAlias(aliases, "Sidi Bouzid", "Sidi Bouzid");
        registerGovernorateAlias(aliases, "Siliana", "Siliana");
        registerGovernorateAlias(aliases, "Sousse", "Sousse");
        registerGovernorateAlias(aliases, "Tataouine", "Tataouine");
        registerGovernorateAlias(aliases, "Tozeur", "Tozeur");
        registerGovernorateAlias(aliases, "Tunis", "Tunis");
        registerGovernorateAlias(aliases, "Zaghouan", "Zaghouan");
        return Map.copyOf(aliases);
    }

    private static void registerGovernorateAlias(Map<String, String> aliases, String canonical, String... variants) {
        for (String variant : variants) {
            String normalized = normalizeLookupValue(variant);
            if (normalized != null) {
                aliases.put(normalized, canonical);
            }
        }
    }
}

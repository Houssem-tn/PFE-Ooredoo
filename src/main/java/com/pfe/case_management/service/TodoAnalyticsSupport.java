package com.pfe.case_management.service;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TodoAnalyticsSupport {

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

    // Matches DMS format: 37°15 36.38 N  or  9°52 51.90 E
    private static final Pattern DMS_COORDINATE_PATTERN = Pattern.compile(
            "(\\d{1,3})[°\\s]+(\\d{1,2})[\\s']+(\\d{1,2}\\.?\\d*)\\s*([NSEWnsew])?");
    private static final List<Map.Entry<String, String>> SORTED_TUNISIA_GOVERNORATE_ALIASES =
            TUNISIA_GOVERNORATE_ALIASES.entrySet().stream()
                    .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                    .toList();

    private TodoAnalyticsSupport() {
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String cleanString(String value) {
        if (value == null) {
            return null;
        }

        int start = 0;
        int end = value.length() - 1;
        while (start <= end && Character.isWhitespace(value.charAt(start))) {
            start += 1;
        }
        while (end >= start && Character.isWhitespace(value.charAt(end))) {
            end -= 1;
        }

        if (start > end) {
            return null;
        }

        StringBuilder builder = null;
        boolean previousWhitespace = false;
        for (int index = start; index <= end; index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                if (previousWhitespace) {
                    continue;
                }
                if (builder == null) {
                    builder = new StringBuilder(end - start + 1);
                    builder.append(value, start, index);
                }
                builder.append(' ');
                previousWhitespace = true;
                continue;
            }

            if (builder != null) {
                builder.append(current);
            }
            previousWhitespace = false;
        }

        if (builder == null) {
            return start == 0 && end == value.length() - 1
                    ? value
                    : value.substring(start, end + 1);
        }

        return builder.toString();
    }

    static String normalizeHeader(String value) {
        String cleaned = cleanString(value);
        if (!hasText(cleaned)) {
            return "";
        }

        return normalizeAsciiTokens(cleaned, true, '_', "");
    }

    static String normalizeLookupValue(String value) {
        String cleaned = cleanString(value);
        if (!hasText(cleaned)) {
            return null;
        }

        return normalizeAsciiTokens(cleaned, false, ' ', null);
    }

    static String canonicalGovernorate(String rawValue) {
        String normalized = normalizeLookupValue(rawValue);
        if (!hasText(normalized)) {
            return null;
        }

        String exactMatch = TUNISIA_GOVERNORATE_ALIASES.get(normalized);
        if (exactMatch != null) {
            return exactMatch;
        }

        for (Map.Entry<String, String> entry : SORTED_TUNISIA_GOVERNORATE_ALIASES) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return toTitleCase(normalized);
    }

    static String normalizeCoordinate(String rawValue) {
        String cleaned = cleanString(rawValue);
        if (!hasText(cleaned)) {
            return null;
        }

        String normalized = cleaned
                .replace("\u00B0", "")
                .replace("Â°", "")
                .replace("Ãƒâ€šÃ‚Â°", "")
                .replace(" ", "")
                .replace(',', '.');
        if (normalized.isEmpty()) {
            return null;
        }

        char lastChar = normalized.charAt(normalized.length() - 1);
        if (lastChar == 'N' || lastChar == 'n' || lastChar == 'E' || lastChar == 'e') {
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (lastChar == 'S' || lastChar == 's' || lastChar == 'W' || lastChar == 'w') {
            normalized = "-" + normalized.substring(0, normalized.length() - 1);
        }

        return hasText(normalized) ? normalized : null;
    }

    static LocalDate parseDate(String rawValue) {
        String cleaned = cleanString(rawValue);
        if (!hasText(cleaned)) {
            return null;
        }

        LocalDate parsed = tryParseCommonDate(cleaned);
        if (parsed != null) {
            return parsed;
        }

        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (DateTimeParseException ignored) {
                // Keep trying supported spreadsheet date formats.
            }
        }

        String datePortion = stripDateTimePortion(cleaned);
        if (!cleaned.equals(datePortion)) {
            parsed = tryParseCommonDate(datePortion);
            if (parsed != null) {
                return parsed;
            }

            for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(datePortion, formatter);
                } catch (DateTimeParseException ignored) {
                    // Continue through the compact date-only fallbacks.
                }
            }
        }

        return null;
    }

    static Integer calculateBusinessDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return null;
        }

        LocalDate firstMeasuredDay = startDate.plusDays(1);
        if (firstMeasuredDay.isAfter(endDate)) {
            return 0;
        }

        long totalDays = ChronoUnit.DAYS.between(firstMeasuredDay, endDate) + 1;
        long fullWeeks = totalDays / 7;
        int businessDays = (int) (fullWeeks * 5);

        long remainingDays = totalDays % 7;
        for (int dayIndex = 0; dayIndex < remainingDays; dayIndex++) {
            if (isWorkingDay(firstMeasuredDay.plusDays(dayIndex))) {
                businessDays += 1;
            }
        }

        return businessDays;
    }

    static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * Converts a raw coordinate string (DMS or decimal) to a decimal Double.
     * Returns null if the string is blank or cannot be parsed. Never throws.
     * Handles formats like "37°15 36.38 N", "9°52 51.90 E", "37.2601", "-9.881".
     */
    static Double parseCoordinateToDecimal(String rawValue) {
        if (!hasText(rawValue)) {
            return null;
        }

        String cleaned = rawValue.trim();

        // Try DMS pattern first (degrees° minutes seconds hemisphere)
        Matcher dmsMatcher = DMS_COORDINATE_PATTERN.matcher(cleaned);
        if (dmsMatcher.find()) {
            try {
                double degrees = Double.parseDouble(dmsMatcher.group(1));
                double minutes = Double.parseDouble(dmsMatcher.group(2));
                double seconds = Double.parseDouble(dmsMatcher.group(3));
                double decimal = degrees + minutes / 60.0 + seconds / 3600.0;
                String hemisphere = dmsMatcher.group(4);
                if (hemisphere != null) {
                    char h = hemisphere.trim().toUpperCase(Locale.ROOT).charAt(0);
                    if (h == 'S' || h == 'W') {
                        decimal = -decimal;
                    }
                }
                return Math.round(decimal * 1_000_000d) / 1_000_000d;
            } catch (NumberFormatException ignored) {
                // Fall through to plain decimal parsing
            }
        }

        // Try plain decimal (possibly with N/S/E/W suffix and comma decimal separator)
        String normalized = cleaned.replace(',', '.');
        double sign = 1.0;
        if (!normalized.isEmpty()) {
            char last = Character.toUpperCase(normalized.charAt(normalized.length() - 1));
            if (last == 'N' || last == 'E') {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
            } else if (last == 'S' || last == 'W') {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
                sign = -1.0;
            }
        }

        normalized = normalized.replaceAll("\\s+", "");
        try {
            return Double.parseDouble(normalized) * sign;
        } catch (NumberFormatException ignored) {
            return null;
        }
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
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return builder.length() == 0 ? null : builder.toString();
    }

    private static String normalizeAsciiTokens(String value, boolean upperCase, char separator, String emptyFallback) {
        String normalizedInput = containsNonAscii(value)
                ? Normalizer.normalize(value, Normalizer.Form.NFD)
                : value;

        StringBuilder builder = new StringBuilder(normalizedInput.length());
        boolean previousSeparator = false;
        for (int index = 0; index < normalizedInput.length(); index++) {
            char current = normalizedInput.charAt(index);
            int type = Character.getType(current);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }

            char normalizedChar = upperCase
                    ? Character.toUpperCase(current)
                    : Character.toLowerCase(current);
            if (isAsciiAlphaNumeric(normalizedChar)) {
                builder.append(normalizedChar);
                previousSeparator = false;
                continue;
            }

            if (builder.length() == 0 || previousSeparator) {
                continue;
            }

            builder.append(separator);
            previousSeparator = true;
        }

        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == separator) {
            builder.setLength(length - 1);
        }

        if (builder.length() == 0) {
            return emptyFallback;
        }

        return builder.toString();
    }

    private static boolean containsNonAscii(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 127) {
                return true;
            }
        }

        return false;
    }

    private static boolean isAsciiAlphaNumeric(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9');
    }

    private static LocalDate tryParseCommonDate(String rawValue) {
        String candidate = stripDateTimePortion(rawValue);
        if (candidate.isEmpty()) {
            return null;
        }

        char separator = candidate.indexOf('/') >= 0 ? '/' : (candidate.indexOf('-') >= 0 ? '-' : '\0');
        if (separator == '\0') {
            return null;
        }

        int firstSeparator = candidate.indexOf(separator);
        int secondSeparator = candidate.indexOf(separator, firstSeparator + 1);
        if (firstSeparator <= 0 || secondSeparator <= firstSeparator + 1 || secondSeparator >= candidate.length() - 1) {
            return null;
        }
        if (candidate.indexOf(separator, secondSeparator + 1) >= 0) {
            return null;
        }

        Integer first = parseInteger(candidate.substring(0, firstSeparator));
        Integer second = parseInteger(candidate.substring(firstSeparator + 1, secondSeparator));
        Integer third = parseInteger(candidate.substring(secondSeparator + 1));
        if (first == null || second == null || third == null) {
            return null;
        }

        int year;
        int month;
        int day;
        if (firstSeparator == 4) {
            year = normalizeYear(first);
            month = second;
            day = third;
        } else {
            year = normalizeYear(third);
            if (separator == '/') {
                if (first > 12 && second <= 12) {
                    day = first;
                    month = second;
                } else {
                    month = first;
                    day = second;
                }
            } else if (first > 12 && second <= 12) {
                day = first;
                month = second;
            } else if (second > 12 && first <= 12) {
                month = first;
                day = second;
            } else {
                day = first;
                month = second;
            }
        }

        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private static String stripDateTimePortion(String value) {
        String normalized = value == null ? "" : value.trim().replace('T', ' ');
        int separatorIndex = normalized.indexOf(' ');
        return separatorIndex < 0 ? normalized : normalized.substring(0, separatorIndex);
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        int result = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < '0' || current > '9') {
                return null;
            }
            result = (result * 10) + (current - '0');
        }

        return result;
    }

    private static int normalizeYear(int value) {
        return value < 100 ? 2000 + value : value;
    }

    private static Map<String, String> buildTunisiaGovernorateAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        registerGovernorateAlias(aliases, "Ariana", "Ariana");
        registerGovernorateAlias(aliases, "Beja", "Beja", "BÃ©ja");
        registerGovernorateAlias(aliases, "Ben Arous", "Ben Arous");
        registerGovernorateAlias(aliases, "Bizerte", "Bizerte");
        registerGovernorateAlias(aliases, "Gabes", "Gabes", "GabÃ¨s");
        registerGovernorateAlias(aliases, "Gafsa", "Gafsa");
        registerGovernorateAlias(aliases, "Jendouba", "Jendouba");
        registerGovernorateAlias(aliases, "Kairouan", "Kairouan");
        registerGovernorateAlias(aliases, "Kasserine", "Kasserine");
        registerGovernorateAlias(aliases, "Kebili", "Kebili", "KÃ©bili");
        registerGovernorateAlias(aliases, "Kef", "Kef", "Le Kef");
        registerGovernorateAlias(aliases, "Mahdia", "Mahdia");
        registerGovernorateAlias(aliases, "Manouba", "Manouba", "Mannouba");
        registerGovernorateAlias(aliases, "Medenine", "Medenine", "MÃ©denine");
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

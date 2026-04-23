package com.pfe.activation_analytics.service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
    private static final List<Map.Entry<String, String>> SORTED_TUNISIA_GOVERNORATE_ALIASES =
            TUNISIA_GOVERNORATE_ALIASES.entrySet().stream()
                    .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                    .toList();
    private static final String[] COMPLETED_KEYWORDS = normalizeKeywords(
            "completed", "termine", "cloture", "done"
    );
    private static final String[] POSITIVE_SIGNAL_KEYWORDS = normalizeKeywords(
            "oui", "ok", "success", "succes", "confirm", "confirme", "favorable", "realise", "pris"
    );
    private static final String[] RDV_SUCCESS_KEYWORDS = normalizeKeywords(
            "rdv pris", "rdv confirme"
    );
    private static final String[] INJOIGNABLE_KEYWORDS = normalizeKeywords(
            "injoignable", "unreachable", "no answer"
    );
    private static final String[] ABANDON_KEYWORDS = normalizeKeywords("abandon");
    private static final String[] BLOCKED_KEYWORDS = normalizeKeywords(
            "gele", "bloque", "blocked"
    );

    private ActivationAnalyticsSupport() {
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

        for (Map.Entry<String, String> entry : SORTED_TUNISIA_GOVERNORATE_ALIASES) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return toTitleCase(normalized);
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
                // Keep trying the known spreadsheet export patterns.
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
                    // Continue through the fallback patterns.
                }
            }
        }

        return null;
    }

    static Double parseCoordinate(String rawValue) {
        String cleaned = cleanString(rawValue);
        if (!hasText(cleaned)) {
            return null;
        }

        String normalized = cleaned
                .replace("\u00B0", "")
                .replace("Ã‚Â°", "")
                .replace(" ", "");
        if (normalized.isEmpty()) {
            return null;
        }

        char lastChar = normalized.charAt(normalized.length() - 1);
        if (lastChar == 'N' || lastChar == 'n' || lastChar == 'E' || lastChar == 'e') {
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (lastChar == 'S' || lastChar == 's' || lastChar == 'W' || lastChar == 'w') {
            normalized = "-" + normalized.substring(0, normalized.length() - 1);
        }

        return parseDouble(normalized.replace(',', '.'));
    }

    static Double parseDouble(String rawValue) {
        String cleanedValue = cleanString(rawValue);
        if (!hasText(cleanedValue)) {
            return null;
        }

        StringBuilder builder = new StringBuilder(cleanedValue.length());
        for (int index = 0; index < cleanedValue.length(); index++) {
            char current = cleanedValue.charAt(index);
            if ((current >= '0' && current <= '9')
                    || current == ','
                    || current == '.'
                    || current == '-') {
                builder.append(current);
            }
        }

        String cleaned = builder.toString();
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

    static Double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        double sum = 0d;
        int count = 0;
        for (Double value : values) {
            if (value == null) {
                continue;
            }
            sum += value;
            count += 1;
        }

        return count == 0 ? null : round2(sum / count);
    }

    static Double asDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    static Instant toInstant(LocalDate value) {
        return value == null ? null : value.atStartOfDay().toInstant(ZoneOffset.UTC);
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

    static boolean isCompleted(String statut, String repTravauxStt, LocalDate dateFinTrvDate) {
        return dateFinTrvDate != null
                || hasPositiveSignal(repTravauxStt)
                || containsAnyKeyword(statut, COMPLETED_KEYWORDS);
    }

    static boolean isRdvSuccess(String repRdv, LocalDate datePriseRdvDate) {
        return datePriseRdvDate != null
                || hasPositiveSignal(repRdv)
                || containsAnyKeyword(repRdv, RDV_SUCCESS_KEYWORDS);
    }

    static boolean isInjoignable(String causeInjoignabilite, String repRdv, String statut, String detail) {
        return hasText(causeInjoignabilite)
                || containsAnyKeyword(repRdv, INJOIGNABLE_KEYWORDS)
                || containsAnyKeyword(statut, INJOIGNABLE_KEYWORDS)
                || containsAnyKeyword(detail, INJOIGNABLE_KEYWORDS);
    }

    static boolean isAbandon(String causeAbandon, String repRdv, String statut, String detail) {
        return hasText(causeAbandon)
                || containsAnyKeyword(repRdv, ABANDON_KEYWORDS)
                || containsAnyKeyword(statut, ABANDON_KEYWORDS)
                || containsAnyKeyword(detail, ABANDON_KEYWORDS);
    }

    static boolean isBlocked(String statut, String detail, String commentaire, String cmtZm, String cmtTrvStt) {
        return containsAnyKeyword(statut, BLOCKED_KEYWORDS)
                || containsAnyKeyword(detail, BLOCKED_KEYWORDS)
                || containsAnyKeyword(commentaire, BLOCKED_KEYWORDS)
                || containsAnyKeyword(cmtZm, BLOCKED_KEYWORDS)
                || containsAnyKeyword(cmtTrvStt, BLOCKED_KEYWORDS);
    }

    static boolean isDelayed(
            Integer endToEndBusinessDays,
            Integer assignmentBusinessDays,
            Integer rdvBusinessDays,
            Integer executionBusinessDays
    ) {
        if (endToEndBusinessDays != null) {
            return endToEndBusinessDays > 5;
        }

        return valueAbove(assignmentBusinessDays, 2)
                || valueAbove(rdvBusinessDays, 2)
                || valueAbove(executionBusinessDays, 3);
    }

    private static boolean isWorkingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private static boolean hasPositiveSignal(String value) {
        return containsAnyKeyword(value, POSITIVE_SIGNAL_KEYWORDS);
    }

    private static boolean valueAbove(Integer value, int threshold) {
        return value != null && value > threshold;
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

    private static String[] normalizeKeywords(String... keywords) {
        String[] normalized = new String[keywords.length];
        for (int index = 0; index < keywords.length; index++) {
            normalized[index] = normalizeLookupValue(keywords[index]);
        }
        return normalized;
    }

    private static boolean containsAnyKeyword(String value, String[] normalizedKeywords) {
        String normalized = normalizeLookupValue(value);
        if (!hasText(normalized)) {
            return false;
        }

        for (String keyword : normalizedKeywords) {
            if (hasText(keyword) && normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> buildTunisiaGovernorateAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        registerGovernorateAlias(aliases, "Ariana", "Ariana");
        registerGovernorateAlias(aliases, "Beja", "Beja", "Beja");
        registerGovernorateAlias(aliases, "Ben Arous", "Ben Arous");
        registerGovernorateAlias(aliases, "Bizerte", "Bizerte");
        registerGovernorateAlias(aliases, "Gabes", "Gabes", "Gabes");
        registerGovernorateAlias(aliases, "Gafsa", "Gafsa");
        registerGovernorateAlias(aliases, "Jendouba", "Jendouba");
        registerGovernorateAlias(aliases, "Kairouan", "Kairouan");
        registerGovernorateAlias(aliases, "Kasserine", "Kasserine");
        registerGovernorateAlias(aliases, "Kebili", "Kebili", "Kebili");
        registerGovernorateAlias(aliases, "Kef", "Kef", "Le Kef");
        registerGovernorateAlias(aliases, "Mahdia", "Mahdia");
        registerGovernorateAlias(aliases, "Manouba", "Manouba", "Mannouba");
        registerGovernorateAlias(aliases, "Medenine", "Medenine", "Medenine");
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

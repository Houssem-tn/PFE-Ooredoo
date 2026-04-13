package com.pfe.case_management.service;

import com.pfe.case_management.dto.ZmPerformanceGovernorateItemResponse;
import com.pfe.case_management.dto.ZmPerformanceGovernorateSttItemResponse;
import com.pfe.case_management.dto.ZmPerformanceStudyResponse;
import com.pfe.case_management.dto.ZmPerformanceSttBreakdownItemResponse;
import com.pfe.case_management.entity.ImportedTodoEntity;
import com.pfe.case_management.model.ImportedTodoSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ZmPerformanceAnalyticsService {
    private static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATTERS = List.of(
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

    private final MongoTemplate mongoTemplate;

    public ZmPerformanceStudyResponse getStudy(
            String zoneManager,
            String governorate,
            LocalDate uploadedFrom,
            LocalDate uploadedTo
    ) {
        List<ImportedTodoEntity> validRows = loadAnalyticsRows(
                ImportedTodoSourceType.ADMIN_PERFORMANCE_IMPORT,
                null,
                uploadedFrom,
                uploadedTo
        );
        return buildStudy(validRows, zoneManager, governorate);
    }

    public ZmPerformanceStudyResponse getSavedStudy(
            String savedStudyId,
            String zoneManager,
            String governorate,
            LocalDate uploadedFrom,
            LocalDate uploadedTo
    ) {
        if (!hasText(savedStudyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saved study id is required");
        }

        List<ImportedTodoEntity> validRows = loadAnalyticsRows(
                ImportedTodoSourceType.SAVED_ADMIN_PERFORMANCE_IMPORT,
                savedStudyId.trim(),
                uploadedFrom,
                uploadedTo
        );
        return buildStudy(validRows, zoneManager, governorate);
    }

    private ZmPerformanceStudyResponse buildStudy(
            List<ImportedTodoEntity> validRows,
            String zoneManager,
            String governorate
    ) {
        List<String> availableZoneManagers = distinctSorted(validRows, ImportedTodoEntity::getZoneManager);
        if (availableZoneManagers.isEmpty()) {
            return emptyStudy();
        }

        String selectedZoneManager = resolveOptionalSelection(zoneManager, availableZoneManagers);
        String normalizedSelectedZm = normalizeLookupValue(selectedZoneManager);

        List<ImportedTodoEntity> zmRows = normalizedSelectedZm == null
                ? validRows
                : validRows.stream()
                .filter(row -> normalizedSelectedZm.equals(normalizeLookupValue(row.getZoneManager())))
                .toList();

        List<String> availableGovernorates = distinctSortedGovernorates(zmRows);
        String selectedGovernorate = hasText(governorate)
                ? resolveOptionalGovernorateSelection(governorate, availableGovernorates)
                : null;
        String normalizedSelectedGovernorate = normalizeGovernorateLookupValue(selectedGovernorate);

        List<ImportedTodoEntity> filteredRows = normalizedSelectedGovernorate == null
                ? zmRows
                : zmRows.stream()
                .filter(row -> normalizedSelectedGovernorate.equals(normalizeGovernorateLookupValue(row.getGouvernorat())))
                .toList();

        long totalCases = filteredRows.size();
        List<Integer> slaValues = filteredRows.stream()
                .map(this::resolvedSlaBusinessDays)
                .filter(value -> value != null)
                .toList();

        List<ZmPerformanceSttBreakdownItemResponse> sttBreakdown = buildSttBreakdown(filteredRows, totalCases);
        List<ZmPerformanceGovernorateItemResponse> governorateBreakdown = buildGovernorateBreakdown(filteredRows, totalCases);

        ZmPerformanceGovernorateItemResponse bestGovernorate = governorateBreakdown.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .min(Comparator.comparingDouble(ZmPerformanceGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);

        ZmPerformanceGovernorateItemResponse worstGovernorate = governorateBreakdown.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .max(Comparator.comparingDouble(ZmPerformanceGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);

        ZmPerformanceSttBreakdownItemResponse topStt = sttBreakdown.isEmpty() ? null : sttBreakdown.get(0);
        ZmPerformanceGovernorateItemResponse topGovernorate = governorateBreakdown.isEmpty() ? null : governorateBreakdown.get(0);

        return new ZmPerformanceStudyResponse(
                selectedZoneManager,
                selectedGovernorate,
                availableZoneManagers,
                availableGovernorates,
                totalCases,
                slaValues.size(),
                round2(slaValues.stream().mapToInt(Integer::intValue).average().orElse(0d)),
                slaValues.stream().mapToDouble(Integer::doubleValue).min().isPresent()
                        ? round2(slaValues.stream().mapToDouble(Integer::doubleValue).min().orElse(0d))
                        : null,
                slaValues.stream().mapToDouble(Integer::doubleValue).max().isPresent()
                        ? round2(slaValues.stream().mapToDouble(Integer::doubleValue).max().orElse(0d))
                        : null,
                sttBreakdown.size(),
                governorateBreakdown.size(),
                topStt != null ? topStt.sttName() : null,
                topStt != null ? topStt.caseCount() : 0L,
                topGovernorate != null ? topGovernorate.gouvernorat() : null,
                topGovernorate != null ? topGovernorate.caseCount() : 0L,
                bestGovernorate != null ? bestGovernorate.gouvernorat() : null,
                bestGovernorate != null ? bestGovernorate.averageSlaBusinessDays() : null,
                worstGovernorate != null ? worstGovernorate.gouvernorat() : null,
                worstGovernorate != null ? worstGovernorate.averageSlaBusinessDays() : null,
                buildInsights(selectedZoneManager, totalCases, slaValues.size(), topStt, topGovernorate, worstGovernorate),
                sttBreakdown,
                governorateBreakdown
        );
    }

    private List<ImportedTodoEntity> loadAnalyticsRows(
            ImportedTodoSourceType sourceType,
            String savedStudyId,
            LocalDate uploadedFrom,
            LocalDate uploadedTo
    ) {
        validateUploadedRange(uploadedFrom, uploadedTo);

        Query query = new Query();
        query.addCriteria(Criteria.where("sourceType").is(sourceType));
        query.addCriteria(Criteria.where("validRow").is(true));

        if (hasText(savedStudyId)) {
            query.addCriteria(Criteria.where("savedStudyId").is(savedStudyId.trim()));
        }

        if (uploadedFrom != null || uploadedTo != null) {
            Criteria uploadedAtCriteria = Criteria.where("uploadedAt");
            if (uploadedFrom != null) {
                uploadedAtCriteria = uploadedAtCriteria.gte(uploadedFrom.atStartOfDay().toInstant(ZoneOffset.UTC));
            }
            if (uploadedTo != null) {
                uploadedAtCriteria = uploadedAtCriteria.lte(uploadedTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1));
            }
            query.addCriteria(uploadedAtCriteria);
        }

        return mongoTemplate.find(query, ImportedTodoEntity.class);
    }

    private void validateUploadedRange(LocalDate uploadedFrom, LocalDate uploadedTo) {
        if (uploadedFrom != null && uploadedTo != null && uploadedTo.isBefore(uploadedFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded To date must be on or after Uploaded From date");
        }
    }

    private List<ZmPerformanceSttBreakdownItemResponse> buildSttBreakdown(List<ImportedTodoEntity> rows, long totalCases) {
        Map<String, List<ImportedTodoEntity>> grouped = groupByNormalized(rows, ImportedTodoEntity::getStt);
        List<ZmPerformanceSttBreakdownItemResponse> items = new ArrayList<>();

        for (List<ImportedTodoEntity> group : grouped.values()) {
            String display = displayValue(group, ImportedTodoEntity::getStt);
            List<Integer> slaValues = group.stream()
                    .map(this::resolvedSlaBusinessDays)
                    .filter(value -> value != null)
                    .toList();

            items.add(new ZmPerformanceSttBreakdownItemResponse(
                    display,
                    group.size(),
                    totalCases == 0 ? 0d : round2((group.size() * 100d) / totalCases),
                    slaValues.isEmpty() ? null : round2(slaValues.stream().mapToInt(Integer::intValue).average().orElse(0d))
            ));
        }

        items.sort(Comparator
                .comparingLong(ZmPerformanceSttBreakdownItemResponse::caseCount).reversed()
                .thenComparing(item -> item.sttName() == null ? "" : item.sttName(), String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<ZmPerformanceGovernorateItemResponse> buildGovernorateBreakdown(List<ImportedTodoEntity> rows, long totalCases) {
        Map<String, List<ImportedTodoEntity>> grouped = groupByGovernorate(rows);
        List<ZmPerformanceGovernorateItemResponse> items = new ArrayList<>();

        for (List<ImportedTodoEntity> group : grouped.values()) {
            String display = governorateDisplayValue(group);
            List<Integer> slaValues = group.stream()
                    .map(this::resolvedSlaBusinessDays)
                    .filter(value -> value != null)
                    .toList();

            Map<String, List<ImportedTodoEntity>> byStt = groupByNormalized(group, ImportedTodoEntity::getStt);
            String topSttName = null;
            long topSttCaseCount = 0L;
            if (!byStt.isEmpty()) {
                List<ImportedTodoEntity> topSttGroup = byStt.values().stream()
                        .max(Comparator.comparingInt(List::size))
                        .orElse(List.of());
                topSttName = displayValue(topSttGroup, ImportedTodoEntity::getStt);
                topSttCaseCount = topSttGroup.size();
            }

            List<ZmPerformanceGovernorateSttItemResponse> sttBreakdown = new ArrayList<>();
            for (List<ImportedTodoEntity> sttGroup : byStt.values()) {
                String sttDisplay = displayValue(sttGroup, ImportedTodoEntity::getStt);
                List<Integer> sttSlaValues = sttGroup.stream()
                        .map(this::resolvedSlaBusinessDays)
                        .filter(value -> value != null)
                        .toList();

                sttBreakdown.add(new ZmPerformanceGovernorateSttItemResponse(
                        sttDisplay,
                        sttGroup.size(),
                        group.isEmpty() ? 0d : round2((sttGroup.size() * 100d) / group.size()),
                        sttSlaValues.isEmpty() ? null : round2(sttSlaValues.stream().mapToInt(Integer::intValue).average().orElse(0d))
                ));
            }

            sttBreakdown.sort(Comparator
                    .comparingLong(ZmPerformanceGovernorateSttItemResponse::caseCount).reversed()
                    .thenComparing(item -> item.sttName() == null ? "" : item.sttName(), String.CASE_INSENSITIVE_ORDER));

            items.add(new ZmPerformanceGovernorateItemResponse(
                    display,
                    group.size(),
                    totalCases == 0 ? 0d : round2((group.size() * 100d) / totalCases),
                    slaValues.isEmpty() ? null : round2(slaValues.stream().mapToInt(Integer::intValue).average().orElse(0d)),
                    byStt.size(),
                    topSttName,
                    topSttCaseCount,
                    sttBreakdown
            ));
        }

        items.sort(Comparator
                .comparingLong(ZmPerformanceGovernorateItemResponse::caseCount).reversed()
                .thenComparing(item -> item.gouvernorat() == null ? "" : item.gouvernorat(), String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<String> buildInsights(
            String selectedZoneManager,
            long totalCases,
            int rowsWithSla,
            ZmPerformanceSttBreakdownItemResponse topStt,
            ZmPerformanceGovernorateItemResponse topGovernorate,
            ZmPerformanceGovernorateItemResponse worstGovernorate
    ) {
        List<String> insights = new ArrayList<>();

        if (hasText(selectedZoneManager)) {
            insights.add(selectedZoneManager + " has " + totalCases + " imported case(s) in the current study scope.");
        } else {
            insights.add("All Zone Managers combined have " + totalCases + " imported case(s) in the current study scope.");
        }

        if (topStt != null) {
            insights.add("Top STT by volume is " + topStt.sttName() + " with " + topStt.caseCount() + " case(s), representing " + topStt.percentage() + "% of the workload.");
        }

        if (topGovernorate != null) {
            insights.add("Highest case volume is in " + topGovernorate.gouvernorat() + " with " + topGovernorate.caseCount() + " case(s).");
        }

        if (worstGovernorate != null && worstGovernorate.averageSlaBusinessDays() != null) {
            insights.add("Slowest governorate on SLA is " + worstGovernorate.gouvernorat() + " with an average of " + worstGovernorate.averageSlaBusinessDays() + " business day(s).");
        }

        if (rowsWithSla == 0) {
            insights.add("No valid SLA dates were found in the current scope, so SLA averages could not be computed.");
        }

        return insights;
    }

    private ZmPerformanceStudyResponse emptyStudy() {
        return new ZmPerformanceStudyResponse(
                null,
                null,
                List.of(),
                List.of(),
                0L,
                0L,
                0d,
                null,
                null,
                0L,
                0L,
                null,
                0L,
                null,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private <T> Map<String, List<ImportedTodoEntity>> groupByNormalized(List<ImportedTodoEntity> rows, ValueExtractor extractor) {
        Map<String, List<ImportedTodoEntity>> grouped = new LinkedHashMap<>();

        for (ImportedTodoEntity row : rows) {
            String rawValue = extractor.extract(row);
            String normalized = normalizeLookupValue(rawValue);
            if (normalized == null) {
                continue;
            }

            grouped.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(row);
        }

        return grouped;
    }

    private Map<String, List<ImportedTodoEntity>> groupByGovernorate(List<ImportedTodoEntity> rows) {
        Map<String, List<ImportedTodoEntity>> grouped = new LinkedHashMap<>();

        for (ImportedTodoEntity row : rows) {
            String normalized = normalizeGovernorateLookupValue(row.getGouvernorat());
            if (normalized == null) {
                continue;
            }

            grouped.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(row);
        }

        return grouped;
    }

    private String displayValue(List<ImportedTodoEntity> rows, ValueExtractor extractor) {
        for (ImportedTodoEntity row : rows) {
            String rawValue = extractor.extract(row);
            if (hasText(rawValue)) {
                return rawValue.trim();
            }
        }
        return null;
    }

    private String governorateDisplayValue(List<ImportedTodoEntity> rows) {
        for (ImportedTodoEntity row : rows) {
            String canonical = canonicalTunisiaGovernorate(row.getGouvernorat());
            if (hasText(canonical)) {
                return canonical;
            }
        }

        return null;
    }

    private List<String> distinctSorted(List<ImportedTodoEntity> rows, ValueExtractor extractor) {
        Map<String, String> normalizedToDisplay = new LinkedHashMap<>();

        for (ImportedTodoEntity row : rows) {
            String rawValue = extractor.extract(row);
            String normalized = normalizeLookupValue(rawValue);
            if (normalized == null || normalizedToDisplay.containsKey(normalized)) {
                continue;
            }

            normalizedToDisplay.put(normalized, rawValue.trim());
        }

        return normalizedToDisplay.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> distinctSortedGovernorates(List<ImportedTodoEntity> rows) {
        Map<String, String> normalizedToDisplay = new LinkedHashMap<>();

        for (ImportedTodoEntity row : rows) {
            String canonical = canonicalTunisiaGovernorate(row.getGouvernorat());
            String normalized = normalizeGovernorateLookupValue(canonical);
            if (normalized == null || normalizedToDisplay.containsKey(normalized)) {
                continue;
            }

            normalizedToDisplay.put(normalized, canonical);
        }

        return normalizedToDisplay.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String resolveOptionalSelection(String requestedValue, List<String> availableValues) {
        if (!hasText(requestedValue)) {
            return null;
        }

        String normalizedRequested = normalizeLookupValue(requestedValue);
        if (normalizedRequested == null) {
            return null;
        }

        return availableValues.stream()
                .filter(value -> normalizedRequested.equals(normalizeLookupValue(value)))
                .findFirst()
                .orElse(null);
    }

    private String resolveOptionalGovernorateSelection(String requestedValue, List<String> availableValues) {
        if (!hasText(requestedValue)) {
            return null;
        }

        String normalizedRequested = normalizeGovernorateLookupValue(requestedValue);
        if (normalizedRequested == null) {
            return null;
        }

        return availableValues.stream()
                .filter(value -> normalizedRequested.equals(normalizeGovernorateLookupValue(value)))
                .findFirst()
                .orElse(null);
    }

    private String normalizeLookupValue(String value) {
        if (!hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeGovernorateLookupValue(String value) {
        String canonical = canonicalTunisiaGovernorate(value);
        if (!hasText(canonical)) {
            return null;
        }

        return normalizeLookupValue(canonical);
    }

    private String canonicalTunisiaGovernorate(String rawValue) {
        String normalized = normalizeGovernorateAlias(rawValue);
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
                .orElse(null);
    }

    private String normalizeGovernorateAlias(String value) {
        if (!hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        return hasText(normalized) ? normalized : null;
    }

    private Integer resolvedSlaBusinessDays(ImportedTodoEntity row) {
        if (row.getSlaBusinessDays() != null) {
            return row.getSlaBusinessDays();
        }

        LocalDate startDate = row.getOpenDateSurTimosDate() != null
                ? row.getOpenDateSurTimosDate()
                : parseImportedDate(row.getOpenDateSurTimos());
        LocalDate endDate = row.getDateAffectationSttDate() != null
                ? row.getDateAffectationSttDate()
                : parseImportedDate(row.getDateAffectationStt());

        return calculateBusinessDays(startDate, endDate);
    }

    private LocalDate parseImportedDate(String rawValue) {
        if (!hasText(rawValue)) {
            return null;
        }

        String trimmed = rawValue.trim();
        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Keep trying the known telecom export formats.
            }
        }

        String strippedTime = trimmed.replace('T', ' ').replaceAll("\\s+", " ").trim();
        int firstSpaceIndex = strippedTime.indexOf(' ');
        if (firstSpaceIndex > 0) {
            String datePortion = strippedTime.substring(0, firstSpaceIndex);
            for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(datePortion, formatter);
                } catch (DateTimeParseException ignored) {
                    // Keep trying compact date-only fallbacks.
                }
            }
        }

        return null;
    }

    private Integer calculateBusinessDays(LocalDate startDate, LocalDate endDate) {
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

    private boolean isWorkingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    @FunctionalInterface
    private interface ValueExtractor {
        String extract(ImportedTodoEntity entity);
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
            String normalized = normalizeGovernorateAliasStatic(variant);
            if (normalized != null) {
                aliases.put(normalized, canonical);
            }
        }
    }

    private static String normalizeGovernorateAliasStatic(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        return normalized.isBlank() ? null : normalized;
    }
}

package com.pfe.case_management.service;

import com.pfe.case_management.dto.SaveZmPerformanceStudyRequest;
import com.pfe.case_management.dto.SavedZmPerformanceInvalidReasonResponse;
import com.pfe.case_management.dto.SavedZmPerformanceRowPreviewResponse;
import com.pfe.case_management.dto.SavedZmPerformanceRowQualityResponse;
import com.pfe.case_management.dto.SavedZmPerformanceStudyResponse;
import com.pfe.case_management.dto.ZmPerformanceStudyResponse;
import com.pfe.case_management.entity.ImportedTodoEntity;
import com.pfe.case_management.entity.SavedZmPerformanceStudyEntity;
import com.pfe.case_management.model.ImportedTodoSourceType;
import com.pfe.case_management.repository.SavedZmPerformanceStudyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SavedZmPerformanceStudyService {
    private static final int INVALID_PAGE_SIZE_DEFAULT = 50;
    private static final int INVALID_PAGE_SIZE_MAX = 200;
    private static final Logger log = LoggerFactory.getLogger(SavedZmPerformanceStudyService.class);

    private final MongoTemplate mongoTemplate;
    private final SavedZmPerformanceStudyRepository savedZmPerformanceStudyRepository;

    public SavedZmPerformanceStudyResponse saveCurrentStudy(String savedBy, SaveZmPerformanceStudyRequest request) {
        long totalStart = System.nanoTime();
        Query stagingQuery = new Query(new Criteria().andOperator(
                Criteria.where("sourceType").is(ImportedTodoSourceType.ADMIN_PERFORMANCE_IMPORT),
                Criteria.where("uploadedBy").is(savedBy)
        ));

        if (request == null || request.study() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no dashboard snapshot to save");
        }

        ZmPerformanceStudyResponse study = request.study();

        if (study.totalCases() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no dashboard data to save");
        }

        long stagingQueryStart = System.nanoTime();
        long stagingRowCount = mongoTemplate.count(stagingQuery, ImportedTodoEntity.class);
        if (stagingRowCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no imported admin performance batch to save");
        }

        List<String> sourceFileNames = loadDistinctOrderedValues(stagingQuery, "sourceFileName");
        List<String> sourceBatchIds = loadDistinctOrderedValues(stagingQuery, "importBatchId");
        long stagingQueryMs = elapsedMs(stagingQueryStart);

        SavedZmPerformanceStudyEntity entity = SavedZmPerformanceStudyEntity.builder()
                .savedAt(Instant.now())
                .savedBy(savedBy)
                .sourceFileNames(sourceFileNames)
                .sourceBatchIds(sourceBatchIds)
                .study(study)
                .build();

        long updateStart = System.nanoTime();
        SavedZmPerformanceStudyEntity savedEntity = savedZmPerformanceStudyRepository.save(entity);
        Update update = new Update()
                .set("sourceType", ImportedTodoSourceType.SAVED_ADMIN_PERFORMANCE_IMPORT)
                .set("savedStudyId", savedEntity.getId());
        mongoTemplate.updateMulti(stagingQuery, update, ImportedTodoEntity.class);
        long updateMs = elapsedMs(updateStart);

        log.info(
                "todo_admin_save_study savedBy={} studyId={} stagingRows={} sourceFiles={} sourceBatches={} stagingQueryMs={} updateMs={} totalMs={}",
                savedBy,
                savedEntity.getId(),
                stagingRowCount,
                sourceFileNames.size(),
                sourceBatchIds.size(),
                stagingQueryMs,
                updateMs,
                elapsedMs(totalStart)
        );
        return toResponse(savedEntity);
    }

    public List<SavedZmPerformanceStudyResponse> listSavedStudies(String savedBy) {
        return savedZmPerformanceStudyRepository.findAllBySavedByOrderBySavedAtDesc(savedBy).stream()
                .map(this::toResponse)
                .toList();
    }

    public SavedZmPerformanceRowQualityResponse getSavedStudyRowQuality(
            String savedBy,
            String savedStudyId,
            String zoneManager,
            String governorate,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo,
            String invalidReason,
            Integer invalidPage,
            Integer invalidPageSize
    ) {
        if (!hasText(savedStudyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saved study id is required");
        }

        validateDateRange("Open Date", openDateFrom, openDateTo);
        validateDateRange("Affectation Date", affectationDateFrom, affectationDateTo);

        SavedZmPerformanceStudyEntity study = savedZmPerformanceStudyRepository.findByIdAndSavedBy(savedStudyId.trim(), savedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved study was not found"));

        Query query = new Query();
        query.addCriteria(Criteria.where("sourceType").is(ImportedTodoSourceType.SAVED_ADMIN_PERFORMANCE_IMPORT));
        query.addCriteria(Criteria.where("savedStudyId").is(savedStudyId.trim()));
        query.addCriteria(Criteria.where("uploadedBy").is(savedBy));

        if (hasText(zoneManager)) {
            query.addCriteria(Criteria.where("zoneManagerNormalized").is(zoneManager.trim().toLowerCase(Locale.ROOT)));
        }

        if (hasText(governorate)) {
            query.addCriteria(Criteria.where("gouvernorat").regex("^" + java.util.regex.Pattern.quote(governorate.trim()) + "$", "i"));
        }

        if (openDateFrom != null || openDateTo != null) {
            Criteria openDateCriteria = Criteria.where("openDateSurTimosDate");
            if (openDateFrom != null) {
                openDateCriteria = openDateCriteria.gte(openDateFrom);
            }
            if (openDateTo != null) {
                openDateCriteria = openDateCriteria.lte(openDateTo);
            }
            query.addCriteria(openDateCriteria);
        }

        if (affectationDateFrom != null || affectationDateTo != null) {
            Criteria affectationDateCriteria = Criteria.where("dateAffectationSttDate");
            if (affectationDateFrom != null) {
                affectationDateCriteria = affectationDateCriteria.gte(affectationDateFrom);
            }
            if (affectationDateTo != null) {
                affectationDateCriteria = affectationDateCriteria.lte(affectationDateTo);
            }
            query.addCriteria(affectationDateCriteria);
        }

        List<ImportedTodoEntity> rows = mongoTemplate.find(query, ImportedTodoEntity.class);
        List<ImportedTodoEntity> validRows = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getValidRow()))
                .toList();
        List<ImportedTodoEntity> invalidRows = rows.stream()
                .filter(row -> !Boolean.TRUE.equals(row.getValidRow()))
                .toList();

        Map<String, Long> invalidReasons = new LinkedHashMap<>();
        for (ImportedTodoEntity row : invalidRows) {
            List<String> issues = row.getValidationIssues() == null || row.getValidationIssues().isEmpty()
                    ? List.of("Unknown validation issue")
                    : row.getValidationIssues();
            for (String issue : issues) {
                invalidReasons.merge(issue, 1L, Long::sum);
            }
        }

        List<SavedZmPerformanceInvalidReasonResponse> invalidReasonSummaries = invalidReasons.entrySet().stream()
                .map(entry -> new SavedZmPerformanceInvalidReasonResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(SavedZmPerformanceInvalidReasonResponse::count).reversed()
                        .thenComparing(SavedZmPerformanceInvalidReasonResponse::reason, String.CASE_INSENSITIVE_ORDER))
                .toList();

        String selectedInvalidReason = hasText(invalidReason) ? invalidReason.trim() : null;
        List<ImportedTodoEntity> filteredInvalidRows = invalidRows.stream()
                .filter(row -> matchesInvalidReason(row, selectedInvalidReason))
                .sorted(savedRowComparator())
                .toList();

        int pageSize = normalizeInvalidPageSize(invalidPageSize);
        int totalPages = Math.max(1, (int) Math.ceil(filteredInvalidRows.size() / (double) pageSize));
        int page = normalizeInvalidPage(invalidPage, totalPages);
        int startIndex = Math.min((page - 1) * pageSize, filteredInvalidRows.size());
        int endIndex = Math.min(startIndex + pageSize, filteredInvalidRows.size());
        List<SavedZmPerformanceRowPreviewResponse> invalidPreviewRows = filteredInvalidRows.subList(startIndex, endIndex).stream()
                .map(this::toSavedRowPreviewResponse)
                .toList();

        return new SavedZmPerformanceRowQualityResponse(
                study.getSourceFileNames() == null ? List.of() : List.copyOf(study.getSourceFileNames()),
                rows.size(),
                validRows.size(),
                invalidRows.size(),
                distinctCount(rows, ImportedTodoEntity::getZoneManager),
                distinctCount(rows, ImportedTodoEntity::getStt),
                distinctCount(rows, ImportedTodoEntity::getGouvernorat),
                validRows.stream().filter(row -> row.getSlaBusinessDays() != null).count(),
                invalidRows.stream().filter(row -> row.getValidationIssues() != null && row.getValidationIssues().size() > 1).count(),
                pageSize,
                page,
                filteredInvalidRows.size(),
                totalPages,
                selectedInvalidReason,
                invalidReasonSummaries,
                invalidPreviewRows
        );
    }

    private SavedZmPerformanceStudyResponse toResponse(SavedZmPerformanceStudyEntity entity) {
        return new SavedZmPerformanceStudyResponse(
                entity.getId(),
                entity.getSavedAt(),
                entity.getSavedBy(),
                entity.getSourceFileNames() == null ? List.of() : List.copyOf(entity.getSourceFileNames()),
                entity.getSourceBatchIds() == null ? List.of() : List.copyOf(entity.getSourceBatchIds()),
                entity.getStudy()
        );
    }

    private List<String> distinctOrderedValues(List<ImportedTodoEntity> rows, ValueExtractor extractor) {
        Map<String, String> values = new LinkedHashMap<>();
        for (ImportedTodoEntity row : rows) {
            String value = extractor.extract(row);
            if (value == null || value.isBlank()) {
                continue;
            }

            values.putIfAbsent(value.trim().toLowerCase(), value.trim());
        }

        return List.copyOf(values.values());
    }

    private List<String> loadDistinctOrderedValues(Query query, String fieldName) {
        List<String> values = mongoTemplate.query(ImportedTodoEntity.class)
                .distinct(fieldName)
                .matching(query)
                .as(String.class)
                .all();

        Map<String, String> orderedValues = new LinkedHashMap<>();
        for (String value : values) {
            String cleaned = TodoAnalyticsSupport.cleanString(value);
            if (!TodoAnalyticsSupport.hasText(cleaned)) {
                continue;
            }

            orderedValues.putIfAbsent(TodoAnalyticsSupport.normalizeLookupValue(cleaned), cleaned);
        }

        return List.copyOf(orderedValues.values());
    }

    private long distinctCount(List<ImportedTodoEntity> rows, ValueExtractor extractor) {
        return rows.stream()
                .map(extractor::extract)
                .filter(this::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
    }

    private SavedZmPerformanceRowPreviewResponse toSavedRowPreviewResponse(ImportedTodoEntity entity) {
        List<String> issues = entity.getValidationIssues() == null || entity.getValidationIssues().isEmpty()
                ? List.of()
                : List.copyOf(entity.getValidationIssues());

        return new SavedZmPerformanceRowPreviewResponse(
                entity.getSourceFileName(),
                entity.getUploadedAt(),
                entity.getRowNumber() == null ? 0 : entity.getRowNumber(),
                entity.getCrmCase(),
                entity.getZoneManager(),
                entity.getGouvernorat(),
                entity.getStt(),
                entity.getStatut(),
                entity.getOpenDateSurTimos(),
                entity.getDateAffectationStt(),
                entity.getSlaBusinessDays(),
                issues,
                Boolean.TRUE.equals(entity.getValidRow())
        );
    }

    private Comparator<ImportedTodoEntity> savedRowComparator() {
        return Comparator
                .comparing(ImportedTodoEntity::getUploadedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ImportedTodoEntity::getSourceFileName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(ImportedTodoEntity::getRowNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ImportedTodoEntity::getId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private boolean matchesInvalidReason(ImportedTodoEntity row, String selectedInvalidReason) {
        if (!hasText(selectedInvalidReason)) {
            return true;
        }

        List<String> issues = row.getValidationIssues() == null || row.getValidationIssues().isEmpty()
                ? List.of("Unknown validation issue")
                : row.getValidationIssues();

        return issues.stream().anyMatch(issue -> selectedInvalidReason.equalsIgnoreCase(issue));
    }

    private int normalizeInvalidPageSize(Integer invalidPageSize) {
        if (invalidPageSize == null) {
            return INVALID_PAGE_SIZE_DEFAULT;
        }

        return Math.max(1, Math.min(INVALID_PAGE_SIZE_MAX, invalidPageSize));
    }

    private int normalizeInvalidPage(Integer invalidPage, int totalPages) {
        if (invalidPage == null) {
            return 1;
        }

        return Math.max(1, Math.min(totalPages, invalidPage));
    }

    private void validateDateRange(String label, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " To date must be on or after " + label + " From date");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private long elapsedMs(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1_000_000d);
    }

    @FunctionalInterface
    private interface ValueExtractor {
        String extract(ImportedTodoEntity entity);
    }
}

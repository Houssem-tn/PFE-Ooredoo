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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SavedZmPerformanceStudyService {
    private static final int ROW_PREVIEW_LIMIT = 120;

    private final MongoTemplate mongoTemplate;
    private final SavedZmPerformanceStudyRepository savedZmPerformanceStudyRepository;

    public SavedZmPerformanceStudyResponse saveCurrentStudy(String savedBy, SaveZmPerformanceStudyRequest request) {
        Query stagingQuery = new Query(Criteria.where("sourceType").is(ImportedTodoSourceType.ADMIN_PERFORMANCE_IMPORT));
        List<ImportedTodoEntity> stagingRows = mongoTemplate.find(stagingQuery, ImportedTodoEntity.class);

        if (stagingRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no imported admin performance batch to save");
        }

        if (request == null || request.study() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no dashboard snapshot to save");
        }

        ZmPerformanceStudyResponse study = request.study();

        if (study.totalCases() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no dashboard data to save");
        }

        SavedZmPerformanceStudyEntity entity = SavedZmPerformanceStudyEntity.builder()
                .savedAt(Instant.now())
                .savedBy(savedBy)
                .sourceFileNames(distinctOrderedValues(stagingRows, ImportedTodoEntity::getSourceFileName))
                .sourceBatchIds(distinctOrderedValues(stagingRows, ImportedTodoEntity::getImportBatchId))
                .study(study)
                .build();

        SavedZmPerformanceStudyEntity savedEntity = savedZmPerformanceStudyRepository.save(entity);
        Update update = new Update()
                .set("sourceType", ImportedTodoSourceType.SAVED_ADMIN_PERFORMANCE_IMPORT)
                .set("savedStudyId", savedEntity.getId());
        mongoTemplate.updateMulti(stagingQuery, update, ImportedTodoEntity.class);
        return toResponse(savedEntity);
    }

    public List<SavedZmPerformanceStudyResponse> listSavedStudies() {
        return savedZmPerformanceStudyRepository.findAllByOrderBySavedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public SavedZmPerformanceRowQualityResponse getSavedStudyRowQuality(
            String savedStudyId,
            String zoneManager,
            String governorate,
            LocalDate uploadedFrom,
            LocalDate uploadedTo
    ) {
        if (!hasText(savedStudyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saved study id is required");
        }

        validateUploadedRange(uploadedFrom, uploadedTo);

        SavedZmPerformanceStudyEntity study = savedZmPerformanceStudyRepository.findById(savedStudyId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved study was not found"));

        Query query = new Query();
        query.addCriteria(Criteria.where("sourceType").is(ImportedTodoSourceType.SAVED_ADMIN_PERFORMANCE_IMPORT));
        query.addCriteria(Criteria.where("savedStudyId").is(savedStudyId.trim()));

        if (hasText(zoneManager)) {
            query.addCriteria(Criteria.where("zoneManagerNormalized").is(zoneManager.trim().toLowerCase(Locale.ROOT)));
        }

        if (hasText(governorate)) {
            query.addCriteria(Criteria.where("gouvernorat").regex("^" + java.util.regex.Pattern.quote(governorate.trim()) + "$", "i"));
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
                ROW_PREVIEW_LIMIT,
                invalidReasonSummaries,
                validRows.stream()
                        .sorted(savedRowComparator())
                        .limit(ROW_PREVIEW_LIMIT)
                        .map(this::toSavedRowPreviewResponse)
                        .toList(),
                invalidRows.stream()
                        .sorted(savedRowComparator())
                        .limit(ROW_PREVIEW_LIMIT)
                        .map(this::toSavedRowPreviewResponse)
                        .toList()
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

    private void validateUploadedRange(LocalDate uploadedFrom, LocalDate uploadedTo) {
        if (uploadedFrom != null && uploadedTo != null && uploadedTo.isBefore(uploadedFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded To date must be on or after Uploaded From date");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface ValueExtractor {
        String extract(ImportedTodoEntity entity);
    }
}

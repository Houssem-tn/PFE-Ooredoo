package com.pfe.case_management.service;

import com.pfe.case_management.dto.SaveZmPerformanceStudyRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SavedZmPerformanceStudyService {

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

    @FunctionalInterface
    private interface ValueExtractor {
        String extract(ImportedTodoEntity entity);
    }
}

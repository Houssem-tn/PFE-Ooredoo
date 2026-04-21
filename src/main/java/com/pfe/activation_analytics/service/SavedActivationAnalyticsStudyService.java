package com.pfe.activation_analytics.service;

import com.pfe.activation_analytics.dto.AdminActivationAnalyticsChartsResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsDashboardResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsRowsPageResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsSummaryResponse;
import com.pfe.activation_analytics.dto.SaveActivationAnalyticsStudyRequest;
import com.pfe.activation_analytics.dto.SavedActivationAnalyticsStudyResponse;
import com.pfe.activation_analytics.entity.ActivationAnalyticsImportBatchEntity;
import com.pfe.activation_analytics.entity.SavedActivationAnalyticsStudyEntity;
import com.pfe.activation_analytics.repository.ActivationAnalyticsImportBatchRepository;
import com.pfe.activation_analytics.repository.SavedActivationAnalyticsStudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SavedActivationAnalyticsStudyService {

    private final ActivationAnalyticsImportBatchRepository batchRepository;
    private final SavedActivationAnalyticsStudyRepository savedStudyRepository;
    private final ActivationAnalyticsDashboardService dashboardService;

    public SavedActivationAnalyticsStudyResponse saveCurrentStudy(String savedBy, SaveActivationAnalyticsStudyRequest request) {
        AdminActivationAnalyticsDashboardResponse dashboard = request == null ? null : request.dashboard();
        if (dashboard == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no activation dashboard snapshot to save");
        }

        if (dashboard.kpis() == null || dashboard.kpis().totalCases() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no activation analytics data to save");
        }

        List<ActivationAnalyticsImportBatchEntity> batches = batchRepository.findAllByUploadedByOrderByUploadedAtDesc(savedBy);
        if (batches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no imported activation dataset to save");
        }

        SavedActivationAnalyticsStudyEntity entity = SavedActivationAnalyticsStudyEntity.builder()
                .savedAt(Instant.now())
                .savedBy(savedBy)
                .sourceFileNames(distinctOrderedValues(batches, ActivationAnalyticsImportBatchEntity::getFileName))
                .sourceBatchIds(distinctOrderedValues(batches, ActivationAnalyticsImportBatchEntity::getBatchId))
                .dashboardSnapshot(dashboard)
                .build();

        return toResponse(savedStudyRepository.save(entity));
    }

    public List<SavedActivationAnalyticsStudyResponse> listSavedStudies(String savedBy) {
        return savedStudyRepository.findAllBySavedByOrderBySavedAtDesc(savedBy).stream()
                .map(this::toResponse)
                .toList();
    }

    public AdminActivationAnalyticsDashboardResponse getSavedStudyDashboard(
            String savedBy,
            String studyId,
            String stt,
            String governorate
    ) {
        SavedActivationAnalyticsStudyEntity study = savedStudyRepository.findByIdAndSavedBy(studyId, savedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved activation analysis was not found"));

        boolean hasAdHocFilters = ActivationAnalyticsSupport.hasText(stt) || ActivationAnalyticsSupport.hasText(governorate);
        if (!hasAdHocFilters && study.getDashboardSnapshot() != null) {
            return study.getDashboardSnapshot();
        }

        return dashboardService.getSavedStudyDashboard(savedBy, study.getSourceBatchIds(), stt, governorate);
    }

    public AdminActivationAnalyticsSummaryResponse getSavedStudySummary(
            String savedBy,
            String studyId,
            String stt,
            String governorate
    ) {
        SavedActivationAnalyticsStudyEntity study = resolveOwnedStudy(studyId, savedBy);
        boolean hasAdHocFilters = ActivationAnalyticsSupport.hasText(stt) || ActivationAnalyticsSupport.hasText(governorate);
        if (!hasAdHocFilters && study.getDashboardSnapshot() != null) {
            return dashboardService.toSummary(study.getDashboardSnapshot());
        }

        return dashboardService.getSavedStudySummary(savedBy, study.getSourceBatchIds(), stt, governorate);
    }

    public AdminActivationAnalyticsChartsResponse getSavedStudyCharts(
            String savedBy,
            String studyId,
            String stt,
            String governorate
    ) {
        SavedActivationAnalyticsStudyEntity study = resolveOwnedStudy(studyId, savedBy);
        boolean hasAdHocFilters = ActivationAnalyticsSupport.hasText(stt) || ActivationAnalyticsSupport.hasText(governorate);
        if (!hasAdHocFilters && study.getDashboardSnapshot() != null) {
            return dashboardService.toCharts(study.getDashboardSnapshot());
        }

        return dashboardService.getSavedStudyCharts(savedBy, study.getSourceBatchIds(), stt, governorate);
    }

    public AdminActivationAnalyticsRowsPageResponse getSavedStudyRows(
            String savedBy,
            String studyId,
            String stt,
            String governorate,
            int page,
            int size
    ) {
        SavedActivationAnalyticsStudyEntity study = resolveOwnedStudy(studyId, savedBy);
        return dashboardService.getSavedStudyRows(savedBy, study.getSourceBatchIds(), stt, governorate, page, size);
    }

    private SavedActivationAnalyticsStudyResponse toResponse(SavedActivationAnalyticsStudyEntity entity) {
        return new SavedActivationAnalyticsStudyResponse(
                entity.getId(),
                entity.getSavedAt(),
                entity.getSavedBy(),
                entity.getSourceFileNames() == null ? List.of() : List.copyOf(entity.getSourceFileNames()),
                entity.getSourceBatchIds() == null ? List.of() : List.copyOf(entity.getSourceBatchIds()),
                entity.getDashboardSnapshot()
        );
    }

    private SavedActivationAnalyticsStudyEntity resolveOwnedStudy(String studyId, String savedBy) {
        return savedStudyRepository.findByIdAndSavedBy(studyId, savedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved activation analysis was not found"));
    }

    private List<String> distinctOrderedValues(
            List<ActivationAnalyticsImportBatchEntity> items,
            java.util.function.Function<ActivationAnalyticsImportBatchEntity, String> extractor
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        for (ActivationAnalyticsImportBatchEntity item : items) {
            String value = extractor.apply(item);
            if (!ActivationAnalyticsSupport.hasText(value)) {
                continue;
            }
            values.putIfAbsent(value.trim().toLowerCase(), value.trim());
        }
        return List.copyOf(values.values());
    }
}

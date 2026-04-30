package com.pfe.plainte_analytics.service;

import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsChartsResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsDashboardResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsRowsPageResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsSummaryResponse;
import com.pfe.plainte_analytics.dto.SavePlainteAnalyticsStudyRequest;
import com.pfe.plainte_analytics.dto.SavedPlainteAnalyticsStudyResponse;
import com.pfe.plainte_analytics.entity.PlainteAnalyticsImportBatchEntity;
import com.pfe.plainte_analytics.entity.SavedPlainteAnalyticsStudyEntity;
import com.pfe.plainte_analytics.repository.PlainteAnalyticsImportBatchRepository;
import com.pfe.plainte_analytics.repository.SavedPlainteAnalyticsStudyRepository;
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
public class SavedPlainteAnalyticsStudyService {

    private final PlainteAnalyticsImportBatchRepository batchRepository;
    private final SavedPlainteAnalyticsStudyRepository savedStudyRepository;
    private final PlainteAnalyticsDashboardService dashboardService;

    public SavedPlainteAnalyticsStudyResponse saveCurrentStudy(String savedBy, SavePlainteAnalyticsStudyRequest request) {
        AdminPlainteAnalyticsDashboardResponse dashboard = request == null ? null : request.dashboard();
        if (dashboard == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no plainte dashboard snapshot to save");
        }

        if (dashboard.kpis() == null || dashboard.kpis().totalCases() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no plainte analytics data to save");
        }

        List<PlainteAnalyticsImportBatchEntity> batches = batchRepository.findAllByUploadedByOrderByUploadedAtDesc(savedBy);
        if (batches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no imported plainte dataset to save");
        }

        SavedPlainteAnalyticsStudyEntity entity = SavedPlainteAnalyticsStudyEntity.builder()
                .savedAt(Instant.now())
                .savedBy(savedBy)
                .sourceFileNames(distinctOrderedValues(batches, PlainteAnalyticsImportBatchEntity::getFileName))
                .sourceBatchIds(distinctOrderedValues(batches, PlainteAnalyticsImportBatchEntity::getBatchId))
                .dashboardSnapshot(dashboard)
                .build();

        return toResponse(savedStudyRepository.save(entity));
    }

    public List<SavedPlainteAnalyticsStudyResponse> listSavedStudies(String savedBy) {
        return savedStudyRepository.findAllBySavedByOrderBySavedAtDesc(savedBy).stream()
                .map(this::toResponse)
                .toList();
    }

    public AdminPlainteAnalyticsDashboardResponse getSavedStudyDashboard(
            String savedBy,
            String studyId,
            String stt,
            String governorate
    ) {
        SavedPlainteAnalyticsStudyEntity study = savedStudyRepository.findByIdAndSavedBy(studyId, savedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved plainte analysis was not found"));

        boolean hasAdHocFilters = PlainteAnalyticsSupport.hasText(stt) || PlainteAnalyticsSupport.hasText(governorate);
        if (!hasAdHocFilters && study.getDashboardSnapshot() != null) {
            return study.getDashboardSnapshot();
        }

        return dashboardService.getSavedStudyDashboard(savedBy, study.getSourceBatchIds(), stt, governorate);
    }

    public AdminPlainteAnalyticsSummaryResponse getSavedStudySummary(
            String savedBy,
            String studyId,
            String stt,
            String governorate
    ) {
        SavedPlainteAnalyticsStudyEntity study = resolveOwnedStudy(studyId, savedBy);
        boolean hasAdHocFilters = PlainteAnalyticsSupport.hasText(stt) || PlainteAnalyticsSupport.hasText(governorate);
        if (!hasAdHocFilters && study.getDashboardSnapshot() != null) {
            return dashboardService.toSummary(study.getDashboardSnapshot());
        }

        return dashboardService.getSavedStudySummary(savedBy, study.getSourceBatchIds(), stt, governorate);
    }

    public AdminPlainteAnalyticsChartsResponse getSavedStudyCharts(
            String savedBy,
            String studyId,
            String stt,
            String governorate
    ) {
        SavedPlainteAnalyticsStudyEntity study = resolveOwnedStudy(studyId, savedBy);
        boolean hasAdHocFilters = PlainteAnalyticsSupport.hasText(stt) || PlainteAnalyticsSupport.hasText(governorate);
        if (!hasAdHocFilters && study.getDashboardSnapshot() != null) {
            return dashboardService.toCharts(study.getDashboardSnapshot());
        }

        return dashboardService.getSavedStudyCharts(savedBy, study.getSourceBatchIds(), stt, governorate);
    }

    public AdminPlainteAnalyticsRowsPageResponse getSavedStudyRows(
            String savedBy,
            String studyId,
            String stt,
            String governorate,
            int page,
            int size
    ) {
        SavedPlainteAnalyticsStudyEntity study = resolveOwnedStudy(studyId, savedBy);
        return dashboardService.getSavedStudyRows(savedBy, study.getSourceBatchIds(), stt, governorate, page, size);
    }

    private SavedPlainteAnalyticsStudyResponse toResponse(SavedPlainteAnalyticsStudyEntity entity) {
        return new SavedPlainteAnalyticsStudyResponse(
                entity.getId(),
                entity.getSavedAt(),
                entity.getSavedBy(),
                entity.getSourceFileNames() == null ? List.of() : List.copyOf(entity.getSourceFileNames()),
                entity.getSourceBatchIds() == null ? List.of() : List.copyOf(entity.getSourceBatchIds()),
                entity.getDashboardSnapshot()
        );
    }

    private SavedPlainteAnalyticsStudyEntity resolveOwnedStudy(String studyId, String savedBy) {
        return savedStudyRepository.findByIdAndSavedBy(studyId, savedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved plainte analysis was not found"));
    }

    private List<String> distinctOrderedValues(
            List<PlainteAnalyticsImportBatchEntity> items,
            java.util.function.Function<PlainteAnalyticsImportBatchEntity, String> extractor
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        for (PlainteAnalyticsImportBatchEntity item : items) {
            String value = extractor.apply(item);
            if (!PlainteAnalyticsSupport.hasText(value)) {
                continue;
            }
            values.putIfAbsent(value.trim().toLowerCase(), value.trim());
        }
        return List.copyOf(values.values());
    }
}

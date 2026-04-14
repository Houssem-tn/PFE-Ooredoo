package com.pfe.case_management.service;

import com.pfe.case_management.dto.ZmTodoAnalysisDetailResponse;
import com.pfe.case_management.dto.ZmTodoAnalysisListItemResponse;
import com.pfe.case_management.dto.ZmTodoAnalyticsResponse;
import com.pfe.case_management.entity.ZmTodoAnalysisEntity;
import com.pfe.case_management.repository.ZmTodoAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ZmTodoAnalysisService {

    private final ZmTodoAnalysisRepository zmTodoAnalysisRepository;

    public ZmTodoAnalysisEntity saveAnalysis(
            String uploadedBy,
            Instant uploadedAt,
            String sourceFileName,
            String importBatchId,
            ZmTodoAnalyticsResponse analytics
    ) {
        ZmTodoAnalysisEntity entity = ZmTodoAnalysisEntity.builder()
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .importBatchId(importBatchId)
                .sourceFileName(sourceFileName)
                .connectedZmName(analytics.connectedZmName())
                .managerNameUsed(analytics.managerNameUsed())
                .analytics(analytics)
                .build();

        return zmTodoAnalysisRepository.save(entity);
    }

    public List<ZmTodoAnalysisListItemResponse> listAnalyses(String uploadedBy) {
        return zmTodoAnalysisRepository.findAllByUploadedByOrderByUploadedAtDesc(uploadedBy).stream()
                .map(this::toListItemResponse)
                .toList();
    }

    public ZmTodoAnalysisDetailResponse getAnalysisDetail(String analysisId, String uploadedBy) {
        ZmTodoAnalysisEntity entity = zmTodoAnalysisRepository.findByIdAndUploadedBy(analysisId, uploadedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved todo analysis not found"));

        return new ZmTodoAnalysisDetailResponse(
                entity.getId(),
                entity.getSourceFileName(),
                entity.getUploadedAt(),
                entity.getImportBatchId(),
                entity.getAnalytics()
        );
    }

    private ZmTodoAnalysisListItemResponse toListItemResponse(ZmTodoAnalysisEntity entity) {
        ZmTodoAnalyticsResponse analytics = entity.getAnalytics();
        return new ZmTodoAnalysisListItemResponse(
                entity.getId(),
                entity.getSourceFileName(),
                entity.getUploadedAt(),
                entity.getImportBatchId(),
                entity.getConnectedZmName(),
                analytics != null ? analytics.totalCases() : 0L,
                analytics != null ? analytics.matchedRows() : 0,
                analytics != null ? analytics.invalidMatchedRows() : 0,
                analytics != null ? analytics.averageSlaBusinessDays() : null
        );
    }
}

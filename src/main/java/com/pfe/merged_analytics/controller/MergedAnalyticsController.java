package com.pfe.merged_analytics.controller;

import com.pfe.merged_analytics.dto.MergedAnalyticsChartsResponse;
import com.pfe.merged_analytics.dto.MergedAnalyticsFiltersResponse;
import com.pfe.merged_analytics.dto.MergedAnalyticsInvalidRowDto;
import com.pfe.merged_analytics.dto.MergedAnalyticsInvalidRowsPageResponse;
import com.pfe.merged_analytics.dto.MergedAnalyticsSummaryResponse;
import com.pfe.merged_analytics.dto.MergedAnalyticsStatusResponse;
import com.pfe.merged_analytics.dto.MergedAnalyticsStudyListItem;
import com.pfe.merged_analytics.dto.MergedAnalyticsUploadResponse;
import com.pfe.merged_analytics.entity.MergedAnalyticsInvalidRowEntity;
import com.pfe.merged_analytics.entity.MergedAnalyticsStudyEntity;
import com.pfe.merged_analytics.repository.MergedAnalyticsInvalidRowRepository;
import com.pfe.merged_analytics.repository.MergedAnalyticsRowRepository;
import com.pfe.merged_analytics.repository.MergedAnalyticsStudyRepository;
import com.pfe.merged_analytics.service.MergedAnalyticsAggregationService;
import com.pfe.merged_analytics.service.MergedAnalyticsAggregationService.MergedAnalyticsFilterParams;
import com.pfe.merged_analytics.service.MergedAnalyticsImportService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/merged-analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class MergedAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(MergedAnalyticsController.class);

    private final MergedAnalyticsImportService importService;
    private final MergedAnalyticsAggregationService aggregationService;
    private final MergedAnalyticsStudyRepository studyRepository;
    private final MergedAnalyticsRowRepository rowRepository;
    private final MergedAnalyticsInvalidRowRepository invalidRowRepository;

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MergedAnalyticsUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        long start = System.nanoTime();
        MergedAnalyticsUploadResponse response = importService.initiateUpload(file, authentication.getName());
        log.info("merged_analytics endpoint=upload studyId={} ms={}", response.studyId(), elapsedMs(start));
        return ResponseEntity.accepted().body(response);
    }

    // ── Status (polling) ──────────────────────────────────────────────────────

    @GetMapping("/{studyId}/status")
    public ResponseEntity<MergedAnalyticsStatusResponse> getStatus(
            @PathVariable String studyId,
            Authentication authentication
    ) {
        MergedAnalyticsStudyEntity study = requireStudy(studyId, authentication.getName());
        int total = study.getTotalRows();
        int processed = study.getProcessedRows();
        int percent = total > 0 ? (int) ((processed * 100L) / total) : 0;

        return ResponseEntity.ok(new MergedAnalyticsStatusResponse(
                study.getStudyId(),
                study.getProcessingStatus() != null ? study.getProcessingStatus().name() : "UNKNOWN",
                total,
                processed,
                study.getValidRows(),
                study.getInvalidRows(),
                study.getWarningRows(),
                percent,
                study.getProcessingDurationMs(),
                study.getErrorMessage()
        ));
    }

    // ── Filters (populated from study metadata) ───────────────────────────────

    @GetMapping("/{studyId}/filters")
    public ResponseEntity<MergedAnalyticsFiltersResponse> getFilters(
            @PathVariable String studyId,
            Authentication authentication
    ) {
        MergedAnalyticsStudyEntity study = requireStudy(studyId, authentication.getName());
        requireCompleted(study);
        return ResponseEntity.ok(aggregationService.getFilters(study));
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/{studyId}/summary")
    public ResponseEntity<MergedAnalyticsSummaryResponse> getSummary(
            @PathVariable String studyId,
            @RequestParam(required = false) List<String> source,
            @RequestParam(required = false) List<String> zoneManager,
            @RequestParam(required = false) List<String> stt,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> gouvernorat,
            @RequestParam(required = false) String dateBasis,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            Authentication authentication
    ) {
        MergedAnalyticsStudyEntity study = requireStudy(studyId, authentication.getName());
        requireCompleted(study);
        MergedAnalyticsFilterParams params = new MergedAnalyticsFilterParams(
                source, zoneManager, stt, status, gouvernorat, dateBasis, dateFrom, dateTo);
        return ResponseEntity.ok(aggregationService.getSummary(study, params));
    }

    // ── Charts ────────────────────────────────────────────────────────────────

    @GetMapping("/{studyId}/charts")
    public ResponseEntity<MergedAnalyticsChartsResponse> getCharts(
            @PathVariable String studyId,
            @RequestParam(required = false) List<String> source,
            @RequestParam(required = false) List<String> zoneManager,
            @RequestParam(required = false) List<String> stt,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> gouvernorat,
            @RequestParam(required = false) String dateBasis,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            Authentication authentication
    ) {
        MergedAnalyticsStudyEntity study = requireStudy(studyId, authentication.getName());
        requireCompleted(study);
        MergedAnalyticsFilterParams params = new MergedAnalyticsFilterParams(
                source, zoneManager, stt, status, gouvernorat, dateBasis, dateFrom, dateTo);
        return ResponseEntity.ok(aggregationService.getCharts(study, params));
    }

    // ── Invalid rows ──────────────────────────────────────────────────────────

    @GetMapping("/{studyId}/invalid-rows")
    public ResponseEntity<MergedAnalyticsInvalidRowsPageResponse> getInvalidRows(
            @PathVariable String studyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication
    ) {
        requireStudy(studyId, authentication.getName());
        int safeSize = Math.min(size, 200);
        Page<MergedAnalyticsInvalidRowEntity> result = invalidRowRepository.findAllByStudyId(
                studyId, PageRequest.of(page, safeSize, Sort.by("rowNumber")));

        List<MergedAnalyticsInvalidRowDto> dtos = result.getContent().stream()
                .map(e -> new MergedAnalyticsInvalidRowDto(
                        e.getId(), e.getRowNumber(), e.getCrmCase(),
                        e.getSource(), e.getZoneManager(), e.getReasons(), e.getRawPreview()))
                .toList();

        return ResponseEntity.ok(new MergedAnalyticsInvalidRowsPageResponse(
                dtos, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    // ── Study list ────────────────────────────────────────────────────────────

    @GetMapping("/studies")
    public ResponseEntity<List<MergedAnalyticsStudyListItem>> listStudies(Authentication authentication) {
        List<MergedAnalyticsStudyListItem> items = studyRepository
                .findAllByUploadedByOrderByUploadedAtDesc(authentication.getName())
                .stream()
                .map(s -> new MergedAnalyticsStudyListItem(
                        s.getStudyId(), s.getFileName(), s.getFileType(),
                        s.getUploadedAt(), s.getTotalRows(), s.getValidRows(),
                        s.getInvalidRows(),
                        s.getProcessingStatus() != null ? s.getProcessingStatus().name() : null,
                        s.getProcessingDurationMs(),
                        s.getSourcesFound()))
                .toList();
        return ResponseEntity.ok(items);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{studyId}")
    public ResponseEntity<Void> deleteStudy(@PathVariable String studyId, Authentication authentication) {
        MergedAnalyticsStudyEntity study = requireStudy(studyId, authentication.getName());
        rowRepository.deleteAllByStudyId(studyId);
        invalidRowRepository.deleteAllByStudyId(studyId);
        studyRepository.delete(study);
        log.info("merged_analytics endpoint=delete studyId={} by={}", studyId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MergedAnalyticsStudyEntity requireStudy(String studyId, String uploadedBy) {
        return studyRepository.findByStudyIdAndUploadedBy(studyId, uploadedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study not found: " + studyId));
    }

    private void requireCompleted(MergedAnalyticsStudyEntity study) {
        if (study.getProcessingStatus() == null || !study.getProcessingStatus().name().equals("COMPLETED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Study is not yet completed. Current status: " + study.getProcessingStatus());
        }
    }

    private long elapsedMs(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000L; }
}

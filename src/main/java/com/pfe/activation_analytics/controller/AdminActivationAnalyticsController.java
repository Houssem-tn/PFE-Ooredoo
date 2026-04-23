package com.pfe.activation_analytics.controller;

import com.pfe.activation_analytics.dto.AdminActivationAnalyticsChartsResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsDashboardResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsImportResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsPreviewResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsRowsPageResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsSummaryResponse;
import com.pfe.activation_analytics.dto.SaveActivationAnalyticsStudyRequest;
import com.pfe.activation_analytics.dto.SavedActivationAnalyticsStudyResponse;
import com.pfe.activation_analytics.model.ActivationImportProcessingMode;
import com.pfe.activation_analytics.service.ActivationAnalyticsDashboardService;
import com.pfe.activation_analytics.service.ActivationAnalyticsImportService;
import com.pfe.activation_analytics.service.SavedActivationAnalyticsStudyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/admin/activation-analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminActivationAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AdminActivationAnalyticsController.class);

    private final ActivationAnalyticsImportService importService;
    private final ActivationAnalyticsDashboardService dashboardService;
    private final SavedActivationAnalyticsStudyService savedStudyService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminActivationAnalyticsPreviewResponse> preview(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return timed("preview", () -> importService.preview(file, authentication.getName()));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminActivationAnalyticsImportResponse> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "mode", defaultValue = "ANALYTICS") ActivationImportProcessingMode mode,
            Authentication authentication
    ) {
        return timed("import", () -> importService.importFile(file, authentication.getName(), mode));
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminActivationAnalyticsSummaryResponse> getSummary(
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return timed("summary", () -> dashboardService.getSummary(authentication.getName(), stt, gouvernorat));
    }

    @GetMapping("/charts")
    public ResponseEntity<AdminActivationAnalyticsChartsResponse> getCharts(
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return timed("charts", () -> dashboardService.getCharts(authentication.getName(), stt, gouvernorat));
    }

    @GetMapping("/rows")
    public ResponseEntity<AdminActivationAnalyticsRowsPageResponse> getRows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return timed("rows", () -> dashboardService.getRows(authentication.getName(), stt, gouvernorat, page, size));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminActivationAnalyticsDashboardResponse> getDashboard(
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return timed("dashboard", () -> dashboardService.getDashboard(authentication.getName(), stt, gouvernorat));
    }

    @PostMapping("/save-study")
    public ResponseEntity<SavedActivationAnalyticsStudyResponse> saveStudy(
            @org.springframework.web.bind.annotation.RequestBody(required = false) SaveActivationAnalyticsStudyRequest request,
            Authentication authentication
    ) {
        return timed("save-study", () -> savedStudyService.saveCurrentStudy(authentication.getName(), request));
    }

    @GetMapping("/saved-studies")
    public ResponseEntity<java.util.List<SavedActivationAnalyticsStudyResponse>> listSavedStudies(Authentication authentication) {
        return timed("saved-studies", () -> savedStudyService.listSavedStudies(authentication.getName()));
    }

    @GetMapping("/saved-studies/{studyId}/summary")
    public ResponseEntity<AdminActivationAnalyticsSummaryResponse> getSavedStudySummary(
            @org.springframework.web.bind.annotation.PathVariable String studyId,
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return timed("saved-summary", () -> savedStudyService.getSavedStudySummary(
                authentication.getName(),
                studyId,
                stt,
                gouvernorat
        ));
    }

    @GetMapping("/saved-studies/{studyId}/charts")
    public ResponseEntity<AdminActivationAnalyticsChartsResponse> getSavedStudyCharts(
            @org.springframework.web.bind.annotation.PathVariable String studyId,
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return timed("saved-charts", () -> savedStudyService.getSavedStudyCharts(
                authentication.getName(),
                studyId,
                stt,
                gouvernorat
        ));
    }

    @GetMapping("/saved-studies/{studyId}/rows")
    public ResponseEntity<AdminActivationAnalyticsRowsPageResponse> getSavedStudyRows(
            @org.springframework.web.bind.annotation.PathVariable String studyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return timed("saved-rows", () -> savedStudyService.getSavedStudyRows(
                authentication.getName(),
                studyId,
                stt,
                gouvernorat,
                page,
                size
        ));
    }

    @GetMapping("/saved-studies/{studyId}/dashboard")
    public ResponseEntity<AdminActivationAnalyticsDashboardResponse> getSavedStudyDashboard(
            @org.springframework.web.bind.annotation.PathVariable String studyId,
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return timed("saved-dashboard", () -> savedStudyService.getSavedStudyDashboard(
                authentication.getName(),
                studyId,
                stt,
                gouvernorat
        ));
    }

    private <T> ResponseEntity<T> timed(String endpoint, Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            return ResponseEntity.ok(supplier.get());
        } finally {
            log.info("activation_analytics_endpoint endpoint={} responseMs={}", endpoint, elapsedMs(start));
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}

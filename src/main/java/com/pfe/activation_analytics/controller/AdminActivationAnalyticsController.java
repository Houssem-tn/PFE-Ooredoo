package com.pfe.activation_analytics.controller;

import com.pfe.activation_analytics.dto.AdminActivationAnalyticsDashboardResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsImportResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsPreviewResponse;
import com.pfe.activation_analytics.dto.SaveActivationAnalyticsStudyRequest;
import com.pfe.activation_analytics.dto.SavedActivationAnalyticsStudyResponse;
import com.pfe.activation_analytics.service.ActivationAnalyticsDashboardService;
import com.pfe.activation_analytics.service.ActivationAnalyticsImportService;
import com.pfe.activation_analytics.service.SavedActivationAnalyticsStudyService;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/admin/activation-analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminActivationAnalyticsController {

    private final ActivationAnalyticsImportService importService;
    private final ActivationAnalyticsDashboardService dashboardService;
    private final SavedActivationAnalyticsStudyService savedStudyService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminActivationAnalyticsPreviewResponse> preview(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(importService.preview(file, authentication.getName()));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminActivationAnalyticsImportResponse> importFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(importService.importFile(file, authentication.getName()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminActivationAnalyticsDashboardResponse> getDashboard(
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return ResponseEntity.ok(dashboardService.getDashboard(authentication.getName(), stt, gouvernorat));
    }

    @PostMapping("/save-study")
    public ResponseEntity<SavedActivationAnalyticsStudyResponse> saveStudy(
            @org.springframework.web.bind.annotation.RequestBody(required = false) SaveActivationAnalyticsStudyRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(savedStudyService.saveCurrentStudy(authentication.getName(), request));
    }

    @GetMapping("/saved-studies")
    public ResponseEntity<java.util.List<SavedActivationAnalyticsStudyResponse>> listSavedStudies(Authentication authentication) {
        return ResponseEntity.ok(savedStudyService.listSavedStudies(authentication.getName()));
    }

    @GetMapping("/saved-studies/{studyId}/dashboard")
    public ResponseEntity<AdminActivationAnalyticsDashboardResponse> getSavedStudyDashboard(
            @org.springframework.web.bind.annotation.PathVariable String studyId,
            @RequestParam(required = false) String stt,
            @RequestParam(required = false) String gouvernorat,
            Authentication authentication
    ) {
        return ResponseEntity.ok(savedStudyService.getSavedStudyDashboard(
                authentication.getName(),
                studyId,
                stt,
                gouvernorat
        ));
    }
}

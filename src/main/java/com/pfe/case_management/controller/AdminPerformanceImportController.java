package com.pfe.case_management.controller;

import com.pfe.case_management.dto.AdminPerformanceImportInjectResponse;
import com.pfe.case_management.dto.AdminPerformanceImportPreviewResponse;
import com.pfe.case_management.dto.SaveZmPerformanceStudyRequest;
import com.pfe.case_management.dto.SavedZmPerformanceStudyResponse;
import com.pfe.case_management.dto.ZmPerformanceStudyResponse;
import com.pfe.case_management.service.SavedZmPerformanceStudyService;
import com.pfe.case_management.service.TodoImportService;
import com.pfe.case_management.service.ZmPerformanceAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/performance-imports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminPerformanceImportController {

    private final TodoImportService todoImportService;
    private final ZmPerformanceAnalyticsService zmPerformanceAnalyticsService;
    private final SavedZmPerformanceStudyService savedZmPerformanceStudyService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminPerformanceImportPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(todoImportService.previewAdminPerformanceImport(file));
    }

    @PostMapping(value = "/inject", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminPerformanceImportInjectResponse> inject(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(todoImportService.injectAdminPerformanceImport(file, authentication.getName()));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ZmPerformanceStudyResponse> getAnalytics(
            @RequestParam(required = false) String zoneManager,
            @RequestParam(required = false) String gouvernorat,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedTo
    ) {
        return ResponseEntity.ok(zmPerformanceAnalyticsService.getStudy(zoneManager, gouvernorat, uploadedFrom, uploadedTo));
    }

    @PostMapping("/save-study")
    public ResponseEntity<SavedZmPerformanceStudyResponse> saveStudy(
            @RequestBody(required = false) SaveZmPerformanceStudyRequest request,
            Authentication authentication
    ) {
        SaveZmPerformanceStudyRequest safeRequest = request == null
                ? new SaveZmPerformanceStudyRequest(null)
                : request;
        return ResponseEntity.ok(savedZmPerformanceStudyService.saveCurrentStudy(authentication.getName(), safeRequest));
    }

    @GetMapping("/saved-studies")
    public ResponseEntity<java.util.List<SavedZmPerformanceStudyResponse>> listSavedStudies() {
        return ResponseEntity.ok(savedZmPerformanceStudyService.listSavedStudies());
    }

    @GetMapping("/saved-studies/{studyId}/analytics")
    public ResponseEntity<ZmPerformanceStudyResponse> getSavedStudyAnalytics(
            @PathVariable String studyId,
            @RequestParam(required = false) String zoneManager,
            @RequestParam(required = false) String gouvernorat,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedTo
    ) {
        return ResponseEntity.ok(zmPerformanceAnalyticsService.getSavedStudy(studyId, zoneManager, gouvernorat, uploadedFrom, uploadedTo));
    }
}

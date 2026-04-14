package com.pfe.case_management.controller;

import com.pfe.case_management.dto.ImportedTodoPageResponse;
import com.pfe.case_management.dto.TodoImportInjectResponse;
import com.pfe.case_management.dto.TodoImportPreviewResponse;
import com.pfe.case_management.dto.ZmTodoAnalysisDetailResponse;
import com.pfe.case_management.dto.ZmTodoAnalysisListItemResponse;
import com.pfe.case_management.dto.ZmTodoImportProcessResponse;
import com.pfe.case_management.dto.ZmTodoAnalyticsResponse;
import com.pfe.case_management.service.TodoImportService;
import com.pfe.case_management.service.ZmTodoAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/zm/todo-imports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ZM')")
public class TodoImportController {

    private final TodoImportService todoImportService;
    private final ZmTodoAnalysisService zmTodoAnalysisService;

    @GetMapping
    public ResponseEntity<ImportedTodoPageResponse> listImportedTodos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String rowQuality,
            @RequestParam(required = false) String gouvernorat,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedTo,
            Authentication authentication
    ) {
        return ResponseEntity.ok(todoImportService.listImportedTodos(
                authentication.getName(),
                page,
                size,
                search,
                statut,
                rowQuality,
                gouvernorat,
                uploadedFrom,
                uploadedTo
        ));
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TodoImportPreviewResponse> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("managerFilter") String managerFilter
    ) {
        return ResponseEntity.ok(todoImportService.preview(file, managerFilter));
    }

    @PostMapping(value = "/inject", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TodoImportInjectResponse> inject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("managerFilter") String managerFilter,
            Authentication authentication
    ) {
        return ResponseEntity.ok(todoImportService.inject(file, managerFilter, authentication.getName()));
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ZmTodoAnalyticsResponse> analyze(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(todoImportService.analyzeForZm(file, authentication.getName()));
    }

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ZmTodoImportProcessResponse> process(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(todoImportService.processForZm(file, authentication.getName()));
    }

    @GetMapping("/analyses")
    public ResponseEntity<java.util.List<ZmTodoAnalysisListItemResponse>> listSavedAnalyses(
            Authentication authentication
    ) {
        return ResponseEntity.ok(zmTodoAnalysisService.listAnalyses(authentication.getName()));
    }

    @GetMapping("/analyses/{analysisId}")
    public ResponseEntity<ZmTodoAnalysisDetailResponse> getSavedAnalysis(
            @PathVariable String analysisId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(zmTodoAnalysisService.getAnalysisDetail(analysisId, authentication.getName()));
    }
}

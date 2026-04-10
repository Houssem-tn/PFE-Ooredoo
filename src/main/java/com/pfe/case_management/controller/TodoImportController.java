package com.pfe.case_management.controller;

import com.pfe.case_management.dto.ImportedTodoPageResponse;
import com.pfe.case_management.dto.TodoImportInjectResponse;
import com.pfe.case_management.dto.TodoImportPreviewResponse;
import com.pfe.case_management.service.TodoImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
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

    @GetMapping
    public ResponseEntity<ImportedTodoPageResponse> listImportedTodos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String statut,
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
}

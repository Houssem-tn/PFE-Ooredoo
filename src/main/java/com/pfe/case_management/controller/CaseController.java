package com.pfe.case_management.controller;

import com.pfe.case_management.dto.AssignSttRequest;
import com.pfe.case_management.dto.AssignTechRequest;
import com.pfe.case_management.dto.AssignZmRequest;
import com.pfe.case_management.dto.CompleteWorkRequest;
import com.pfe.case_management.dto.CreateCaseRequest;
import com.pfe.case_management.dto.SetRdvRequest;
import com.pfe.case_management.dto.ValidateZmRequest;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.service.CaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @PostMapping
    public ResponseEntity<CaseEntity> createCase(@Valid @RequestBody CreateCaseRequest request) {
        return ResponseEntity.ok(caseService.createCase(request));
    }

    @GetMapping
    public ResponseEntity<List<CaseEntity>> getCasesByType(@RequestParam CaseType type) {
        return ResponseEntity.ok(caseService.getAllCasesByType(type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseEntity> getCaseById(@PathVariable String id) {
        return ResponseEntity.ok(caseService.getCaseById(id));
    }

    @PostMapping("/{id}/assign-zm")
    public ResponseEntity<CaseEntity> assignToZm(
            @PathVariable String id,
            @Valid @RequestBody AssignZmRequest request
    ) {
        return ResponseEntity.ok(caseService.assignToZm(id, request));
    }

    @PostMapping("/{id}/assign-stt")
    public ResponseEntity<CaseEntity> assignToStt(
            @PathVariable String id,
            @Valid @RequestBody AssignSttRequest request
    ) {
        return ResponseEntity.ok(caseService.assignToStt(id, request));
    }

    @PostMapping("/{id}/assign-tech")
    public ResponseEntity<CaseEntity> assignToTech(
            @PathVariable String id,
            @Valid @RequestBody AssignTechRequest request
    ) {
        return ResponseEntity.ok(caseService.assignToTech(id, request));
    }

    @PostMapping("/{id}/rdv")
    public ResponseEntity<CaseEntity> setRdv(
            @PathVariable String id,
            @Valid @RequestBody SetRdvRequest request
    ) {
        return ResponseEntity.ok(caseService.setRdv(id, request));
    }

    @PostMapping("/{id}/work")
    public ResponseEntity<CaseEntity> completeWork(
            @PathVariable String id,
            @Valid @RequestBody CompleteWorkRequest request
    ) {
        return ResponseEntity.ok(caseService.completeWork(id, request));
    }

    @PostMapping("/{id}/work-proof")
    public ResponseEntity<CaseEntity> uploadWorkProof(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(caseService.uploadWorkProof(id, file));
    }

    @PostMapping("/{id}/validate-zm")
    public ResponseEntity<CaseEntity> validateByZm(
            @PathVariable String id,
            @Valid @RequestBody ValidateZmRequest request
    ) {
        return ResponseEntity.ok(caseService.validateByZm(id, request));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<CaseEntity> closeCase(@PathVariable String id) {
        return ResponseEntity.ok(caseService.closeCase(id));
    }
}

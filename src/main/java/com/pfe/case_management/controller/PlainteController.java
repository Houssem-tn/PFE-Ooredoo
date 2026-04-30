package com.pfe.case_management.controller;

import com.pfe.case_management.dto.ComplaintDiagnosisRequest;
import com.pfe.case_management.dto.ComplaintImportResponse;
import com.pfe.case_management.dto.PlainteDTO;
import com.pfe.case_management.dto.ReopenComplaintRequest;
import com.pfe.case_management.service.PlainteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plaintes")
@RequiredArgsConstructor
public class PlainteController {

    private final PlainteService plainteService;

    @GetMapping
    public ResponseEntity<List<PlainteDTO>> listComplaints() {
        return ResponseEntity.ok(plainteService.listComplaints());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlainteDTO> getComplaint(@PathVariable String id) {
        return ResponseEntity.ok(plainteService.getComplaint(id));
    }

    @PostMapping("/import")
    public ResponseEntity<ComplaintImportResponse> importComplaints(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(plainteService.importCsv(file, authentication.getName()));
    }

    @PostMapping("/{id}/diagnosis")
    public ResponseEntity<PlainteDTO> diagnose(
            @PathVariable String id,
            @Valid @RequestBody ComplaintDiagnosisRequest request
    ) {
        return ResponseEntity.ok(plainteService.diagnose(id, request));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<PlainteDTO> reopen(
            @PathVariable String id,
            @RequestBody ReopenComplaintRequest request
    ) {
        return ResponseEntity.ok(plainteService.reopen(id, request));
    }
}

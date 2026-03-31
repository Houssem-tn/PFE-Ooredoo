package com.pfe.case_management.controller;

import com.pfe.case_management.dto.PerformanceSummaryResponse;
import com.pfe.case_management.service.CaseAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/analytics")
@RequiredArgsConstructor
public class CaseAnalyticsController {

    private final CaseAnalyticsService caseAnalyticsService;

    @GetMapping("/my-performance")
    public ResponseEntity<PerformanceSummaryResponse> getMyPerformance() {
        return ResponseEntity.ok(caseAnalyticsService.getMyPerformance());
    }

    @GetMapping("/tech-performance")
    public ResponseEntity<List<PerformanceSummaryResponse>> getTechPerformance() {
        return ResponseEntity.ok(caseAnalyticsService.getTechPerformanceForMyScope());
    }
}
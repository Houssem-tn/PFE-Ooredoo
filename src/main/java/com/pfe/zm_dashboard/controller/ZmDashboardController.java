package com.pfe.zm_dashboard.controller;

import com.pfe.zm_dashboard.dto.ZmDashboardResponse;
import com.pfe.zm_dashboard.service.ZmDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only operational dashboard for the Zone Manager. Everything returned is scoped to the
 * authenticated ZM; no other ZM's data is exposed (see {@link ZmDashboardService}).
 */
@RestController
@RequestMapping("/api/v1/zm/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ZM')")
public class ZmDashboardController {

    private final ZmDashboardService zmDashboardService;

    @GetMapping
    public ResponseEntity<ZmDashboardResponse> getDashboard() {
        return ResponseEntity.ok(zmDashboardService.getDashboard());
    }
}

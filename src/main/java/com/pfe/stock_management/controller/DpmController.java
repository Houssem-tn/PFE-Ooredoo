package com.pfe.stock_management.controller;

import com.pfe.stock_management.dto.ConfirmDpmRequest;
import com.pfe.stock_management.dto.CreateDpmRequest;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.service.DpmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/dpms")
@RequiredArgsConstructor
public class DpmController {

    private final DpmService dpmService;

    @PostMapping
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<DpmRequest> createDpm(@Valid @RequestBody CreateDpmRequest request) {
        return ResponseEntity.ok(dpmService.createDpm(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DpmRequest> getById(@PathVariable String id) {
        return ResponseEntity.ok(dpmService.getById(id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('STT')")
    public ResponseEntity<DpmRequest> confirmDpm(
            @PathVariable String id,
            @Valid @RequestBody ConfirmDpmRequest request
    ) {
        return ResponseEntity.ok(dpmService.confirmDpm(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ZM')")
    public ResponseEntity<DpmRequest> cancelDpm(@PathVariable String id) {
        return ResponseEntity.ok(dpmService.cancelDpm(id));
    }
}

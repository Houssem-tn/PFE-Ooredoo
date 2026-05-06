package com.pfe.stock_management.controller;

import com.pfe.Entity.AppUser;
import com.pfe.Repository.UserRepository;
import com.pfe.stock_management.dto.DeclareTechUsageRequest;
import com.pfe.stock_management.dto.DeclareTechUsageResponse;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.service.SttStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/stock/tech")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TECH')")
public class TechStockController {

    private final SttStockService sttStockService;
    private final UserRepository userRepository;

    @GetMapping("/my-stock")
    public ResponseEntity<TechStock> getMyStock() {
        AppUser tech = currentTech();
        return ResponseEntity.ok(sttStockService.getStockForTech(tech.getId()));
    }

    @PostMapping("/usage")
    public ResponseEntity<DeclareTechUsageResponse> declareUsage(@Valid @RequestBody DeclareTechUsageRequest request) {
        AppUser tech = currentTech();
        return ResponseEntity.ok(sttStockService.declareUsage(tech, request));
    }

    private AppUser currentTech() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}

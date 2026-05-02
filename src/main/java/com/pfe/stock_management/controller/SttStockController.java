package com.pfe.stock_management.controller;

import com.pfe.Entity.AppUser;
import com.pfe.Repository.UserRepository;
import com.pfe.stock_management.dto.DistributeToTechRequest;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.service.DpmService;
import com.pfe.stock_management.service.SttStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/stt")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STT')")
public class SttStockController {

    private final DpmService dpmService;
    private final SttStockService sttStockService;
    private final UserRepository userRepository;

    @GetMapping("/incoming-dpms")
    public ResponseEntity<List<DpmRequest>> getIncomingDpms() {
        AppUser stt = currentStt();
        return ResponseEntity.ok(dpmService.getIncomingDpmsForStt(stt.getId()));
    }

    @GetMapping("/all-dpms")
    public ResponseEntity<List<DpmRequest>> getAllDpms() {
        AppUser stt = currentStt();
        return ResponseEntity.ok(dpmService.getAllDpmsForStt(stt.getId()));
    }

    @GetMapping("/my-stock")
    public ResponseEntity<SttStock> getMyStock() {
        AppUser stt = currentStt();
        return ResponseEntity.ok(sttStockService.getStockForStt(stt.getId()));
    }

    @PostMapping("/distribute")
    public ResponseEntity<SttDistribution> distributeToTech(@Valid @RequestBody DistributeToTechRequest request) {
        return ResponseEntity.ok(sttStockService.distributeToTech(request));
    }

    @GetMapping("/distributions")
    public ResponseEntity<List<SttDistribution>> getDistributionHistory() {
        AppUser stt = currentStt();
        return ResponseEntity.ok(sttStockService.getDistributionHistoryForStt(stt.getId()));
    }

    @GetMapping("/tech-stocks")
    public ResponseEntity<List<TechStock>> getMyTechsStocks() {
        AppUser stt = currentStt();
        return ResponseEntity.ok(sttStockService.getTechStocksForStt(stt.getId()));
    }

    private AppUser currentStt() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}

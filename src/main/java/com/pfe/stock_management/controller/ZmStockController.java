package com.pfe.stock_management.controller;

import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import com.pfe.stock_management.dto.DpmTrackingDto;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.entity.TechStockUsage;
import com.pfe.stock_management.service.DpmService;
import com.pfe.stock_management.service.SttStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/zm")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ZM')")
public class ZmStockController {

    private final DpmService dpmService;
    private final SttStockService sttStockService;
    private final UserRepository userRepository;

    @GetMapping("/my-dpms")
    public ResponseEntity<List<DpmRequest>> getMyDpms() {
        AppUser zm = currentZm();
        return ResponseEntity.ok(dpmService.getDpmsForZm(zm.getId()));
    }

    @GetMapping("/my-dpms/{id}")
    public ResponseEntity<DpmRequest> getDpmById(@PathVariable String id) {
        DpmRequest dpm = dpmService.getById(id);
        AppUser zm = currentZm();
        if (!dpm.getZmId().equals(zm.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your DPM");
        }
        return ResponseEntity.ok(dpm);
    }

    @GetMapping("/my-dpms/{id}/tracking")
    public ResponseEntity<DpmTrackingDto> getDpmTracking(@PathVariable String id) {
        AppUser zm = currentZm();
        DpmTrackingDto tracking = dpmService.getDpmTracking(id);
        if (!tracking.getDpm().getZmId().equals(zm.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your DPM");
        }
        return ResponseEntity.ok(tracking);
    }

    @GetMapping("/my-stts-stocks")
    public ResponseEntity<List<SttStock>> getMySttStocks() {
        AppUser zm = currentZm();
        List<String> sttIds = dpmService.getDpmsForZm(zm.getId()).stream()
                .map(DpmRequest::getSttId)
                .distinct()
                .toList();
        List<SttStock> stocks = sttIds.stream()
                .map(sttStockService::getStockForStt)
                .toList();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/my-techs-stocks")
    public ResponseEntity<List<TechStock>> getMyTechsStocks() {
        AppUser zm = currentZm();
        List<String> sttIds = dpmService.getDpmsForZm(zm.getId()).stream()
                .map(DpmRequest::getSttId)
                .distinct()
                .toList();
        List<TechStock> stocks = sttIds.stream()
                .flatMap(sttId -> sttStockService.getTechStocksForStt(sttId).stream())
                .toList();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/my-techs-usages")
    public ResponseEntity<List<TechStockUsage>> getMyTechsUsages() {
        AppUser zm = currentZm();
        List<String> sttIds = dpmService.getDpmsForZm(zm.getId()).stream()
                .map(DpmRequest::getSttId)
                .distinct()
                .toList();
        return ResponseEntity.ok(sttStockService.getTechUsagesForStts(sttIds));
    }

    private AppUser currentZm() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}

package com.pfe.stock_management.controller;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.UserService;
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
    private final UserService userService;

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
        List<SttStock> stocks = getResponsibleSttIds(zm).stream()
                .map(sttStockService::getStockForStt)
                .toList();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/my-techs-stocks")
    public ResponseEntity<List<TechStock>> getMyTechsStocks() {
        AppUser zm = currentZm();
        List<TechStock> stocks = getResponsibleSttIds(zm).stream()
                .flatMap(sttId -> sttStockService.getTechStocksForStt(sttId).stream())
                .toList();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/my-techs-usages")
    public ResponseEntity<List<TechStockUsage>> getMyTechsUsages() {
        AppUser zm = currentZm();
        return ResponseEntity.ok(sttStockService.getTechUsagesForStts(getResponsibleSttIds(zm)));
    }

    /** All DPMs received by any STT in this ZM's territory (any creator ZM). */
    @GetMapping("/my-stts-dpms")
    public ResponseEntity<List<DpmRequest>> getMySttsDpms() {
        AppUser zm = currentZm();
        List<DpmRequest> dpms = getResponsibleSttIds(zm).stream()
                .flatMap(sttId -> dpmService.getAllDpmsForStt(sttId).stream())
                .toList();
        return ResponseEntity.ok(dpms);
    }

    /**
     * Returns IDs of STTs whose covered governorates overlap with this ZM's territory.
     * Uses the same logic as UserService.findMyStts — the single source of truth.
     */
    private List<String> getResponsibleSttIds(AppUser zm) {
        return userService.findResponsibleSttIds(zm);
    }

    private AppUser currentZm() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}

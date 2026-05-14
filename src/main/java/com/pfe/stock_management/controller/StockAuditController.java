package com.pfe.stock_management.controller;

import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import com.pfe.stock_management.dto.CaseConsumptionDto;
import com.pfe.stock_management.dto.CaseConsumptionSummaryDto;
import com.pfe.stock_management.service.CaseStockConsumptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stock-audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ZM','STT','TECH')")
public class StockAuditController {

    private final CaseStockConsumptionService consumptionService;
    private final UserRepository userRepository;

    @GetMapping("/case-consumptions")
    public ResponseEntity<List<CaseConsumptionDto>> getCaseConsumptions(
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String crmCase,
            @RequestParam(required = false) String zmId,
            @RequestParam(required = false) String sttId,
            @RequestParam(required = false) String technicianId,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        AppUser current = resolveCurrentUser();
        String role = primaryRole(current);

        List<CaseConsumptionDto> result = switch (role) {
            case "ROLE_ADMIN" -> consumptionService.getAll(caseType, crmCase, zmId, sttId, technicianId, equipmentId, from, to);
            case "ROLE_ZM"    -> consumptionService.getForZm(current.getId()).stream()
                    .filter(r -> technicianId == null || technicianId.equals(r.getTechnicianId()))
                    .filter(r -> sttId == null || sttId.equals(r.getSttId()))
                    .filter(r -> caseType == null || (r.getCaseType() != null && r.getCaseType().name().equalsIgnoreCase(caseType)))
                    .filter(r -> equipmentId == null || (r.getConsumedItems() != null && r.getConsumedItems().stream().anyMatch(i -> equipmentId.equals(i.getEquipmentId()))))
                    .toList();
            case "ROLE_STT"   -> consumptionService.getForStt(current.getId()).stream()
                    .filter(r -> technicianId == null || technicianId.equals(r.getTechnicianId()))
                    .filter(r -> caseType == null || (r.getCaseType() != null && r.getCaseType().name().equalsIgnoreCase(caseType)))
                    .filter(r -> equipmentId == null || (r.getConsumedItems() != null && r.getConsumedItems().stream().anyMatch(i -> equipmentId.equals(i.getEquipmentId()))))
                    .toList();
            case "ROLE_TECH"  -> consumptionService.getForTech(current.getId());
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        };

        return ResponseEntity.ok(result);
    }

    @GetMapping("/case-consumptions/by-case/{caseId}")
    public ResponseEntity<CaseConsumptionDto> getByCaseId(@PathVariable String caseId) {
        AppUser current = resolveCurrentUser();
        CaseConsumptionDto dto = consumptionService.getByCaseId(caseId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        assertCanViewConsumption(current, dto);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/case-consumptions/{id}")
    public ResponseEntity<CaseConsumptionDto> getById(@PathVariable String id) {
        AppUser current = resolveCurrentUser();
        CaseConsumptionDto dto = consumptionService.getById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        assertCanViewConsumption(current, dto);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','ZM')")
    public ResponseEntity<CaseConsumptionSummaryDto> getSummary() {
        return ResponseEntity.ok(consumptionService.getSummary());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AppUser resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private String primaryRole(AppUser user) {
        if (user.getRole() == null) return "";
        return user.getRole().name();
    }

    private void assertCanViewConsumption(AppUser current, CaseConsumptionDto dto) {
        String role = primaryRole(current);
        switch (role) {
            case "ROLE_ADMIN" -> { /* allow */ }
            case "ROLE_ZM"    -> {
                if (!current.getId().equals(dto.getZmId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
                }
            }
            case "ROLE_STT"   -> {
                if (!current.getId().equals(dto.getSttId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
                }
            }
            case "ROLE_TECH"  -> {
                if (!current.getId().equals(dto.getTechnicianId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}

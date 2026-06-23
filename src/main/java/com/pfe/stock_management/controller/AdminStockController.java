package com.pfe.stock_management.controller;

import com.pfe.stock_management.dto.DpmTrackingDto;
import com.pfe.stock_management.dto.TeamDistributionDto;
import com.pfe.stock_management.dto.ZmStockAuditDto;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.StockMovement;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.entity.TechStockUsage;
import com.pfe.stock_management.service.DpmService;
import com.pfe.stock_management.service.SttStockService;
import com.pfe.stock_management.service.StockMovementService;
import com.pfe.stock_management.service.TeamDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStockController {

    private final DpmService dpmService;
    private final SttStockService sttStockService;
    private final StockMovementService stockMovementService;
    private final TeamDistributionService teamDistributionService;

    @GetMapping("/dpms")
    public ResponseEntity<List<DpmRequest>> getAllDpms() {
        return ResponseEntity.ok(dpmService.getAllDpms());
    }

    @GetMapping("/dpms/{id}")
    public ResponseEntity<DpmRequest> getDpmById(@PathVariable String id) {
        return ResponseEntity.ok(dpmService.getById(id));
    }

    @GetMapping("/dpms/{id}/tracking")
    public ResponseEntity<DpmTrackingDto> getDpmTracking(@PathVariable String id) {
        return ResponseEntity.ok(dpmService.getDpmTracking(id));
    }

    @GetMapping("/stt-stocks")
    public ResponseEntity<List<SttStock>> getAllSttStocks() {
        return ResponseEntity.ok(sttStockService.getAllSttStocks());
    }

    @GetMapping("/tech-stocks")
    public ResponseEntity<List<TechStock>> getAllTechStocks() {
        return ResponseEntity.ok(sttStockService.getAllTechStocks());
    }

    @GetMapping("/tech-usages")
    public ResponseEntity<List<TechStockUsage>> getAllTechUsages() {
        return ResponseEntity.ok(sttStockService.getAllTechUsages());
    }

    @GetMapping("/movements")
    public ResponseEntity<List<StockMovement>> getAllMovements() {
        return ResponseEntity.ok(stockMovementService.getAll());
    }

    @GetMapping("/movements/reference/{referenceId}")
    public ResponseEntity<List<StockMovement>> getMovementsByReference(@PathVariable String referenceId) {
        return ResponseEntity.ok(stockMovementService.getByReference(referenceId));
    }

    @GetMapping("/distributions")
    public ResponseEntity<List<SttDistribution>> getAllDistributions() {
        return ResponseEntity.ok(sttStockService.getAllDistributions());
    }

    @GetMapping("/audit/zm/{zmId}")
    public ResponseEntity<ZmStockAuditDto> getZmStockAudit(@PathVariable String zmId) {
        return ResponseEntity.ok(dpmService.getZmStockAudit(zmId));
    }

    /**
     * Team-level distribution tracking (all STTs). DPM-source-independent: includes
     * new pooled distributions whose legacy {@code dpmId} is null. Optional filters:
     * zmId, sttId, techId, equipmentId, from/to (on distribution date), status.
     */
    @GetMapping("/team-distributions")
    public ResponseEntity<List<TeamDistributionDto>> getTeamDistributions(
            @RequestParam(required = false) String zmId,
            @RequestParam(required = false) String sttId,
            @RequestParam(required = false) String techId,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(teamDistributionService.getTeamDistributions(
                null,
                new TeamDistributionService.TeamDistributionFilter(
                        zmId, sttId, techId, equipmentId, from, to, status)));
    }
}

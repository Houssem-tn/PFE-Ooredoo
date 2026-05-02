package com.pfe.stock_management.controller;

import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.StockMovement;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.service.DpmService;
import com.pfe.stock_management.service.SttStockService;
import com.pfe.stock_management.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStockController {

    private final DpmService dpmService;
    private final SttStockService sttStockService;
    private final StockMovementService stockMovementService;

    @GetMapping("/dpms")
    public ResponseEntity<List<DpmRequest>> getAllDpms() {
        return ResponseEntity.ok(dpmService.getAllDpms());
    }

    @GetMapping("/dpms/{id}")
    public ResponseEntity<DpmRequest> getDpmById(@PathVariable String id) {
        return ResponseEntity.ok(dpmService.getById(id));
    }

    @GetMapping("/stt-stocks")
    public ResponseEntity<List<SttStock>> getAllSttStocks() {
        return ResponseEntity.ok(sttStockService.getAllSttStocks());
    }

    @GetMapping("/tech-stocks")
    public ResponseEntity<List<TechStock>> getAllTechStocks() {
        return ResponseEntity.ok(sttStockService.getAllTechStocks());
    }

    @GetMapping("/movements")
    public ResponseEntity<List<StockMovement>> getAllMovements() {
        return ResponseEntity.ok(stockMovementService.getAll());
    }

    @GetMapping("/movements/reference/{referenceId}")
    public ResponseEntity<List<StockMovement>> getMovementsByReference(@PathVariable String referenceId) {
        return ResponseEntity.ok(stockMovementService.getByReference(referenceId));
    }
}

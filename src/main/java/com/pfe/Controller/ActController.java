package com.pfe.Controller;

import com.pfe.ActDto.CreateActFromCrmRequest;
import com.pfe.Entity.ActIntervention;
import com.pfe.Entity.ActStatus;
import com.pfe.Repository.ActRepository;
import com.pfe.Service.ActService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.pfe.ActDto.*;

import java.util.List;

@RestController
@RequestMapping("/act")
public class ActController {

    private final ActService service;
    private final ActRepository repo;

    public ActController(ActService service, ActRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    // CREATE from CRM
    @PostMapping
    public ResponseEntity<ActIntervention> create(@RequestBody CreateActFromCrmRequest req) {
        return ResponseEntity.ok(service.createFromCrm(req));
    }

    // Assign STT
    @PostMapping("/{id}/assign-stt")
    public ResponseEntity<ActIntervention> assignStt(@PathVariable String id, @RequestBody AssignSttRequest req) {
        return ResponseEntity.ok(service.assignToStt(id, req));
    }

    // RDV
    @PostMapping("/{id}/rdv")
    public ResponseEntity<ActIntervention> setRdv(@PathVariable String id, @RequestBody SetRdvRequest req) {
        return ResponseEntity.ok(service.setRdv(id, req));
    }

    // Assign technician
    @PostMapping("/{id}/assign-tech")
    public ResponseEntity<ActIntervention> assignTech(@PathVariable String id, @RequestBody AssignTechnicianRequest req) {
        return ResponseEntity.ok(service.assignTechnician(id, req));
    }

    @Operation(summary = "Mark ACT as IN_EXECUTION (first evidence received)")
    @PutMapping("/{actId}/in-execution")
    public ResponseEntity<ActIntervention> markInExecution(@PathVariable String actId) {
        return ResponseEntity.ok(service.markInExecution(actId));
    }
    // Submit (tech finished evidence)
    @PostMapping("/{id}/submit")
    public ResponseEntity<ActIntervention> submit(@PathVariable String id) {
        return ResponseEntity.ok(service.submit(id));
    }

    // STT pre-validate
    @PostMapping("/{id}/stt-prevalidate")
    public ResponseEntity<ActIntervention> sttPrevalidate(@PathVariable String id, @RequestBody SttPrevalidateRequest req) {
        return ResponseEntity.ok(service.sttPrevalidate(id, req));
    }

    // ZM validate
    @PostMapping("/{id}/zm-validate")
    public ResponseEntity<ActIntervention> zmValidate(@PathVariable String id, @RequestBody ZmValidateRequest req) {
        return ResponseEntity.ok(service.zmValidate(id, req));
    }

    // Metrics update (pipeline/admin)
    @PatchMapping("/{id}/metrics")
    public ResponseEntity<ActIntervention> updateMetrics(@PathVariable String id, @RequestBody UpdateMetricsRequest req) {
        return ResponseEntity.ok(service.updateMetrics(id, req));
    }

    // Queries
    @GetMapping("/{id}")
    public ResponseEntity<ActIntervention> getOne(@PathVariable String id) {
        return ResponseEntity.ok(repo.findById(id).orElseThrow());
    }

    @GetMapping
    public ResponseEntity<List<ActIntervention>> list(@RequestParam(required = false) ActStatus statut) {
        if (statut != null) return ResponseEntity.ok(repo.findByStatut(statut));
        return ResponseEntity.ok(repo.findAll());
    }
}

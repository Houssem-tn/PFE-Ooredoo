package com.pfe.stock_management.controller;

import com.pfe.Entity.AppUser;
import com.pfe.Repository.UserRepository;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.service.DpmService;
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

    private AppUser currentZm() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}

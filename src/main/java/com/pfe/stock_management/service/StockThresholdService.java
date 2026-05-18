package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.stock_management.dto.StockThresholdRequest;
import com.pfe.stock_management.dto.StockThresholdResponse;
import com.pfe.stock_management.entity.StockThreshold;
import com.pfe.stock_management.repository.StockThresholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockThresholdService {

    private final StockThresholdRepository thresholdRepository;
    private final UserRepository userRepository;

    public List<StockThresholdResponse> getMyThresholds() {
        AppUser zm = currentZm();
        return thresholdRepository.findByZmId(zm.getId())
                .stream()
                .map(StockThresholdResponse::from)
                .collect(Collectors.toList());
    }

    public List<StockThresholdResponse> getEffectiveThresholds(String zmId) {
        return thresholdRepository.findByZmIdAndEnabledTrue(zmId)
                .stream()
                .map(StockThresholdResponse::from)
                .collect(Collectors.toList());
    }

    public StockThresholdResponse createOrUpdate(StockThresholdRequest request) {
        AppUser zm = currentZm();
        requireCanCreateDpm(zm);
        validateThresholdValues(request);

        StockThreshold threshold = thresholdRepository
                .findByZmIdAndItemId(zm.getId(), request.getItemId())
                .orElseGet(() -> StockThreshold.builder()
                        .zmId(zm.getId())
                        .zmName(zm.getFullName())
                        .itemId(request.getItemId())
                        .createdAt(Instant.now())
                        .build());

        applyRequest(threshold, request, zm);
        return StockThresholdResponse.from(thresholdRepository.save(threshold));
    }

    public StockThresholdResponse update(String id, StockThresholdRequest request) {
        AppUser zm = currentZm();
        requireCanCreateDpm(zm);
        validateThresholdValues(request);

        StockThreshold threshold = thresholdRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Threshold not found"));

        if (!zm.getId().equals(threshold.getZmId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own thresholds");
        }

        applyRequest(threshold, request, zm);
        return StockThresholdResponse.from(thresholdRepository.save(threshold));
    }

    public StockThresholdResponse toggle(String id) {
        AppUser zm = currentZm();
        requireCanCreateDpm(zm);

        StockThreshold threshold = thresholdRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Threshold not found"));

        if (!zm.getId().equals(threshold.getZmId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only modify your own thresholds");
        }

        threshold.setEnabled(!threshold.isEnabled());
        threshold.setUpdatedAt(Instant.now());
        threshold.setUpdatedBy(zm.getEmail());
        return StockThresholdResponse.from(thresholdRepository.save(threshold));
    }

    private void applyRequest(StockThreshold threshold, StockThresholdRequest request, AppUser zm) {
        threshold.setItemName(request.getItemName());
        threshold.setItemType(request.getItemType());
        threshold.setWarningThreshold(request.getWarningThreshold());
        threshold.setCriticalThreshold(request.getCriticalThreshold());
        threshold.setUnit(request.getUnit());
        threshold.setEnabled(request.isEnabled());
        threshold.setUpdatedAt(Instant.now());
        threshold.setUpdatedBy(zm.getEmail());
    }

    private void validateThresholdValues(StockThresholdRequest req) {
        if (req.getWarningThreshold() < req.getCriticalThreshold()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Warning threshold must be >= critical threshold");
        }
    }

    private void requireCanCreateDpm(AppUser zm) {
        if (!Boolean.TRUE.equals(zm.isCanCreateDpm())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to manage stock thresholds. Only DPM-authorized ZMs can configure thresholds.");
        }
    }

    private AppUser currentZm() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}

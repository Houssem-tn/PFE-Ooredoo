package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.stock_management.dto.CaseLimitResponse;
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
    private final CaseRepository caseRepository;

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

    /**
     * Returns all configured case usage limits for the ZM responsible for the given case.
     * Resolves the chain: caseId → techId → STT → ZM.
     * Called by TECH/STT role to feed proactive frontend limit detection.
     */
    public List<CaseLimitResponse> getEffectiveCaseLimits(String caseId) {
        CaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));

        String techId = caseEntity.getTechId();
        if (techId == null) return List.of();

        AppUser tech = userRepository.findById(techId).orElse(null);
        if (tech == null || tech.getAssignedSttId() == null) return List.of();

        AppUser stt = userRepository.findById(tech.getAssignedSttId()).orElse(null);
        if (stt == null || stt.getAssignedZmId() == null) return List.of();

        return thresholdRepository.findByZmId(stt.getAssignedZmId()).stream()
                .filter(t -> t.getCaseUsageLimit() != null)
                .map(CaseLimitResponse::from)
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
        threshold.setUnit(request.getUnit());

        // STT-specific thresholds
        threshold.setSttWarningThreshold(request.getSttWarningThreshold());
        threshold.setSttCriticalThreshold(request.getSttCriticalThreshold());
        threshold.setSttAlertsEnabled(request.getSttAlertsEnabled());

        // TECH-specific thresholds
        threshold.setTechWarningThreshold(request.getTechWarningThreshold());
        threshold.setTechCriticalThreshold(request.getTechCriticalThreshold());
        threshold.setTechAlertsEnabled(request.getTechAlertsEnabled());

        // Legacy fields — only overwrite if explicitly sent by client
        if (request.getWarningThreshold() != null) {
            threshold.setWarningThreshold(request.getWarningThreshold());
        }
        if (request.getCriticalThreshold() != null) {
            threshold.setCriticalThreshold(request.getCriticalThreshold());
        }

        // Master enabled = true when either alert channel is active
        boolean sttOn  = Boolean.TRUE.equals(request.getSttAlertsEnabled());
        boolean techOn = Boolean.TRUE.equals(request.getTechAlertsEnabled());
        if (request.getSttAlertsEnabled() == null && request.getTechAlertsEnabled() == null) {
            threshold.setEnabled(request.isEnabled());
        } else {
            threshold.setEnabled(sttOn || techOn);
        }

        threshold.setCaseUsageLimitEnabled(request.isCaseUsageLimitEnabled());
        threshold.setCaseUsageLimit(request.getCaseUsageLimit());
        threshold.setUpdatedAt(Instant.now());
        threshold.setUpdatedBy(zm.getEmail());
    }

    private void validateThresholdValues(StockThresholdRequest req) {
        // STT thresholds
        if (req.getSttWarningThreshold() != null && req.getSttCriticalThreshold() != null
                && req.getSttWarningThreshold() < req.getSttCriticalThreshold()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "STT warning threshold must be >= STT critical threshold");
        }
        // TECH thresholds
        if (req.getTechWarningThreshold() != null && req.getTechCriticalThreshold() != null
                && req.getTechWarningThreshold() < req.getTechCriticalThreshold()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TECH warning threshold must be >= TECH critical threshold");
        }
        // Legacy thresholds (old clients)
        if (req.getWarningThreshold() != null && req.getCriticalThreshold() != null
                && req.getWarningThreshold() < req.getCriticalThreshold()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Warning threshold must be >= critical threshold");
        }
        if (req.isCaseUsageLimitEnabled() && req.getCaseUsageLimit() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "caseUsageLimit is required when caseUsageLimitEnabled is true");
        }
        if (req.getCaseUsageLimit() != null && req.getCaseUsageLimit() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "caseUsageLimit must be >= 0");
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

package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Dto.AssignGovernoratesToZmRequest;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.CaseModuleType;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.support.TunisiaGovernorateSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminAssignmentService {
    private final UserRepository userRepository;

    public AdminAssignmentService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser assignGovernoratesToZm(AssignGovernoratesToZmRequest req) {
        String zmId = req.zmId().trim();

        AppUser zm = userRepository.findById(zmId)
                .orElseThrow(() -> new IllegalArgumentException("ZM not found"));

        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new IllegalStateException("Selected user is not a ZM");
        }

        List<String> normalizedGovernorates = normalizeGovernoratesAllowEmpty(req.assignedGovernorates());
        ensureGovernoratesAreAvailable(zmId, normalizedGovernorates);

        zm.setAssignedGovernorates(normalizedGovernorates);
        return userRepository.save(zm);
    }

    public AppUser assignCoverageByModule(String zmId, CaseModuleType moduleType, List<String> governorates) {
        AppUser zm = userRepository.findById(zmId.trim())
                .orElseThrow(() -> new IllegalArgumentException("ZM not found"));

        if (zm.getRole() != UserRole.ROLE_ZM) {
            throw new IllegalStateException("Selected user is not a ZM");
        }

        List<String> normalized = normalizeGovernoratesAllowEmpty(governorates);
        ensureModuleGovernoratesAreAvailable(zmId, moduleType, normalized);

        if (zm.getCoverageByModule() == null) {
            zm.setCoverageByModule(new HashMap<>());
        }
        zm.getCoverageByModule().put(moduleType.name(), normalized);
        return userRepository.save(zm);
    }

    public AppUser assignCoverageAreasToStt(String sttId, List<String> coveredGovernorates) {
        AppUser stt = userRepository.findById(sttId.trim())
                .orElseThrow(() -> new IllegalArgumentException("STT not found"));

        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new IllegalStateException("Selected user is not an STT");
        }

        List<String> normalizedGovernorates = normalizeGovernoratesRequireSelection(coveredGovernorates);
        stt.setCoveredGovernorates(normalizedGovernorates);
        stt.setRequestedGovernorates(
                safeGovernorates(stt.getRequestedGovernorates()).stream()
                        .filter(governorate -> !normalizedGovernorates.contains(governorate))
                        .toList()
        );
        return userRepository.save(stt);
    }

    private void ensureModuleGovernoratesAreAvailable(String currentZmId, CaseModuleType moduleType, Collection<String> governorates) {
        if (governorates.isEmpty()) {
            return;
        }

        String moduleKey = moduleType.name();
        Map<String, String> conflicts = new LinkedHashMap<>();

        for (AppUser otherZm : userRepository.findByRoleOrderByFullNameAsc(UserRole.ROLE_ZM)) {
            if (otherZm.getId() == null || otherZm.getId().equals(currentZmId)) {
                continue;
            }

            List<String> otherGovs;
            Map<String, List<String>> otherCoverage = otherZm.getCoverageByModule();
            if (otherCoverage != null && otherCoverage.containsKey(moduleKey)
                    && otherCoverage.get(moduleKey) != null && !otherCoverage.get(moduleKey).isEmpty()) {
                otherGovs = safeGovernorates(otherCoverage.get(moduleKey));
            } else if (CaseModuleType.ACT == moduleType) {
                otherGovs = safeGovernorates(otherZm.getAssignedGovernorates());
            } else {
                continue;
            }

            for (String governorate : safeGovernorates(new ArrayList<>(governorates))) {
                if (TunisiaGovernorateSupport.containsEquivalent(otherGovs, governorate)) {
                    conflicts.putIfAbsent(governorate, displayName(otherZm));
                }
            }
        }

        if (!conflicts.isEmpty()) {
            String conflictSummary = conflicts.entrySet().stream()
                    .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Governorates already assigned to another ZM for module " + moduleKey + ": " + conflictSummary
            );
        }
    }

    private List<String> normalizeGovernoratesAllowEmpty(List<String> governorates) {
        if (governorates == null || governorates.isEmpty()) {
            return List.of();
        }

        return TunisiaGovernorateSupport.normalizeSelection(governorates);
    }

    private List<String> normalizeGovernoratesRequireSelection(List<String> governorates) {
        if (governorates == null || governorates.isEmpty()) {
            throw new IllegalArgumentException("At least one governorate must be selected");
        }

        return TunisiaGovernorateSupport.normalizeSelection(governorates);
    }

    private void ensureGovernoratesAreAvailable(String currentZmId, Collection<String> governorates) {
        if (governorates.isEmpty()) {
            return;
        }

        Map<String, String> conflicts = new LinkedHashMap<>();

        for (AppUser otherZm : userRepository.findByRoleOrderByFullNameAsc(UserRole.ROLE_ZM)) {
            if (otherZm.getId() == null || otherZm.getId().equals(currentZmId)) {
                continue;
            }

            for (String governorate : safeGovernorates(otherZm.getAssignedGovernorates())) {
                if (TunisiaGovernorateSupport.containsEquivalent(governorates, governorate)) {
                    conflicts.putIfAbsent(governorate, displayName(otherZm));
                }
            }
        }

        if (!conflicts.isEmpty()) {
            String conflictSummary = conflicts.entrySet().stream()
                    .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                    .collect(Collectors.joining(", "));

            throw new IllegalStateException("Governorates already assigned to another ZM: " + conflictSummary);
        }
    }

    private List<String> safeGovernorates(List<String> governorates) {
        return TunisiaGovernorateSupport.normalizeStoredValues(governorates);
    }

    private String displayName(AppUser user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim().toLowerCase();
        }

        return "unknown ZM";
    }
}

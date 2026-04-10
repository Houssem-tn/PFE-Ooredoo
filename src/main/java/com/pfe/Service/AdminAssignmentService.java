package com.pfe.Service;

import com.pfe.Dto.AssignGovernoratesToZmRequest;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminAssignmentService {
    private static final List<String> TUNISIA_GOVERNORATES = List.of(
            "Tunis",
            "Ariana",
            "Ben Arous",
            "Mannouba",
            "Nabeul",
            "Zaghouan",
            "Bizerte",
            "B\u00E9ja",
            "Jendouba",
            "Le Kef",
            "Siliana",
            "Sousse",
            "Monastir",
            "Mahdia",
            "Sfax",
            "Kairouan",
            "Kasserine",
            "Sidi Bouzid",
            "Gab\u00E8s",
            "M\u00E9denine",
            "Tataouine",
            "Gafsa",
            "Tozeur",
            "K\u00E9bili"
    );
    private static final Set<String> TUNISIA_GOVERNORATE_SET = Set.copyOf(TUNISIA_GOVERNORATES);

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

        List<String> normalizedGovernorates = normalizeGovernorates(req.assignedGovernorates());
        ensureGovernoratesAreAvailable(zmId, normalizedGovernorates);

        zm.setAssignedGovernorates(normalizedGovernorates);
        return userRepository.save(zm);
    }

    public AppUser assignCoverageAreasToStt(String sttId, List<String> coveredGovernorates) {
        AppUser stt = userRepository.findById(sttId.trim())
                .orElseThrow(() -> new IllegalArgumentException("STT not found"));

        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new IllegalStateException("Selected user is not an STT");
        }

        List<String> normalizedGovernorates = normalizeGovernorates(coveredGovernorates);
        stt.setCoveredGovernorates(normalizedGovernorates);
        stt.setRequestedGovernorates(
                safeGovernorates(stt.getRequestedGovernorates()).stream()
                        .filter(governorate -> !normalizedGovernorates.contains(governorate))
                        .toList()
        );
        return userRepository.save(stt);
    }

    private List<String> normalizeGovernorates(List<String> governorates) {
        if (governorates == null || governorates.isEmpty()) {
            throw new IllegalArgumentException("At least one governorate must be selected");
        }

        LinkedHashSet<String> requestedGovernorates = new LinkedHashSet<>();
        for (String governorate : governorates) {
            if (governorate == null || governorate.isBlank()) {
                throw new IllegalArgumentException("Governorate names cannot be blank");
            }
            requestedGovernorates.add(governorate.trim());
        }

        List<String> invalidGovernorates = requestedGovernorates.stream()
                .filter(governorate -> !TUNISIA_GOVERNORATE_SET.contains(governorate))
                .toList();

        if (!invalidGovernorates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsupported governorates: " + String.join(", ", invalidGovernorates)
            );
        }

        return TUNISIA_GOVERNORATES.stream()
                .filter(requestedGovernorates::contains)
                .toList();
    }

    private void ensureGovernoratesAreAvailable(String currentZmId, Collection<String> governorates) {
        if (governorates.isEmpty()) {
            return;
        }

        Map<String, String> conflicts = new LinkedHashMap<>();

        for (AppUser otherZm : userRepository.findByRoleAndAssignedGovernoratesIn(UserRole.ROLE_ZM, governorates)) {
            if (otherZm.getId() == null || otherZm.getId().equals(currentZmId)) {
                continue;
            }

            for (String governorate : safeGovernorates(otherZm.getAssignedGovernorates())) {
                if (governorates.contains(governorate)) {
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
        return governorates == null ? List.of() : governorates;
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

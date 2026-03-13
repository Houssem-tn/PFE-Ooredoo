package com.pfe.Service;

import com.pfe.Dto.TechnicianListItemDTO;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SttTechnicianServiceImpl implements SttTechnicianService {

    private final UserRepository userRepository;

    public SttTechnicianServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<TechnicianListItemDTO> getMyTechnicians(String sttEmail) {
        AppUser stt = userRepository.findByEmail(sttEmail)
                .orElseThrow(() -> new RuntimeException("Connected STT not found"));

        if (stt.getRole() != UserRole.ROLE_STT) {
            throw new AccessDeniedException("Only STT can access their technicians");
        }

        return userRepository
                .findByRoleAndAssignedSttIdOrderByCreatedAtDesc(UserRole.ROLE_TECH, stt.getId())
                .stream()
                .map(tech -> new TechnicianListItemDTO(
                        tech.getId(),
                        tech.getFullName(),
                        tech.getEmail(),
                        tech.getPhone(),
                        tech.getStatus(),
                        tech.isEnabled(),
                        tech.getCreatedAt()
                ))
                .toList();
    }
}
package com.pfe.Service;

import com.pfe.Dto.AssignSttToZmRequest;
import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminAssignmentService {

    private final UserRepository userRepository;

    public AdminAssignmentService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser assignSttToZm(AssignSttToZmRequest req) {
        String sttId = req.sttId().trim();
        String zmId = req.zmId().trim();

        AppUser stt = userRepository.findById(sttId)
                .orElseThrow(() -> new IllegalArgumentException("STT not found"));

        if (stt.getRole() != UserRole.ROLE_STT || stt.getStatus() != AccountStatus.APPROVED || !stt.isEnabled()) {
            throw new IllegalStateException("Selected user is not an active STT");
        }

        AppUser zm = userRepository.findById(zmId)
                .orElseThrow(() -> new IllegalArgumentException("ZM not found"));

        if (zm.getRole() != UserRole.ROLE_ZM || zm.getStatus() != AccountStatus.APPROVED || !zm.isEnabled()) {
            throw new IllegalStateException("Selected user is not an active ZM");
        }

        stt.setAssignedZmId(zm.getId());
        stt.setAssignedZmEmail(zm.getEmail());

        return userRepository.save(stt);
    }
}
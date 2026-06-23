package com.pfe.MainPackage.Controller;

import com.pfe.MainPackage.Dto.TechnicianListItemDTO;
import com.pfe.MainPackage.Service.SttTechnicianService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stt")
@PreAuthorize("hasAuthority('ROLE_STT')")
public class SttTechnicianController {

    private final SttTechnicianService sttTechnicianService;

    public SttTechnicianController(SttTechnicianService sttTechnicianService) {
        this.sttTechnicianService = sttTechnicianService;
    }

    @GetMapping("/my-technicians")
    public List<TechnicianListItemDTO> myTechnicians(Authentication auth) {
        return sttTechnicianService.getMyTechnicians(auth.getName());
    }
}
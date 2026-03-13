package com.pfe.Service;

import com.pfe.Dto.TechnicianListItemDTO;

import java.util.List;

public interface SttTechnicianService {
    List<TechnicianListItemDTO> getMyTechnicians(String sttEmail);
}
package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Dto.TechnicianListItemDTO;

import java.util.List;

public interface SttTechnicianService {
    List<TechnicianListItemDTO> getMyTechnicians(String sttEmail);
}
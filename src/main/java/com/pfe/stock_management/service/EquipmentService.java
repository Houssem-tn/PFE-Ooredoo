package com.pfe.stock_management.service;

import com.pfe.Entity.FileEntityType;
import com.pfe.Entity.FileRole;
import com.pfe.Service.FileStorageService;
import com.pfe.stock_management.dto.CreateEquipmentRequest;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final FileStorageService fileStorageService;

    public List<Equipment> getAllActive() {
        List<Equipment> items = equipmentRepository.findByActiveTrue();
        items.forEach(this::enrichImageUrl);
        return items;
    }

    public List<Equipment> getAll() {
        List<Equipment> items = equipmentRepository.findAll();
        items.forEach(this::enrichImageUrl);
        return items;
    }

    public Equipment getById(String id) {
        Equipment eq = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found: " + id));
        enrichImageUrl(eq);
        return eq;
    }

    public Equipment create(CreateEquipmentRequest request, MultipartFile image) {
        String caller = currentUser();

        Equipment eq = Equipment.builder()
                .name(request.getName())
                .description(request.getDescription())
                .unit(request.getUnit())
                .category(request.getCategory())
                .createdBy(caller)
                .active(true)
                .build();

        if (image != null && !image.isEmpty()) {
            eq = equipmentRepository.save(eq);
            try {
                var meta = fileStorageService.upload(
                        image,
                        FileEntityType.STOCK_EQUIPMENT,
                        eq.getId(),
                        FileRole.EQUIPMENT_IMAGE,
                        caller,
                        null, null, null, null, null
                );
                eq.setImageFileId(meta.getId());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload failed: " + e.getMessage());
            }
        }

        Equipment saved = equipmentRepository.save(eq);
        enrichImageUrl(saved);
        return saved;
    }

    public Equipment update(String id, CreateEquipmentRequest request, MultipartFile image) {
        Equipment eq = getById(id);
        eq.setName(request.getName());
        eq.setDescription(request.getDescription());
        eq.setUnit(request.getUnit());
        eq.setCategory(request.getCategory());

        if (image != null && !image.isEmpty()) {
            String caller = currentUser();
            try {
                var meta = fileStorageService.upload(
                        image,
                        FileEntityType.STOCK_EQUIPMENT,
                        id,
                        FileRole.EQUIPMENT_IMAGE,
                        caller,
                        null, null, null, null, null
                );
                eq.setImageFileId(meta.getId());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload failed: " + e.getMessage());
            }
        }

        Equipment saved = equipmentRepository.save(eq);
        enrichImageUrl(saved);
        return saved;
    }

    public void deactivate(String id) {
        Equipment eq = getById(id);
        eq.setActive(false);
        equipmentRepository.save(eq);
    }

    private void enrichImageUrl(Equipment eq) {
        if (eq.getImageFileId() != null && !eq.getImageFileId().isBlank()) {
            eq.setImageUrl(fileStorageService.buildViewUrl(eq.getImageFileId()));
        }
    }

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "system";
    }
}

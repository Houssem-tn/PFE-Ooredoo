package com.pfe.Controller;

import com.pfe.Dto.AssignGovernoratesToZmRequest;
import com.pfe.Dto.UserDTO;
import com.pfe.Entity.AppUser;
import com.pfe.Service.AdminAssignmentService;
import com.pfe.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminAssignmentController {

    public record AssignCoverageAreasToSttRequest(
            @NotBlank(message = "sttId is required")
            String sttId,
            @NotEmpty(message = "coveredGovernorates is required")
            List<@NotBlank(message = "Governorate names cannot be blank") String> coveredGovernorates
    ) {
    }

    private final AdminAssignmentService adminAssignmentService;
    private final UserMapper userMapper;

    public AdminAssignmentController(AdminAssignmentService adminAssignmentService,
                                     UserMapper userMapper) {
        this.adminAssignmentService = adminAssignmentService;
        this.userMapper = userMapper;
    }

    @PutMapping("/assign-governorates-to-zm")
    public ResponseEntity<UserDTO> assignGovernoratesToZm(@Valid @RequestBody AssignGovernoratesToZmRequest req) {
        AppUser updated = adminAssignmentService.assignGovernoratesToZm(req);
        return ResponseEntity.ok(userMapper.toDto(updated));
    }

    @PutMapping("/assign-coverage-areas-to-stt")
    public ResponseEntity<UserDTO> assignCoverageAreasToStt(@Valid @RequestBody AssignCoverageAreasToSttRequest req) {
        AppUser updated = adminAssignmentService.assignCoverageAreasToStt(req.sttId(), req.coveredGovernorates());
        return ResponseEntity.ok(userMapper.toDto(updated));
    }
}

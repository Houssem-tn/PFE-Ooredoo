package com.pfe.Controller;

import com.pfe.Dto.AssignSttToZmRequest;
import com.pfe.Dto.UserDTO;
import com.pfe.Entity.AppUser;
import com.pfe.Service.AdminAssignmentService;
import com.pfe.mapper.UserMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAssignmentController {

    private final AdminAssignmentService adminAssignmentService;
    private final UserMapper userMapper;

    public AdminAssignmentController(AdminAssignmentService adminAssignmentService,
                                     UserMapper userMapper) {
        this.adminAssignmentService = adminAssignmentService;
        this.userMapper = userMapper;
    }

    @PutMapping("/assign-stt-to-zm")
    public ResponseEntity<UserDTO> assignSttToZm(@Valid @RequestBody AssignSttToZmRequest req) {
        AppUser updated = adminAssignmentService.assignSttToZm(req);
        return ResponseEntity.ok(userMapper.toDto(updated));
    }
}
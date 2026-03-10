package com.pfe.Controller;

import com.pfe.Dto.RejectRequest;
import com.pfe.Dto.UserDTO;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.mapper.UserMapper;
import com.pfe.Service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final UserMapper userMapper;

    public ApprovalController(ApprovalService approvalService, UserMapper userMapper) {
        this.approvalService = approvalService;
        this.userMapper = userMapper;
    }

    /**
     * ✅ Recommended list:
     * ADMIN -> كل pending
     * ZM -> pending STT
     * STT -> pending TECH
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ZM','ROLE_STT')")
    public List<UserDTO> pending(Authentication auth) {
        UserRole approverRole = extractRole(auth);
        List<AppUser> users = approvalService.listMyPending(approverRole);
        return users.stream().map(userMapper::toDto).toList();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ZM','ROLE_STT')")
    public UserDTO approve(@PathVariable String id, Authentication auth) {
        UserRole approverRole = extractRole(auth);
        AppUser approved = approvalService.approve(id, auth.getName(), approverRole);
        return userMapper.toDto(approved);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ZM','ROLE_STT')")
    public UserDTO reject(@PathVariable String id,
                          @Valid @RequestBody RejectRequest body,
                          Authentication auth) {
        UserRole approverRole = extractRole(auth);
        AppUser rejected = approvalService.reject(id, auth.getName(), approverRole, body.reason());
        return userMapper.toDto(rejected);
    }

    /**
     * Works with your current UserDetailsServiceImpl:
     * authority string == "ROLE_ADMIN"/"ROLE_ZM"/...
     */
    private UserRole extractRole(Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority();
        return UserRole.valueOf(role);
    }
}
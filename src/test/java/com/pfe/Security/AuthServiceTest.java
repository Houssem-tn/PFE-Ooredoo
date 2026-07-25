package com.pfe.Security;

import com.pfe.MainPackage.Dto.LoginRequest;
import com.pfe.MainPackage.Dto.RegisterRequest;
import com.pfe.MainPackage.Dto.UserDTO;
import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.TechInvitationRepository;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.AuthService;
import com.pfe.MainPackage.Service.FileStorageService;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.MainPackage.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private AuthenticationManager authManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private NotificationEventService notificationEventService;
    @Mock
    private TechInvitationRepository invitationRepo;
    @Mock
    private FileStorageService fileStorageService;

    private AuthService newService() {
        return new AuthService(
                userRepository, encoder, authManager, jwtService,
                userMapper, notificationEventService, invitationRepo, fileStorageService
        );
    }

    private AppUser approvedUser() {
        return AppUser.builder()
                .id("user-1")
                .email("stt@example.com")
                .password("hashed")
                .role(UserRole.ROLE_STT)
                .status(AccountStatus.APPROVED)
                .enabled(true)
                .build();
    }

    private UserDTO dummyDto(String id, String email, UserRole role) {
        return new UserDTO(
                id, "Name", email, null,
                role, null,
                AccountStatus.APPROVED, true, false, false,
                null, null, null,
                null, null, null,
                null, null,
                null, null,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of(),
                null, null, null
        );
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    void login_shouldReturnTokenAndUserOnValidCredentials() {
        AuthService service = newService();
        AppUser user = approvedUser();

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        // Login now embeds the account's live tokenVersion (0 for a fresh user)
        // in the JWT so a later password reset can invalidate old tokens.
        when(jwtService.generateToken("stt@example.com", UserRole.ROLE_STT, 0L)).thenReturn("jwt-token");
        when(userMapper.toDto(any(AppUser.class))).thenReturn(
                dummyDto("user-1", "stt@example.com", UserRole.ROLE_STT)
        );

        var response = service.login(new LoginRequest("stt@example.com", "correct-password"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.role()).isEqualTo(UserRole.ROLE_STT);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_shouldRejectUnknownEmailAsInvalidCredentials() {
        AuthService service = newService();
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");

        verify(authManager, never()).authenticate(any());
    }

    @Test
    void login_shouldRejectPendingApprovalAccount() {
        AuthService service = newService();
        AppUser pending = approvedUser();
        pending.setStatus(AccountStatus.PENDING);
        when(userRepository.findByEmail(pending.getEmail())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.login(new LoginRequest(pending.getEmail(), "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account pending approval");

        verify(authManager, never()).authenticate(any());
    }

    @Test
    void login_shouldRejectDisabledBannedAccount() {
        AuthService service = newService();
        AppUser banned = approvedUser();
        banned.setEnabled(false);
        when(userRepository.findByEmail(banned.getEmail())).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> service.login(new LoginRequest(banned.getEmail(), "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unfortunately you are banned! Contact admin");
    }

    @Test
    void login_shouldTranslateBadCredentialsExceptionToInvalidCredentials() {
        AuthService service = newService();
        AppUser user = approvedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login(new LoginRequest(user.getEmail(), "wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_shouldTranslateDisabledExceptionToBannedMessage() {
        AuthService service = newService();
        AppUser user = approvedUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(authManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> service.login(new LoginRequest(user.getEmail(), "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unfortunately you are banned! Contact admin");
    }

    @Test
    void login_shouldRejectAccountWithNoRoleAssignedYet() {
        AuthService service = newService();
        AppUser user = approvedUser();
        user.setRole(null);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest(user.getEmail(), "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account role not assigned");

        verify(jwtService, never()).generateToken(anyString(), any());
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    void register_shouldRejectDuplicateEmail() {
        AuthService service = newService();
        when(userRepository.existsByEmail("stt@example.com")).thenReturn(true);

        RegisterRequest req = new RegisterRequest(
                "Acme STT", null, null, "stt@example.com", "password1", UserRole.ROLE_STT, null, null
        );

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already used");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldRejectRoleNotAllowedForPublicRegistration() {
        AuthService service = newService();
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);

        RegisterRequest req = new RegisterRequest(
                "Someone", "First", "Last", "admin@example.com", "password1", UserRole.ROLE_ADMIN, null, null
        );

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role not allowed for public registration");
    }

    @Test
    void register_shouldCreatePendingUnapprovedUserForAllowedRole() {
        AuthService service = newService();
        when(userRepository.existsByEmail("newstt@example.com")).thenReturn(false);
        when(encoder.encode("password1")).thenReturn("hashed-password");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(AppUser.class))).thenReturn(
                dummyDto("id", "newstt@example.com", null)
        );

        RegisterRequest req = new RegisterRequest(
                "Acme STT", null, null, "newstt@example.com", "password1", UserRole.ROLE_STT, null, null
        );

        service.register(req);

        var captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        AppUser saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.getRole()).isNull();
        assertThat(saved.getRoleRequested()).isEqualTo(UserRole.ROLE_STT);
        verify(notificationEventService).onUserRegistered(saved);
    }
}

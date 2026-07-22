package com.forsalaw.userManagement.service;

import com.forsalaw.security.JwtService;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.model.AuthResponse;
import com.forsalaw.userManagement.model.LoginRequest;
import com.forsalaw.userManagement.model.RegisterRequest;
import com.forsalaw.userManagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserService userService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock PasswordResetEmailService passwordResetEmailService;
    @Mock UnlockAccountEmailService unlockAccountEmailService;

    @InjectMocks AuthService authService;

    private RegisterRequest registerRequest(String email, String password) {
        RegisterRequest r = new RegisterRequest();
        r.setNom("Dupont");
        r.setPrenom("Jean");
        r.setEmail(email);
        r.setMotDePasse(password);
        return r;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setMotDePasse(password);
        return r;
    }

    @Test
    void register_withDuplicateEmail_throws() {
        when(userRepository.existsByEmail("jean@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("  Jean@Example.COM ", "Passw0rd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existe déjà");

        // L'unicite est verifiee sur l'email NORMALISE (fix Phase 2), pas sur le brut.
        verify(userRepository).existsByEmail("jean@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withValidData_createsClientAndReturnsToken() {
        when(userRepository.existsByEmail("jean@example.com")).thenReturn(false);
        when(userService.generateNextId("USR")).thenReturn("2026-USR-00001");
        when(passwordEncoder.encode("Passw0rd")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken("jean@example.com", "client")).thenReturn("jwt-token");

        AuthResponse res = authService.register(registerRequest("  Jean@Example.COM ", "Passw0rd"));

        assertThat(res.getToken()).isEqualTo("jwt-token");
        assertThat(res.getEmail()).isEqualTo("jean@example.com");   // email normalise
        assertThat(res.getRoleUser()).isEqualTo(RoleUser.client);   // jamais admin via le domaine email
    }

    @Test
    void login_withWrongPassword_throwsAndIncrementsAttempts() {
        User user = activeClient();
        when(userRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("jean@example.com", "wrong")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    @Test
    void login_withValidCredentials_returnsToken() {
        User user = activeClient();
        when(userRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd", "hashed")).thenReturn(true);
        when(jwtService.generateToken("jean@example.com", "client")).thenReturn("jwt-token");

        AuthResponse res = authService.login(loginRequest("jean@example.com", "Passw0rd"));

        assertThat(res.getToken()).isEqualTo("jwt-token");
        assertThat(res.getEmail()).isEqualTo("jean@example.com");
    }

    private User activeClient() {
        User user = new User();
        user.setId("2026-USR-00001");
        user.setNom("Dupont");
        user.setPrenom("Jean");
        user.setEmail("jean@example.com");
        user.setMotDePasse("hashed");
        user.setActif(true);
        user.setRoleUser(RoleUser.client);
        user.setFailedLoginAttempts(0);
        return user;
    }
}

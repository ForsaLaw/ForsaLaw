package com.forsalaw.userManagement.service;

import  com.forsalaw.security.JwtService;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.model.AuthResponse;
import com.forsalaw.userManagement.model.ForgotPasswordRequest;
import com.forsalaw.userManagement.model.LoginRequest;
import com.forsalaw.userManagement.model.RequestUnlockAccountRequest;
import com.forsalaw.userManagement.model.RegisterRequest;
import com.forsalaw.userManagement.model.ResetPasswordRequest;
import com.forsalaw.userManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 3;

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetEmailService passwordResetEmailService;
    private final UnlockAccountEmailService unlockAccountEmailService;

    public AuthResponse register(RegisterRequest request) {
        // Normaliser l'email AVANT le controle d'unicite : sinon "  Foo@Bar.com" passe le
        // existsByEmail (compare le brut) puis est enregistre normalise => doublons possibles.
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email.");
        }
        User user = new User();
        user.setId(userService.generateNextId("USR"));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setEmail(normalizedEmail);
        user.setMotDePasse(passwordEncoder.encode(request.getMotDePasse()));
        // Les inscriptions publiques sont toujours CLIENT. Les admins/avocats sont provisionnes
        // separement (endpoint admin dedie / base de donnees), jamais via le domaine de l'email.
        user.setRoleUser(RoleUser.client);
        user.setActif(true);
        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getEmail(), user.getRoleUser().name());
        return toAuthResponse(user, token);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Email ou mot de passe incorrect."));
        if (!user.isActif()) {
            throw new IllegalArgumentException("Compte bloque. Veuillez contacter l'administrateur.");
        }
        if (!passwordEncoder.matches(request.getMotDePasse(), user.getMotDePasse())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setActif(false);
                user.setBlockedByFailedAttempts(true);
                userRepository.save(user);
                throw new IllegalArgumentException("Compte bloque apres 3 tentatives. Veuillez contacter l'administrateur.");
            }
            userRepository.save(user);
            int remaining = MAX_FAILED_LOGIN_ATTEMPTS - attempts;
            throw new IllegalArgumentException(
                    "Email ou mot de passe incorrect. Tentatives restantes: " + remaining + "."
            );
        }
        if (user.getFailedLoginAttempts() > 0 || user.isBlockedByFailedAttempts()) {
            user.setFailedLoginAttempts(0);
            user.setBlockedByFailedAttempts(false);
            userRepository.save(user);
        }
        String token = jwtService.generateToken(user.getEmail(), user.getRoleUser().name());
        return toAuthResponse(user, token);
    }

    public String forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);
            passwordResetEmailService.sendPasswordResetEmail(normalizedEmail, token);
            log.warn("Password reset token generated for {}", normalizedEmail);
        });
        return "Si l'email existe, un token de reinitialisation a ete genere.";
    }

    public String resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Token de reinitialisation invalide."));

        if (user.getPasswordResetExpiresAt() == null || LocalDateTime.now().isAfter(user.getPasswordResetExpiresAt())) {
            throw new IllegalArgumentException("Token de reinitialisation expire.");
        }

        user.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
        return "Mot de passe reinitialise avec succes.";
    }

    public String requestUnlockAccount(RequestUnlockAccountRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        unlockAccountEmailService.sendUnlockRequestToAdmin(normalizedEmail, request.getMessage());
        return "Si le compte est bloque, votre demande a ete transmise a l'administrateur.";
    }

    private AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getNom(),
                user.getPrenom(),
                user.getRoleUser()
        );
    }
}

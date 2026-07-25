package com.forsalaw.userManagement.controller;

import com.forsalaw.userManagement.model.AuthResponse;
import com.forsalaw.userManagement.model.ForgotPasswordRequest;
import com.forsalaw.userManagement.model.LoginRequest;
import com.forsalaw.userManagement.model.RequestUnlockAccountRequest;
import com.forsalaw.userManagement.model.RegisterRequest;
import com.forsalaw.userManagement.model.ResetPasswordRequest;
import com.forsalaw.security.JwtCookieService;
import com.forsalaw.userManagement.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtCookieService jwtCookieService;

    /**
     * URL publique du backend (port API). Obligatoire pour /api/auth/google : une redirection relative /oauth2/...
     * serait résolue par le navigateur sur l'origine du front (ex. localhost:3000) au lieu de l'API (8081).
     */
    @Value("${forsalaw.server.public-url:http://localhost:8081}")
    private String publicApiBaseUrl;

    @Operation(
            summary = "Inscription",
            description = "Cree un compte et depose le JWT dans un cookie HttpOnly. Le token reste "
                    + "present dans le corps de la reponse pour Swagger / clients HTTP (Bearer).")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return withAuthCookie(response);
    }

    @Operation(
            summary = "Connexion",
            description = "Authentifie l'utilisateur et depose le JWT dans un cookie HttpOnly. Le token reste "
                    + "present dans le corps de la reponse pour Swagger / clients HTTP (Bearer).")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return withAuthCookie(response);
    }

    @Operation(
            summary = "Amorcer le cookie CSRF",
            description = "Endpoint GET (donc sans protection CSRF) dont le seul but est de faire emettre "
                    + "le cookie XSRF-TOKEN, que le front renvoie ensuite dans l'en-tete X-XSRF-TOKEN.")
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        // Resoudre la valeur declenche l'ecriture du cookie (chargement differe en Spring Security 6).
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Deconnexion",
            description = "Efface le cookie d'authentification. Indispensable : un cookie HttpOnly ne peut pas "
                    + "etre supprime par le JavaScript du navigateur, seul le serveur peut l'expirer.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.clear().toString())
                .build();
    }

    /** Depose le JWT dans le cookie HttpOnly tout en conservant le corps JSON existant. */
    private ResponseEntity<AuthResponse> withAuthCookie(AuthResponse response) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.build(response.getToken()).toString())
                .body(response);
    }

    @Operation(summary = "Mot de passe oublie", description = "Genere un token temporaire de reinitialisation pour l'email fourni.")
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @Operation(summary = "Reinitialiser le mot de passe", description = "Reinitialise le mot de passe a partir d'un token valide.")
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @Operation(summary = "Demander le deblocage du compte", description = "Transmet une demande de deblocage a l'administrateur en cas de compte bloque.")
    @PostMapping("/request-unlock")
    public ResponseEntity<String> requestUnlock(@Valid @RequestBody RequestUnlockAccountRequest request) {
        return ResponseEntity.ok(authService.requestUnlockAccount(request));
    }

    @Operation(summary = "Login with Google", description = "Redirige vers Google OAuth2 pour authentification.")
    @GetMapping("/google")
    public RedirectView googleLogin() {
        String base = publicApiBaseUrl.endsWith("/")
                ? publicApiBaseUrl.substring(0, publicApiBaseUrl.length() - 1)
                : publicApiBaseUrl;
        return new RedirectView(base + "/oauth2/authorization/google");
    }
}

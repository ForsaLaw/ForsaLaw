package com.forsalaw.userManagement.controller;

import com.forsalaw.userManagement.model.ChangePasswordRequest;
import com.forsalaw.userManagement.model.NotificationPreferencesDTO;
import com.forsalaw.userManagement.model.UpdateNotificationPreferencesRequest;
import com.forsalaw.userManagement.model.UpdateUserRequest;
import com.forsalaw.userManagement.model.UserDTO;
import com.forsalaw.userManagement.service.ErasureService;
import com.forsalaw.userManagement.service.NotificationPreferencesService;
import com.forsalaw.userManagement.service.ProfilePhotoService;
import com.forsalaw.userManagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final NotificationPreferencesService notificationPreferencesService;
    private final ProfilePhotoService profilePhotoService;
    private final ErasureService erasureService;

    @Operation(summary = "Mon profil", description = "Retourne les informations du compte de l'utilisateur connecté (nom, prénom, email, rôle, etc.).")
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getMe(Authentication authentication) {
        String email = authentication.getName();
        UserDTO user = userService.getByEmail(email);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Preferences de notification email (rendez-vous)")
    @GetMapping("/me/notification-preferences")
    public ResponseEntity<NotificationPreferencesDTO> getNotificationPreferences(Authentication authentication) {
        return ResponseEntity.ok(notificationPreferencesService.getForCurrentUser(authentication.getName()));
    }

    @Operation(summary = "Mettre a jour les preferences de notification email (RDV)")
    @PutMapping("/me/notification-preferences")
    public ResponseEntity<NotificationPreferencesDTO> updateNotificationPreferences(
            Authentication authentication,
            @RequestBody UpdateNotificationPreferencesRequest request
    ) {
        return ResponseEntity.ok(notificationPreferencesService.updateForCurrentUser(authentication.getName(), request));
    }

    @Operation(summary = "Modifier mon profil", description = "Met à jour le profil (nom, prénom, email). Le mot de passe peut encore être modifié ici via motDePasseActuel + nouveauMotDePasse, ou via POST /me/password (recommandé).")
    @PutMapping("/me")
    public ResponseEntity<UserDTO> updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        String email = authentication.getName();
        UserDTO user = userService.updateProfile(email, request);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Envoyer ma photo de profil", description = "Client, avocat ou admin. Formats : JPEG, PNG, GIF, WebP. Taille max 5 Mo. Remplace la photo precedente.")
    @PostMapping(value = "/me/profile-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDTO> uploadMyProfilePhoto(
            Authentication authentication,
            @Parameter(description = "Fichier image")
            @RequestParam("fichier") MultipartFile fichier,
            HttpServletRequest request
    ) throws IOException {
        profilePhotoService.uploadProfilePhoto(authentication.getName(), fichier, request);
        return ResponseEntity.ok(userService.getByEmail(authentication.getName()));
    }

    @Operation(summary = "Telecharger ma photo de profil", description = "JWT requis. 404 si aucune photo.")
    @GetMapping(value = "/me/profile-photo", produces = { MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp", MediaType.IMAGE_GIF_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE })
    public ResponseEntity<Resource> downloadMyProfilePhoto(
            Authentication authentication,
            HttpServletRequest request
    ) {
        String email = authentication.getName();
        Resource resource = profilePhotoService.telechargerMaPhoto(email, request);
        MediaType ct = profilePhotoService.mediaTypeForUserProfilePhoto(email);
        String filename = profilePhotoService.originalFilenameForUserProfilePhoto(email);
        ContentDisposition cd = ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(ct)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .body(resource);
    }

    @Operation(
            summary = "Changer mon mot de passe",
            description = "Utilisateur connecté (client, avocat ou admin) : fournit le mot de passe actuel et le nouveau. "
                    + "Comptes créés uniquement via Google : message d'erreur explicite ; utiliser « mot de passe oublié » pour définir un mot de passe local.")
    @PostMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Supprimer mon compte",
            description = "Anonymise définitivement les données personnelles du compte "
                    + "(nom, prénom, email, téléphone, mot de passe, photo, et identifiants "
                    + "professionnels si le compte est un avocat) puis le désactive. "
                    + "Opération IRRÉVERSIBLE. Le journal d'audit, immuable, est conservé "
                    + "intact : il continue de référencer le compte, devenu non identifiable. "
                    + "Les sauvegardes antérieures conservent les valeurs d'origine jusqu'à "
                    + "leur expiration (30 jours).")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(Authentication authentication) {
        String email = authentication.getName();
        erasureService.effacer(email);
        return ResponseEntity.noContent().build();
    }
}

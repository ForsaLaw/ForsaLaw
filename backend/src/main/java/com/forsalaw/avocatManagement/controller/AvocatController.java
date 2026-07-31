package com.forsalaw.avocatManagement.controller;

import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import com.forsalaw.avocatManagement.entity.SpecialiteJuridique;
import com.forsalaw.avocatManagement.model.AvocatDTO;
import com.forsalaw.avocatManagement.model.DomaineAvecSpecialitesDTO;
import com.forsalaw.avocatManagement.model.SpecialiteItem;
import com.forsalaw.avocatManagement.model.CreateAvocatRequest;
import com.forsalaw.avocatManagement.model.UpdateAvocatRequest;
import com.forsalaw.avocatManagement.service.AvocatService;
import com.forsalaw.userManagement.model.ChangePasswordRequest;
import com.forsalaw.userManagement.service.ProfilePhotoService;
import com.forsalaw.userManagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/avocats")
@RequiredArgsConstructor
// Necessaire pour que les contraintes portees par les @RequestParam (ex. limite sur /match)
// soient reellement evaluees : sans @Validated, @Min/@Max sur un parametre sont ignores en
// silence et la borne n'existe que dans la documentation.
@Validated
public class AvocatController {

    private final AvocatService avocatService;
    private final UserService userService;
    private final ProfilePhotoService profilePhotoService;

    @Operation(summary = "Liste des domaines avec sous-domaines", description = "Retourne les domaines du droit avec la liste de leurs spécialités (sous-domaines). Pour cocher d'abord le droit puis le sous-domaine dans Swagger. Public.")
    @GetMapping("/domaines")
    public ResponseEntity<List<DomaineAvecSpecialitesDTO>> listDomaines() {
        List<DomaineAvecSpecialitesDTO> list = Arrays.stream(DomaineJuridique.values())
                .map(d -> {
                    List<SpecialiteItem> specialites = Arrays.stream(SpecialiteJuridique.values())
                            .filter(s -> s.getDomaine() == d)
                            .sorted(Comparator.comparing(SpecialiteJuridique::getLibelle))
                            .map(s -> new SpecialiteItem(s.name(), s.getLibelle()))
                            .collect(Collectors.toList());
                    return new DomaineAvecSpecialitesDTO(d.name(), d.getLibelle(), specialites);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "Liste des spécialités juridiques", description = "Retourne toutes les spécialités (code + libellé) en liste plate. Public.")
    @GetMapping("/specialites")
    public ResponseEntity<List<SpecialiteItem>> listSpecialites() {
        List<SpecialiteItem> list = Arrays.stream(SpecialiteJuridique.values())
                .map(s -> new SpecialiteItem(s.name(), s.getLibelle()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @Operation(
            summary = "Avocats recommandés pour un domaine (public)",
            description = "Retourne jusqu'à 3 avocats actifs exerçant dans le domaine indiqué, "
                    + "les mieux notés d'abord. Alimente la carte de recommandation affichée sous "
                    + "la réponse de l'assistant IA, dont le domaine est issu de la même énumération "
                    + "DomaineJuridique. Liste vide si aucun avocat n'exerce ce domaine."
    )
    @GetMapping("/match")
    public ResponseEntity<List<AvocatDTO>> match(
            @RequestParam DomaineJuridique domaine,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int limite
    ) {
        return ResponseEntity.ok(avocatService.trouverParDomaine(domaine, limite));
    }

    @Operation(summary = "Liste des avocats (public)", description = "Retourne une liste paginée des avocats actifs, avec filtres optionnels (spécialité, ville, vérifié). Accessible sans authentification.")
    @GetMapping
    public ResponseEntity<Page<AvocatDTO>> listPublic(
            @RequestParam(required = false) SpecialiteJuridique specialite,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) Boolean verifie,
            @PageableDefault(size = 20, sort = "dateCreation", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AvocatDTO> page = avocatService.findAllPublic(pageable, specialite, ville, verifie);
        return ResponseEntity.ok(page);
    }

    @Operation(summary = "Ma demande/profil avocat", description = "Retourne la demande ou le profil avocat de l'utilisateur connecté (client ou avocat).")
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT','AVOCAT')")
    public ResponseEntity<AvocatDTO> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        AvocatDTO avocat = avocatService.getMyProfile(email);
        return ResponseEntity.ok(avocat);
    }

    @Operation(summary = "Soumettre ma demande avocat", description = "Soumet une demande avocat pour le compte connecté. Le compte reste client tant que l'admin ne valide pas.")
    @PostMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT','AVOCAT')")
    public ResponseEntity<AvocatDTO> createMyProfile(
            Authentication authentication,
            @Valid @RequestBody CreateAvocatRequest request
    ) {
        String email = authentication.getName();
        AvocatDTO avocat = avocatService.createProfile(email, request);
        return ResponseEntity.ok(avocat);
    }

    @Operation(summary = "Modifier ma demande/profil avocat", description = "Met à jour la demande/profil avocat de l'utilisateur connecté.")
    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT','AVOCAT')")
    public ResponseEntity<AvocatDTO> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateAvocatRequest request
    ) {
        String email = authentication.getName();
        AvocatDTO avocat = avocatService.updateMyProfile(email, request);
        return ResponseEntity.ok(avocat);
    }

    @Operation(summary = "Désactiver mon profil avocat", description = "Désactive le profil avocat de l'utilisateur connecté. Le profil n'apparaîtra plus dans la liste publique.")
    @DeleteMapping("/me")
    @PreAuthorize("hasRole('AVOCAT')")
    public ResponseEntity<Void> deactivateMyProfile(Authentication authentication) {
        String email = authentication.getName();
        avocatService.deactivateMyProfile(email);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Changer mon mot de passe (profil avocat)",
            description = "Réservé aux comptes avec le rôle AVOCAT. Même logique que POST /api/users/me/password : mot de passe actuel + nouveau ; comptes Google-only exclus.")
    @PostMapping("/me/password")
    @PreAuthorize("hasRole('AVOCAT')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Envoyer ma photo de profil (espace avocat)", description = "Meme regles que POST /api/users/me/profile-photo ; JWT role AVOCAT.")
    @PostMapping(value = "/me/profile-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AVOCAT')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AvocatDTO> uploadMyProfilePhotoAvocat(
            Authentication authentication,
            @Parameter(description = "Fichier image")
            @RequestParam("fichier") MultipartFile fichier,
            HttpServletRequest request
    ) throws IOException {
        profilePhotoService.uploadProfilePhoto(authentication.getName(), fichier, request);
        return ResponseEntity.ok(avocatService.getMyProfile(authentication.getName()));
    }

    @Operation(summary = "Telecharger ma photo de profil (espace avocat)", description = "Identique a GET /api/users/me/profile-photo pour le compte connecte.")
    @GetMapping(value = "/me/profile-photo", produces = { MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp", MediaType.IMAGE_GIF_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE })
    @PreAuthorize("hasRole('AVOCAT')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Resource> downloadMyProfilePhotoAvocat(
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

    @Operation(summary = "Photo de profil publique (fiche avocat)", description = "Sans authentification : image pour la liste / fiche publique si l'avocat est actif et a une photo.")
    @GetMapping(value = "/{id}/profile-photo", produces = { MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp", MediaType.IMAGE_GIF_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE })
    public ResponseEntity<Resource> getPublicAvocatProfilePhoto(@PathVariable String id) {
        Resource resource = profilePhotoService.telechargerPhotoPubliqueAvocat(id);
        MediaType ct = profilePhotoService.mediaTypeForPublicAvocatProfilePhoto(id);
        String filename = profilePhotoService.originalFilenameForPublicAvocat(id);
        ContentDisposition cd = ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(ct)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .body(resource);
    }

    @Operation(summary = "Détail d'un avocat (public)", description = "Retourne la fiche d'un avocat par son identifiant (avocats actifs uniquement). Accessible sans authentification.")
    @GetMapping("/{id}")
    public ResponseEntity<AvocatDTO> getById(@PathVariable String id) {
        AvocatDTO avocat = avocatService.getById(id);
        return ResponseEntity.ok(avocat);
    }
}

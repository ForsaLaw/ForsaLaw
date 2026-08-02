package com.forsalaw.userManagement.service;

import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.model.AdminUpdateUserRequest;
import com.forsalaw.userManagement.model.ChangePasswordRequest;
import com.forsalaw.userManagement.model.UpdateUserRequest;
import com.forsalaw.userManagement.model.UserDTO;
import com.forsalaw.userManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final IdSequenceService idSequenceService;
    private final PasswordEncoder passwordEncoder;
    private final ProfilePhotoService profilePhotoService;

    /** Prochain ID séquentiel (même logique que le coffre documents / logs). */
    @Transactional
    public String generateNextId(String prefix) {
        return idSequenceService.generateNextId(prefix);
    }

    public UserDTO getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        return toDTO(user);
    }

    @Transactional
    public UserDTO updateProfile(String currentEmail, UpdateUserRequest request) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));

        if (request.getNom() != null && !request.getNom().isBlank()) {
            user.setNom(request.getNom());
        }
        if (request.getPrenom() != null && !request.getPrenom().isBlank()) {
            user.setPrenom(request.getPrenom());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank() && !request.getEmail().equals(currentEmail)) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Cet email est déjà utilisé.");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getNouveauMotDePasse() != null && !request.getNouveauMotDePasse().isBlank()) {
            if (request.getMotDePasseActuel() == null || request.getMotDePasseActuel().isBlank()) {
                throw new IllegalArgumentException("Le mot de passe actuel est requis pour changer le mot de passe.");
            }
            applyPasswordChange(user, request.getMotDePasseActuel(), request.getNouveauMotDePasse());
        }

        user = userRepository.save(user);
        return toDTO(user);
    }

    /**
     * Changement de mot de passe pour l'utilisateur connecté (client, avocat, admin).
     * Les comptes créés uniquement via Google OAuth n'ont pas de mot de passe local exploitable ici.
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        applyPasswordChange(user, request.getMotDePasseActuel(), request.getNouveauMotDePasse());
        userRepository.save(user);
    }

    private void applyPasswordChange(User user, String motDePasseActuel, String nouveauMotDePasse) {
        if (isGoogleOAuthPlaceholderPassword(user.getMotDePasse())) {
            throw new IllegalArgumentException(
                    "Ce compte a été créé via Google : il n'y a pas de mot de passe local à modifier. "
                            + "Utilisez la procédure « mot de passe oublié » pour définir un mot de passe ForsaLaw, ou gérez l'accès depuis votre compte Google.");
        }
        if (!passwordEncoder.matches(motDePasseActuel, user.getMotDePasse())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect.");
        }
        if (nouveauMotDePasse.equals(motDePasseActuel)) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent du mot de passe actuel.");
        }
        user.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
    }

    private static boolean isGoogleOAuthPlaceholderPassword(String hashedOrMarker) {
        return hashedOrMarker != null && hashedOrMarker.contains("GOOGLE_OAUTH2_ACCOUNT");
    }

    public Page<UserDTO> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toDTO);
    }

    public Page<UserDTO> findAll(Pageable pageable, String search) {
        if (search == null || search.isBlank()) {
            return userRepository.findAll(pageable).map(this::toDTO);
        }
        return userRepository.searchByNomOrPrenomOrEmail(search.trim(), pageable).map(this::toDTO);
    }

    public UserDTO getById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        return toDTO(user);
    }

    @Transactional
    public UserDTO updateByAdmin(String id, AdminUpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));

        if (request.getNom() != null && !request.getNom().isBlank()) {
            user.setNom(request.getNom());
        }
        if (request.getPrenom() != null && !request.getPrenom().isBlank()) {
            user.setPrenom(request.getPrenom());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank() && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Cet email est déjà utilisé.");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getRoleUser() != null) {
            user.setRoleUser(request.getRoleUser());
        }
        if (request.getActif() != null) {
            user.setActif(request.getActif());
        }
        if (request.getNouveauMotDePasse() != null && !request.getNouveauMotDePasse().isBlank()) {
            user.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        }

        user = userRepository.save(user);
        return toDTO(user);
    }

    // deactivateMyAccount a ete retiree : elle desactivait le compte en conservant nom,
    // prenom, email et telephone en clair, alors qu'elle servait « supprimer mon compte ».
    // L'effacement d'un compte par son titulaire passe desormais par ErasureService, qui
    // applique la politique decrite dans docs/ERASURE_POLICY.md. La desactivation
    // administrative ci-dessous est un acte different (suspension, pas effacement) et reste.

    @Transactional
    public void deactivateByAdmin(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        user.setActif(false);
        userRepository.save(user);
    }

    @Transactional
    public void deactivateByAdminEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé avec cet email."));
        user.setActif(false);
        userRepository.save(user);
    }

    @Transactional
    public void reactivateByAdmin(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        user.setActif(true);
        user.setFailedLoginAttempts(0);
        user.setBlockedByFailedAttempts(false);
        userRepository.save(user);
    }

    @Transactional
    public void reactivateByAdminEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé avec cet email."));
        user.setActif(true);
        user.setFailedLoginAttempts(0);
        user.setBlockedByFailedAttempts(false);
        userRepository.save(user);
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNom(user.getNom());
        dto.setPrenom(user.getPrenom());
        dto.setEmail(user.getEmail());
        dto.setRoleUser(user.getRoleUser());
        dto.setActif(user.isActif());
        dto.setDateCreation(user.getDateCreation());
        dto.setDateMiseAJour(user.getDateMiseAJour());
        dto.setProfilePhotoUrl(profilePhotoService.userProfilePhotoAbsoluteUrl(user));
        return dto;
    }
}

package com.forsalaw.avocatManagement.service;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.entity.AvocatVerificationStatus;
import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import com.forsalaw.avocatManagement.entity.SpecialiteJuridique;
import com.forsalaw.avocatManagement.model.*;
import com.forsalaw.avocatManagement.repository.AvocatRepository;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.service.ProfilePhotoService;
import com.forsalaw.userManagement.service.UserService;
import com.forsalaw.userManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvocatService {

    private final AvocatRepository avocatRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ProfilePhotoService profilePhotoService;

    @Transactional
    public AvocatDTO createProfile(String userEmail, CreateAvocatRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        if (user.getRoleUser() == RoleUser.admin) {
            throw new IllegalArgumentException("Un compte admin ne peut pas soumettre une demande avocat.");
        }
        if (avocatRepository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("Une demande/profil avocat existe déjà pour ce compte.");
        }
        if (avocatRepository.existsByNumeroCarteProfessionnelleIgnoreCase(request.getNumeroCarteProfessionnelle().trim())) {
            throw new IllegalArgumentException("Ce numero de carte professionnelle est deja utilise.");
        }
        if (avocatRepository.existsByCinIgnoreCase(request.getCin().trim())) {
            throw new IllegalArgumentException("Ce CIN est deja utilise.");
        }
        if (request.getDomaine() == null) {
            throw new IllegalArgumentException("Le domaine du droit est requis lors de la création du profil.");
        }
        if (request.getSpecialite() == null) {
            throw new IllegalArgumentException("La spécialité (sous-domaine) est requise lors de la création du profil.");
        }
        if (request.getSpecialite().getDomaine() != request.getDomaine()) {
            throw new IllegalArgumentException("La spécialité choisie doit appartenir au domaine sélectionné. Domaine choisi : " + request.getDomaine().getLibelle() + ".");
        }
        Avocat avocat = new Avocat();
        avocat.setId(userService.generateNextId("AVC"));
        avocat.setUser(user);
        avocat.setSpecialite(request.getSpecialite());
        avocat.setAnneesExperience(request.getAnneesExperience());
        avocat.setVille(request.getVille() != null ? request.getVille() : "");
        avocat.setDescription(request.getDescription());
        avocat.setNumeroCarteProfessionnelle(request.getNumeroCarteProfessionnelle().trim());
        avocat.setCin(request.getCin().trim());
        avocat.setBarreau(request.getBarreau().trim());
        avocat.setVerificationStatus(AvocatVerificationStatus.PENDING);
        avocat.setVerificationComment("Demande en attente de validation admin.");
        avocat.setVerifie(false);
        avocat = avocatRepository.save(avocat);
        return toDTO(avocat);
    }

    @Transactional(readOnly = true)
    public AvocatDTO getMyProfile(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        Avocat avocat = avocatRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Profil avocat non trouvé."));
        return toDTO(avocat);
    }

    @Transactional
    public AvocatDTO updateMyProfile(String userEmail, UpdateAvocatRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        Avocat avocat = avocatRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Profil avocat non trouvé."));
        if (request.getSpecialite() != null) avocat.setSpecialite(request.getSpecialite());
        if (request.getAnneesExperience() != null) avocat.setAnneesExperience(request.getAnneesExperience());
        if (request.getVille() != null) avocat.setVille(request.getVille());
        if (request.getDescription() != null) avocat.setDescription(request.getDescription());
        avocat = avocatRepository.save(avocat);
        return toDTO(avocat);
    }

    @Transactional
    public void deactivateMyProfile(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));
        Avocat avocat = avocatRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Profil avocat non trouvé."));
        avocat.setActif(false);
        avocatRepository.save(avocat);
    }

    public Page<AvocatDTO> findAllPublic(Pageable pageable, SpecialiteJuridique specialite, String ville, Boolean verifie) {
        return avocatRepository.findAllActifsFiltered(specialite, ville != null ? ville.trim() : null, verifie, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public AvocatDTO getById(String id) {
        Avocat avocat = avocatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouvé."));
        if (!avocat.isActif()) {
            throw new IllegalArgumentException("Avocat non trouvé.");
        }
        return toDTO(avocat);
    }

    public Page<AvocatDTO> findAllAdmin(Pageable pageable, SpecialiteJuridique specialite, String ville, Boolean verifie, Boolean actif) {
        return avocatRepository.findAllFiltered(specialite, ville != null ? ville.trim() : null, verifie, actif, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public AvocatDTO getByIdAdmin(String id) {
        Avocat avocat = avocatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouvé."));
        return toDTO(avocat);
    }

    @Transactional
    public AvocatDTO updateByAdmin(String id, AdminUpdateAvocatRequest request) {
        Avocat avocat = avocatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouvé."));
        if (request.getSpecialite() != null) avocat.setSpecialite(request.getSpecialite());
        if (request.getAnneesExperience() != null) avocat.setAnneesExperience(request.getAnneesExperience());
        if (request.getVille() != null) avocat.setVille(request.getVille());
        if (request.getDescription() != null) avocat.setDescription(request.getDescription());
        if (request.getActif() != null) avocat.setActif(request.getActif());
        avocat = avocatRepository.save(avocat);
        return toDTO(avocat);
    }

    /**
     * Décision admin : verification / acces espace avocat uniquement ({@code verifie} + {@code verificationStatus} + role user).
     */
    @Transactional
    public AvocatDTO updateVerificationByAdmin(String id, AdminAvocatVerificationRequest request) {
        Avocat avocat = avocatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouvé."));
        assertVerifieCoherentAvecStatut(request.getVerificationStatus(), request.getVerifie());
        applyVerificationStatus(avocat, request.getVerificationStatus());
        avocat = avocatRepository.save(avocat);
        return toDTO(avocat);
    }

    private static void assertVerifieCoherentAvecStatut(AvocatVerificationStatus status, boolean verifie) {
        boolean attendu = status == AvocatVerificationStatus.APPROVED;
        if (verifie != attendu) {
            throw new IllegalArgumentException(
                    "verifie doit etre " + attendu + " lorsque verificationStatus est " + status + "."
            );
        }
    }

    @Transactional
    public void deactivateByAdmin(String id) {
        Avocat avocat = avocatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouvé."));
        avocat.setActif(false);
        avocatRepository.save(avocat);
    }

    @Transactional
    public void deactivateByAdminEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé avec cet email."));
        Avocat avocat = avocatRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Aucun profil avocat associé à cet email."));
        avocat.setActif(false);
        avocatRepository.save(avocat);
    }

    @Transactional
    public void reactivateByAdmin(String id) {
        Avocat avocat = avocatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avocat non trouvé."));
        avocat.setActif(true);
        avocatRepository.save(avocat);
    }

    @Transactional
    public void reactivateByAdminEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé avec cet email."));
        Avocat avocat = avocatRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Aucun profil avocat associé à cet email."));
        avocat.setActif(true);
        avocatRepository.save(avocat);
    }

    /**
     * Avocats actifs exercant dans un domaine, les mieux places d'abord.
     *
     * <p>Le domaine est developpe en ses specialites AVANT la requete : {@code Avocat} porte une
     * specialite, jamais un domaine — celui-ci n'est qu'un attribut de l'enum, absent de la base.
     * Filtrer « par domaine » en SQL est donc impossible sans cette traduction.</p>
     *
     * <p>Un domaine sans aucune specialite rattachee renvoie une liste vide sans interroger la
     * base : un {@code IN ()} vide est un predicat invalide sur certains moteurs.</p>
     */
    @Transactional(readOnly = true)
    public List<AvocatDTO> trouverParDomaine(DomaineJuridique domaine, int limite) {
        List<SpecialiteJuridique> specialites = Arrays.stream(SpecialiteJuridique.values())
                .filter(s -> s.getDomaine() == domaine)
                .toList();

        if (specialites.isEmpty()) {
            return List.of();
        }
        return avocatRepository
                .findActifsParSpecialites(specialites, PageRequest.of(0, limite))
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private AvocatDTO toDTO(Avocat a) {
        AvocatDTO dto = new AvocatDTO();
        dto.setId(a.getId());
        dto.setUserId(a.getUser().getId());
        dto.setUserNom(a.getUser().getNom());
        dto.setUserPrenom(a.getUser().getPrenom());
        dto.setUserEmail(a.getUser().getEmail());
        dto.setSpecialite(a.getSpecialite());
        if (a.getSpecialite() != null) {
            dto.setSpecialiteLibelle(a.getSpecialite().getLibelle());
            dto.setDomaine(a.getSpecialite().getDomaine());
            dto.setDomaineLibelle(a.getSpecialite().getDomaine().getLibelle());
        } else {
            dto.setSpecialiteLibelle(null);
            dto.setDomaine(null);
            dto.setDomaineLibelle(null);
        }
        dto.setAnneesExperience(a.getAnneesExperience());
        dto.setVille(a.getVille());
        dto.setDescription(a.getDescription());
        dto.setNumeroCarteProfessionnelle(a.getNumeroCarteProfessionnelle());
        dto.setCin(a.getCin());
        dto.setBarreau(a.getBarreau());
        dto.setNoteMoyenne(a.getNoteMoyenne());
        dto.setTotalDossiers(a.getTotalDossiers());
        dto.setVerifie(a.isVerifie());
        dto.setVerificationStatus(resolveStatus(a));
        dto.setVerificationComment(a.getVerificationComment());
        dto.setActif(a.isActif());
        dto.setDateCreation(a.getDateCreation());
        dto.setDateMiseAJour(a.getDateMiseAJour());
        dto.setProfilePhotoPublicUrl(profilePhotoService.avocatPublicProfilePhotoAbsoluteUrl(a.getId(), a.getUser()));
        return dto;
    }

    private void applyVerificationStatus(Avocat avocat, AvocatVerificationStatus status) {
        avocat.setVerificationStatus(status);
        switch (status) {
            case APPROVED -> {
                avocat.setVerifie(true);
                avocat.getUser().setRoleUser(RoleUser.avocat);
                userRepository.save(avocat.getUser());
            }
            case PENDING, REJECTED -> {
                avocat.setVerifie(false);
                if (avocat.getUser().getRoleUser() == RoleUser.avocat) {
                    avocat.getUser().setRoleUser(RoleUser.client);
                    userRepository.save(avocat.getUser());
                }
            }
        }
    }

    private AvocatVerificationStatus resolveStatus(Avocat avocat) {
        if (avocat.getVerificationStatus() != null) {
            return avocat.getVerificationStatus();
        }
        return avocat.isVerifie() ? AvocatVerificationStatus.APPROVED : AvocatVerificationStatus.PENDING;
    }
}

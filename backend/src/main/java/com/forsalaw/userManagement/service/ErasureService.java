package com.forsalaw.userManagement.service;

import com.forsalaw.avocatManagement.entity.Avocat;
import com.forsalaw.avocatManagement.repository.AvocatRepository;
import com.forsalaw.documentManagement.repository.DocumentMetadataRepository;
import com.forsalaw.storage.S3StorageService;
import com.forsalaw.storage.StorageException;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Droit a l'effacement : anonymisation en place, sans jamais toucher au journal d'audit.
 *
 * <p><b>Anonymiser plutot que supprimer.</b> {@code audit_log} est append-only, garanti par un
 * declencheur en base (V8) qui rejette tout UPDATE et tout DELETE. Effacer la ligne
 * {@code users} briserait la reference de {@code actor_user_id} ; annuler ce champ serait un
 * UPDATE sur la table immuable, donc refuse. La ligne est donc CONSERVEE avec son identifiant,
 * et videe de ce qui identifie une personne : le journal continue de dire qu'un acte a ete
 * pose par l'utilisateur X, sans que X soit rattachable a quelqu'un.</p>
 *
 * <p><b>Pourquoi ce service existe.</b> {@code docs/ERASURE_POLICY.md} decrivait cette
 * procedure champ par champ, mais {@code DELETE /api/users/me} se contentait de basculer
 * {@code actif} a false : nom, prenom, email et telephone restaient en clair. Un compte
 * « supprime » conservait donc l'integralite de ses donnees personnelles, l'ecart entre la
 * politique ecrite et le code etant lui-meme le risque.</p>
 *
 * <p><b>Ce que l'effacement ne couvre pas.</b> Les sauvegardes anterieures contiennent encore
 * les valeurs d'origine et ne disparaissent qu'en expirant ({@code BACKUP_RETENTION_DAYS},
 * 30 jours). Une restauration reappliquerait donc des donnees effacees : la politique impose
 * de rejouer l'anonymisation apres toute restauration. Ce service ne peut pas garantir ce
 * point a lui seul.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErasureService {

    /** Domaine reserve par la RFC 2606 : non routable, donc impossible a recontacter. */
    private static final String DOMAINE_NON_ROUTABLE = "@invalid";

    private static final String NOM_ANONYME = "Utilisateur supprime";

    private final UserRepository userRepository;
    private final AvocatRepository avocatRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final S3StorageService storageService;

    /**
     * Anonymise le compte et desactive l'acces.
     *
     * <p>Idempotente : rejouer l'operation sur un compte deja anonymise n'a aucun effet
     * observable, ce qui compte apres une restauration de sauvegarde ou une double demande.</p>
     */
    @Transactional
    public void effacer(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé."));

        supprimerPhotoDeProfil(user);

        user.setNom(NOM_ANONYME);
        user.setPrenom("");
        // L'unicite de l'email est contrainte en base : un placeholder constant ferait echouer
        // le DEUXIEME effacement. L'identifiant, lui, est deja unique.
        user.setEmail("deleted+" + user.getId() + DOMAINE_NON_ROUTABLE);
        user.setTelephone(null);
        // Valeur qui n'est pas un hash BCrypt valide : aucune saisie ne peut y correspondre.
        // Surtout PAS un hash de chaine vide (une connexion avec un mot de passe vide
        // passerait), ni un prefixe de type {noop} : inoffensif avec le BCryptPasswordEncoder
        // actuel, il deviendrait un mot de passe en clair accepte le jour ou quelqu'un
        // basculerait sur un DelegatingPasswordEncoder.
        user.setMotDePasse("COMPTE_EFFACE_" + user.getId());
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        user.setProfilePhotoDocumentId(null);
        user.setActif(false);
        userRepository.save(user);

        effacerFicheAvocat(user);

        // Journalise l'identifiant seul : tracer l'email ici recreerait, dans les journaux
        // applicatifs, le lien que l'on vient precisement de rompre.
        log.info("Effacement : compte {} anonymise.", user.getId());
    }

    /**
     * Une fiche avocat porte ses propres identifiants (CIN, carte professionnelle, numero
     * ONAT). Les laisser en place viderait l'effacement de son sens : ils designent une
     * personne aussi surement qu'un nom.
     */
    private void effacerFicheAvocat(User user) {
        avocatRepository.findByUserId(user.getId()).ifPresent(avocat -> {
            avocat.setCin(null);
            avocat.setNumeroCarteProfessionnelle(null);
            avocat.setNumeroOnat(null);
            avocat.setBarreau(null);
            // Redaction libre : contient regulierement un parcours et des coordonnees.
            avocat.setDescription(null);
            // Un profil anonymise ne doit plus etre propose a la recherche d'avocats.
            avocat.setVerifie(false);
            avocatRepository.save(avocat);
            log.info("Effacement : fiche avocat {} anonymisee.", avocat.getId());
        });
    }

    /**
     * Retire reellement l'image du stockage objet, et pas seulement sa reference.
     *
     * <p>Une photo de profil est une donnee biometrique de fait. La suppression logique de
     * {@code DocumentService} ({@code supprime = true}) laisserait l'objet telechargeable pour
     * qui connait sa cle : l'effacement exige la suppression des octets.</p>
     *
     * <p>Un echec du stockage n'interrompt PAS l'anonymisation : il vaut mieux un compte
     * anonymise dont la photo subsiste — signale dans les journaux pour reprise manuelle —
     * qu'une transaction annulee laissant toutes les donnees personnelles en place.</p>
     */
    private void supprimerPhotoDeProfil(User user) {
        String documentId = user.getProfilePhotoDocumentId();
        if (documentId == null) {
            return;
        }
        documentMetadataRepository.findById(documentId).ifPresent(doc -> {
            try {
                storageService.delete(doc.getCheminFichier());
            } catch (StorageException e) {
                log.error("Effacement : photo de profil {} non supprimee du stockage ; "
                        + "une reprise manuelle est necessaire.", documentId, e);
            }
            documentMetadataRepository.delete(doc);
        });
    }
}

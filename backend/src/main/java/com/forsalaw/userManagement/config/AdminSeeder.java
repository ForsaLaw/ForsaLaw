package com.forsalaw.userManagement.config;

import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Amorce le premier compte ADMIN au demarrage, depuis la configuration (jamais depuis le code
 * ni une migration SQL : on ne stocke pas d'empreinte de mot de passe dans Git).
 *
 * <p>NB : contrairement aux anciens patchers supprimes, ceci ne fait AUCUN DDL runtime — c'est
 * un simple amorcage de donnees, conditionnel et idempotent.</p>
 *
 * <ul>
 *     <li>Si un ADMIN existe deja : ne rien faire.</li>
 *     <li>Sinon, si {@code admin.seed.email} et {@code admin.seed.password} sont definis :
 *         creer le compte ADMIN (mot de passe encode via {@link PasswordEncoder}).</li>
 *     <li>Sinon (ex. CI) : journaliser un avertissement et ne pas interrompre le demarrage.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email:}")
    private String seedEmail;

    @Value("${admin.seed.password:}")
    private String seedPassword;

    /**
     * Exige qu'un ADMIN existe ou puisse etre amorce. A mettre a {@code true} en production :
     * sans cela, un deploiement sans admin demarre normalement et ne se decouvre inutilisable
     * qu'au moment ou quelqu'un tente d'administrer.
     */
    @Value("${admin.seed.required:false}")
    private boolean seedRequired;

    /** Le compte amorce detient tous les droits : la longueur minimale n'est pas negociable. */
    private static final int LONGUEUR_MIN_MOT_DE_PASSE = 16;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (userRepository.existsByRoleUser(RoleUser.admin)) {
            log.info("Admin seed: an ADMIN user already exists. Skipping admin seed.");
            return;
        }

        String email = seedEmail == null ? "" : seedEmail.trim().toLowerCase();
        if (email.isEmpty() || seedPassword == null || seedPassword.isEmpty()) {
            // En production, une base SANS admin et SANS amorcage est un deploiement inutilisable :
            // plus personne ne peut verifier un avocat ni moderer quoi que ce soit, et le seul
            // recours est une intervention manuelle en base. Mieux vaut refuser de demarrer.
            if (seedRequired) {
                throw new IllegalStateException(
                        "Aucun compte ADMIN n'existe et ADMIN_SEED_EMAIL / ADMIN_SEED_PASSWORD ne sont "
                                + "pas definis, alors que ADMIN_SEED_REQUIRED=true. Demarrage interrompu.");
            }
            log.warn("No admin user exists and seed credentials are not configured. Skipping admin seed.");
            return;
        }

        // Un mot de passe d'amorcage faible est pire qu'aucun amorcage : le compte cree detient
        // tous les droits et son adresse est previsible. Le refus est franc, jamais un repli
        // silencieux sur une valeur de remplacement.
        if (seedPassword.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            throw new IllegalStateException(
                    "ADMIN_SEED_PASSWORD fait moins de " + LONGUEUR_MIN_MOT_DE_PASSE
                            + " caracteres. Le compte amorce detient tous les droits : "
                            + "utilisez un secret long et aleatoire. Demarrage interrompu.");
        }

        // Garde-fou : ne pas entrer en collision avec un compte non-admin existant (email unique).
        if (userRepository.existsByEmail(email)) {
            log.warn("Admin seed: email {} already belongs to an existing account. Skipping admin seed.", email);
            return;
        }

        User admin = new User();
        admin.setId(userService.generateNextId("USR"));
        admin.setNom("Admin");
        admin.setPrenom("ForsaLaw");
        admin.setEmail(email);
        admin.setMotDePasse(passwordEncoder.encode(seedPassword));
        admin.setRoleUser(RoleUser.admin);
        admin.setActif(true);
        userRepository.save(admin);

        log.info("Admin seed: created initial ADMIN account {}.", email);
    }
}

package com.forsalaw.ragManagement.chat.service;

import com.forsalaw.ragManagement.chat.model.AiConsentResponse;
import com.forsalaw.userManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/** Lecture et enregistrement du consentement IA, rattaches au compte et non au navigateur. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiConsentService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AiConsentResponse lire(String email) {
        var utilisateur = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        return new AiConsentResponse(utilisateur.getAiConsentVersion(), utilisateur.getAiConsentedAt());
    }

    /**
     * Enregistre le consentement a la version indiquee.
     *
     * <p>L'horodatage est repris a chaque acceptation, y compris si la version est identique :
     * c'est la date du geste qui fait foi, et un reconsentement volontaire ne doit pas etre
     * silencieusement ignore.</p>
     */
    @Transactional
    public AiConsentResponse enregistrer(String email, int version) {
        var utilisateur = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));

        utilisateur.setAiConsentVersion(version);
        utilisateur.setAiConsentedAt(LocalDateTime.now());
        userRepository.save(utilisateur);

        log.info("Consentement IA v{} enregistre pour l'utilisateur {}.", version, utilisateur.getId());
        return new AiConsentResponse(utilisateur.getAiConsentVersion(), utilisateur.getAiConsentedAt());
    }
}

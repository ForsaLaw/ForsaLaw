package com.forsalaw.sosManagement.service;

import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import com.forsalaw.avocatManagement.model.AvocatDTO;
import com.forsalaw.avocatManagement.service.AvocatService;
import com.forsalaw.sosManagement.entity.SosArrest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bouchon de mobilisation : journalise le message qui serait envoye, sans rien emettre.
 *
 * <p><b>Aucun SMS ni message WhatsApp n'est envoye.</b> Faute de compte fournisseur, ce bouchon
 * permet d'eprouver le flux de bout en bout.</p>
 *
 * <p>Il interroge tout de meme le referentiel des penalistes plutot que de journaliser un texte
 * fixe : c'est ce qui verifie qu'il EXISTE des destinataires. Un bouchon qui affiche « envoi en
 * cours » sans regarder la base masquerait le cas le plus probable en production — un domaine
 * sans aucun avocat inscrit, ou l'urgence ne serait transmise a personne.</p>
 */
@Service
@ConditionalOnProperty(name = "forsalaw.sos.dispatch.stub-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class StubDispatchService implements DispatchService {

    /** Une arrestation releve du penal : c'est ce domaine que l'on mobilise. */
    private static final DomaineJuridique DOMAINE_URGENCE = DomaineJuridique.DROIT_PENAL;
    private static final int NOMBRE_AVOCATS_MOBILISES = 5;

    private final AvocatService avocatService;

    @Override
    public boolean mobiliserAvocats(SosArrest signalement) {
        List<AvocatDTO> penalistes =
                avocatService.trouverParDomaine(DOMAINE_URGENCE, NOMBRE_AVOCATS_MOBILISES);

        if (penalistes.isEmpty()) {
            // Echec franc plutot que succes silencieux : personne n'a ete prevenu.
            log.error("SOS : AUCUN avocat penaliste actif en base — le signalement {} ne peut etre "
                    + "transmis a personne.", signalement.getId());
            return false;
        }

        log.warn("SOS : DISPATCHING SMS TO CRIMINAL LAWYERS... (simulation, aucun envoi reel)");
        log.warn("SOS : signalement {} — {} a {}, le {}. Contact : {}.",
                signalement.getId(),
                signalement.getNomDetenu(),
                signalement.getLieuArrestation(),
                signalement.getDateHeureArrestation(),
                signalement.getContactUrgence());

        penalistes.forEach(avocat -> log.warn("SOS :   -> destinataire simule : {} {} ({})",
                avocat.getUserPrenom(), avocat.getUserNom(), avocat.getSpecialiteLibelle()));

        return true;
    }
}

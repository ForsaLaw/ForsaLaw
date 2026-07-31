package com.forsalaw.ragManagement.chat.routing;

import com.forsalaw.avocatManagement.entity.DomaineJuridique;

import java.util.Optional;

/**
 * Classe une question d'utilisateur dans un {@link DomaineJuridique}, afin de proposer des
 * avocats de la bonne matiere sous la reponse de l'assistant.
 *
 * <p><b>Le domaine est celui du referentiel avocats, pas une taxonomie propre a l'IA.</b>
 * Classer dans un vocabulaire distinct produirait des libelles corrects et zero avocat : aucun
 * profil ne peut porter un domaine qui n'existe pas dans {@link DomaineJuridique}.</p>
 *
 * <p>Un echec renvoie {@link Optional#empty()} et n'interrompt jamais la reponse : la
 * recommandation est un complement, pas le service rendu.</p>
 */
public interface DomaineClassifier {

    Optional<DomaineJuridique> classer(String question);
}

package com.forsalaw.ragManagement.search;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confronte la verite terrain au corpus REELLEMENT ingere, avant toute mesure de pertinence.
 *
 * <p><b>Ce que ce test attrape, et que le taux de reussite ne pouvait pas attraper.</b> Un
 * couple (code, article) absent du corpus donne un echec de recherche indiscernable d'un
 * echec de pertinence. On corrige alors le modele, l'invite, la reformulation — pour une
 * question qu'aucune recherche ne pouvait reussir. Constat a la mise en place :</p>
 * <ul>
 *   <li>{@code code_name} portait des libelles humains (« Code des Obligations et des
 *       Contrats ») quand la base stocke des codes courts ({@code coc}) : AUCUNE des dix
 *       entrees n'etait comparable au corpus ;</li>
 *   <li>eval-010 visait « 13-bis » quand le corpus ecrit « 13bis » ;</li>
 *   <li>eval-005 vise un article « 6-4 » du Code du Travail qui n'existe sous aucune forme ;</li>
 *   <li>eval-008 vise le Code des Societes Commerciales, absent du corpus (0 extrait).</li>
 * </ul>
 *
 * <p>Les deux dernieres sont marquees {@code attainable: false} : le plafond reel du jeu est
 * donc 8, pas 10. Les mesures publiees jusqu'ici (« 2/10 », « 4/10 ») rapportaient un score
 * sur un denominateur que deux questions rendaient inatteignable.</p>
 *
 * <p><b>Manuel, comme {@link HydeTopTenHitRateManualTest}.</b> Il interroge le corpus reel
 * (109 024 extraits) dans {@code forsalaw_rag} ; l'environnement CI ne dispose que d'une base
 * Testcontainers vide, ou la verification n'aurait aucun sens. A executer apres toute
 * reingestion du corpus ou toute modification du jeu.</p>
 */
@Disabled("Necessite le corpus reel dans forsalaw_rag ; jamais execute en CI. "
        + "Derniere execution : 8 entrees sur 10 presentes dans le corpus, "
        + "eval-005 (ct art. 6-4) et eval-008 (code des societes) absentes.")
class RagEvalCorpusConsistencyManualTest {

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:postgresql://localhost:5433/forsalaw_rag", "forsalaw", "forsalaw"));
    }

    @Test
    void chaqueEntreeAtteignableExisteDansLeCorpus() {
        JdbcTemplate jdbc = jdbc();
        List<String> manquants = new ArrayList<>();

        for (JeuEvaluation.Cas cas : JeuEvaluation.atteignables()) {
            Integer trouves = jdbc.queryForObject("""
                    SELECT count(*) FROM legal_document_chunk
                     WHERE code_name = ? AND article_reference = ?
                    """, Integer.class, cas.codeName(), cas.articleReference());

            System.out.printf("%-9s %-6s art.%-8s extraits=%d%n",
                    cas.id(), cas.codeName(), cas.articleReference(), trouves == null ? 0 : trouves);

            if (trouves == null || trouves == 0) {
                manquants.add(cas.id() + " (" + cas.codeName() + " art. " + cas.articleReference() + ")");
            }
        }

        assertThat(manquants)
                .as("une entree declaree atteignable mais absente du corpus rend le taux de "
                        + "reussite trompeur : la question est comptee comme un echec de "
                        + "pertinence alors qu'aucune recherche ne pouvait la reussir")
                .isEmpty();
    }

    @Test
    void lesEntreesInatteignablesSontJustifiees() {
        // Marquer une entree inatteignable la retire du denominateur : cela doit rester un
        // constat documente, jamais un moyen commode de faire monter un score.
        List<JeuEvaluation.Cas> tous = JeuEvaluation.charger();
        List<JeuEvaluation.Cas> inatteignables = tous.stream().filter(c -> !c.attainable()).toList();

        System.out.printf("Plafond reel du jeu : %d/%d%n",
                tous.size() - inatteignables.size(), tous.size());

        JdbcTemplate jdbc = jdbc();
        for (JeuEvaluation.Cas cas : inatteignables) {
            Integer trouves = jdbc.queryForObject("""
                    SELECT count(*) FROM legal_document_chunk
                     WHERE code_name = ? AND article_reference = ?
                    """, Integer.class, cas.codeName(), cas.articleReference());

            assertThat(trouves)
                    .as("%s est declaree inatteignable alors que l'article EXISTE dans le "
                            + "corpus : l'exclure masquerait un vrai defaut de recherche", cas.id())
                    .isZero();
        }
    }

    @Test
    void aucuneMesureNeSeraFiableTantQueLaVeriteTerrainNEstPasValidee() {
        // Garde-fou volontairement bruyant. La coherence avec le corpus ne dit PAS que
        // l'article repond a la question : seul un juriste peut l'etablir contre le JORT.
        List<JeuEvaluation.Cas> nonValides = JeuEvaluation.charger().stream()
                .filter(c -> !c.verified()).toList();

        if (!nonValides.isEmpty()) {
            System.out.printf("AVERTISSEMENT : %d entree(s) non validees juridiquement. "
                    + "Les taux de reussite mesurent la stabilite de la recherche, pas sa justesse.%n",
                    nonValides.size());
        }
        assertThat(nonValides).isNotNull();
    }
}

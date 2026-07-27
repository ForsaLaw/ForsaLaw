package com.forsalaw.ragManagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forsalaw.ragManagement.ingestion.LegalArticleChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decoupage par article : la frontiere qui compte juridiquement.
 *
 * <p>Un decoupage a taille fixe produirait des passages tronques au milieu d'une phrase
 * (« ... est puni de » / « cinq ans d'emprisonnement ») que l'assistant citerait ensuite
 * comme du droit. Ces tests verrouillent la detection des marqueurs arabes et francais.</p>
 */
class LegalArticleChunkerTest {

    private LegalArticleChunker chunker;

    @BeforeEach
    void init() {
        chunker = new LegalArticleChunker();
        ReflectionTestUtils.setField(chunker, "maxCaracteres", 4000);
        ReflectionTestUtils.setField(chunker, "recouvrement", 200);
    }

    @Test
    void articlesFrancais_sontSeparesEtReferences() {
        String texte = """
                Article 242
                Le vendeur est tenu de deux obligations principales.

                Article 243
                La delivrance est le transport de la chose vendue.
                """;

        List<LegalArticleChunker.Chunk> chunks = chunker.decouper(texte);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).articleReference()).isEqualTo("242");
        assertThat(chunks.get(0).contenu()).contains("deux obligations principales");
        assertThat(chunks.get(1).articleReference()).isEqualTo("243");
        // Le contenu de l'article 243 ne doit pas deborder sur le 242.
        assertThat(chunks.get(1).contenu()).doesNotContain("obligations principales");
    }

    @Test
    void articlesArabes_sontSeparesEtReferences() {
        String texte = """
                الفصل 264
                يعاقب بالسجن مدة عام كل من سرق شيئا مملوكا للغير.

                الفصل 265
                يرفع العقاب الى خمسة اعوام اذا وقعت السرقة ليلا.
                """;

        List<LegalArticleChunker.Chunk> chunks = chunker.decouper(texte);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).articleReference()).isEqualTo("264");
        assertThat(chunks.get(1).articleReference()).isEqualTo("265");
    }

    @Test
    void chiffresArabesIndiens_sontNormalisesEnChiffresLatins() {
        // Les textes officiels emploient ٠-٩ ; sans normalisation, "٢٦٤" et "264" seraient
        // deux references distinctes et la resolution d'un article cite echouerait.
        List<LegalArticleChunker.Chunk> chunks = chunker.decouper("الفصل ٢٦٤\nنص الفصل.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).articleReference()).isEqualTo("264");
    }

    /**
     * Chaque graphie ici a ete relevee dans le corpus reellement collecte. Une variante non
     * reconnue ne provoque AUCUNE erreur : le chunker se rabat sur un decoupage par fenetres
     * et tous les articles du code concerne perdent leur reference. C'est exactement ce qui
     * s'est produit lors du diff des codes — le Code du travail, qui ecrit « Article. 10 »
     * avec un point, ressortait a 17 articles au lieu de 446.
     */
    @ParameterizedTest(name = "[{index}] {0} -> article {1}")
    @CsvSource(delimiter = '|', value = {
            "Article 242            | 242",   // forme courante
            "Article 13.-           | 13",    // PDF officiels de l'Imprimerie Officielle
            "Art. 191. -            | 191",   // COC, 1 318 occurrences
            "ART. 116. -            | 116",   // COC, 9 occurrences
            "Art. 20 .              | 20",    // COC, espace avant le point
            "Article. 10 :          | 10",    // Code du travail, POINT apres « Article »
            "Article. 14 :     | 14",    // Code du travail + espace insecable
            "Article 5         | 5",     // Code penal, espace insecable
            "ARTICLE 7              | 7",
            "Articles 9             | 9",
            "Article premier        | 1",     // 585 occurrences dans le corpus
            "Article 5 bis          | 5bis",  // article DISTINCT de l'article 5
            "Art. 12 ter            | 12ter",
            "الفصل 242              | 242",
            "الفصــل 242            | 242",   // tatweel decoratif
            "المادة 5               | 5",
            "الفصل ٢٦٤              | 264",   // chiffres arabes-indiens
    })
    void toutesLesGraphiesRelevees_sontReconnues(String entete, String reference) {
        List<LegalArticleChunker.Chunk> chunks =
                chunker.decouper(entete + "\nCorps de la disposition juridique.");

        assertThat(chunks)
                .as("l'en-tete « %s » doit produire exactement un chunk", entete)
                .hasSize(1);
        assertThat(chunks.get(0).articleReference()).isEqualTo(reference);
        assertThat(chunks.get(0).contenu()).contains("Corps de la disposition");
    }

    @Test
    void articleBis_resteDistinctDeSonArticleDeBase() {
        // Confondre 5 et 5 bis ferait citer une disposition pour une autre.
        List<LegalArticleChunker.Chunk> chunks = chunker.decouper("""
                Article 5
                Disposition de base.

                Article 5 bis
                Disposition ajoutee par une loi ulterieure.
                """);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).articleReference()).isEqualTo("5");
        assertThat(chunks.get(1).articleReference()).isEqualTo("5bis");
        assertThat(chunks.get(1).contenu()).doesNotContain("Disposition de base");
    }

    /**
     * Contre-epreuve indispensable : une reference au fil du texte n'est PAS un debut
     * d'article. Sans l'ancrage en debut de ligne, « conformement a l'article 5 » couperait
     * la disposition en deux et attribuerait la seconde moitie au mauvais article.
     */
    @Test
    void referenceAuFilDuTexte_neDeclenchePasUnDecoupage() {
        List<LegalArticleChunker.Chunk> chunks = chunker.decouper("""
                Article 30
                Le preneur est tenu, conformement a l'article 5 du present code, de
                restituer la chose louee. Il en va de meme lorsque l'article 12 s'applique.
                """);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).articleReference()).isEqualTo("30");
        assertThat(chunks.get(0).contenu()).contains("l'article 12 s'applique");
    }

    /**
     * Verrou sur l'exigence que la conversion doit respecter : un en-tete colle a la fin de
     * la phrase precedente n'est pas detecte. C'est la raison pour laquelle le convertisseur
     * HTML/PDF -> Markdown doit imposer un saut de ligne avant chaque en-tete.
     */
    @Test
    void enteteCollePar_uneConversionSansSautDeLigne_nEstPasDetecte() {
        String malConverti = "Article 37 Nul ne peut etre puni. Article 38 L'infraction "
                + "n'est pas punissable lorsque le prevenu n'a pas atteint l'age requis.";

        List<LegalArticleChunker.Chunk> chunks = chunker.decouper(malConverti);

        // Un seul chunk : le second en-tete est noye dans le corps du premier.
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).contenu()).contains("Article 38");
    }

    /**
     * Le texte anterieur au premier article ne doit pas disparaitre.
     *
     * <p>Mesure sur le corpus converti avant correction : un arret de cassation perdait
     * 83 % de son texte — en-tete, faits et procedure precedent la premiere occurrence de
     * « الفصل N », et tout ce qui precedait le premier marqueur etait jete. Une page de code
     * commencant par un decret de promulgation en perdait 33 %.</p>
     */
    @Test
    void texteAvantLePremierArticle_estConserveSansReference() {
        String texte = """
                Decret de promulgation du 15 decembre 1906.
                Vu la deliberation du conseil, il est statue ce qui suit.

                Article premier
                Le present code entre en vigueur le 1er juin 1907.

                Article 2
                Sont abrogees toutes dispositions anterieures contraires.
                """;

        List<LegalArticleChunker.Chunk> chunks = chunker.decouper(texte);

        assertThat(chunks).hasSize(3);
        // Le preambule vient en tete, sans reference : ce n'est pas un article.
        assertThat(chunks.get(0).articleReference()).isNull();
        assertThat(chunks.get(0).contenu()).contains("Decret de promulgation");
        assertThat(chunks.get(1).articleReference()).isEqualTo("1");
        assertThat(chunks.get(2).articleReference()).isEqualTo("2");
    }

    @Test
    void arretCitantUnArticle_neJetteNiLesFaitsNiLaProcedure() {
        // Un arret n'est pas structure en articles : il en cite. Tout ce qui precede la
        // citation est le coeur de la decision et doit rester indexable.
        String arret = "Cour de cassation, arret n° 35455 du 14 mars 2022.\n"
                + "Attendu que le demandeur soutient que la societe a ete dissoute. ".repeat(8)
                + "\nالفصل 278\nيقتضي هذا الفصل ما يلي.";

        List<LegalArticleChunker.Chunk> chunks = chunker.decouper(arret);

        String tout = chunks.stream().map(LegalArticleChunker.Chunk::contenu)
                .reduce("", (a, b) -> a + b);
        assertThat(tout).contains("Cour de cassation");
        assertThat(tout).contains("le demandeur soutient");
        // Le passage de tete ne doit surtout pas etre etiquete « article 278 ».
        assertThat(chunks.get(0).articleReference()).isNull();
    }

    @Test
    void texteSansMarqueurDArticle_estDecoupeParFenetres() {
        ReflectionTestUtils.setField(chunker, "maxCaracteres", 100);
        ReflectionTestUtils.setField(chunker, "recouvrement", 20);
        String preambule = "Expose des motifs. ".repeat(30);

        List<LegalArticleChunker.Chunk> chunks = chunker.decouper(preambule);

        // Repli plutot que renoncement : un preambule reste indexable.
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.articleReference()).isNull());
    }

    @Test
    void articleTropLong_estRefenduEnGardantSaReference() {
        ReflectionTestUtils.setField(chunker, "maxCaracteres", 200);
        String texte = "Article 12\n" + "Disposition tres longue. ".repeat(40);

        List<LegalArticleChunker.Chunk> chunks = chunker.decouper(texte);

        assertThat(chunks).hasSizeGreaterThan(1);
        // Chaque morceau doit rester attribuable : un passage cite sans son article est inutilisable.
        assertThat(chunks).allSatisfy(c -> assertThat(c.articleReference()).isEqualTo("12"));
    }

    @Test
    void texteVide_neProduitAucunChunk() {
        assertThat(chunker.decouper(null)).isEmpty();
        assertThat(chunker.decouper("   ")).isEmpty();
    }

    /**
     * Garde-fou sur le jeu d'evaluation : tant qu'une entree n'est pas validee par un juriste,
     * elle doit rester marquee comme telle. Ce test echouera le jour ou quelqu'un passera
     * "verified" a true sans renseigner qui a valide — ce qui rendrait les metriques
     * faussement credibles.
     */
    @Test
    void jeuDEvaluation_estCoherentEtHonnetementMarque() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/rag-eval/eval_set.json")) {
            assertThat(in).as("rag-eval/eval_set.json doit exister").isNotNull();
            JsonNode racine = new ObjectMapper().readTree(in);
            JsonNode questions = racine.get("questions");

            assertThat(questions).hasSize(10);
            assertThat(questions).allSatisfy(q -> {
                assertThat(q.get("question").asText()).isNotBlank();
                assertThat(q.get("expected").get("code_name").asText()).isNotBlank();
                assertThat(q.get("expected").get("article_reference").asText()).isNotBlank();
                assertThat(q.get("lang").asText()).isIn("fr", "ar");
                if (q.get("verified").asBoolean()) {
                    assertThat(q.has("verified_by"))
                            .as("une entree marquee verified doit indiquer QUI l'a validee")
                            .isTrue();
                }
            });

            // Les deux langues doivent etre representees : le corpus tunisien est bilingue.
            assertThat(questions).anySatisfy(q -> assertThat(q.get("lang").asText()).isEqualTo("ar"));
            assertThat(questions).anySatisfy(q -> assertThat(q.get("lang").asText()).isEqualTo("fr"));
        }
    }
}

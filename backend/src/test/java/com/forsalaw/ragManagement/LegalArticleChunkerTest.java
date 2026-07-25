package com.forsalaw.ragManagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forsalaw.ragManagement.ingestion.LegalArticleChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

package com.forsalaw.ragManagement.ingestion;

import com.forsalaw.AbstractIntegrationTest;
import com.forsalaw.ragManagement.embedding.EmbeddingClient;
import com.forsalaw.storage.S3StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Depot d'un PDF unique via l'endpoint admin. {@link S3StorageService} est simule (aucun MinIO
 * necessaire ici, contrairement aux tests de {@code S3StorageService} lui-meme) : ce qui est
 * verifie, c'est que l'archivage est bien APPELE avec la bonne cle, pas le comportement de S3.
 */
class AdminRagIngestionServiceIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    EmbeddingClient embeddingClient;

    @MockBean
    S3StorageService s3StorageService;

    @Autowired
    AdminRagIngestionService adminRagIngestionService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void preparer() {
        jdbcTemplate.execute("DELETE FROM legal_document_chunk");
        jdbcTemplate.execute("DELETE FROM rag_supersede_proposal");

        when(embeddingClient.dimensions()).thenReturn(1024);
        when(embeddingClient.modelName()).thenReturn("stub");
        when(embeddingClient.embed(anyList())).thenAnswer(inv -> {
            List<String> textes = inv.getArgument(0);
            return textes.stream().map(t -> new float[1024]).toList();
        });
    }

    @Test
    void pdfValide_estIngereEtArchive() throws IOException {
        var fichier = new MockMultipartFile("fichier", "coc-extrait.pdf", "application/pdf",
                pdfAvecTexte("Article 1\nDisposition de test pour l'ingestion admin, redigee assez longuement pour depasser le seuil minimal de caracteres par page."));

        var resultat = adminRagIngestionService.ingererPdf(
                fichier, "coc-test", 1, null, null, "Extrait COC", "test-admin-1", "admin@forsalaw.tn");

        assertThat(resultat.dejaIngere()).isFalse();
        assertThat(resultat.chunksCrees()).isGreaterThan(0);
        assertThat(resultat.sourceReference()).isEqualTo("test-admin-1");

        verify(s3StorageService).upload(
                eq("rag-sources/test-admin-1.pdf"), any(), anyLong(), eq("application/pdf"));
    }

    @Test
    void sourceReferenceDejaIngeree_neReArchivePasEtNeRevectorisePas() throws IOException {
        var fichier = new MockMultipartFile("fichier", "doc.pdf", "application/pdf",
                pdfAvecTexte("Article 1\nTexte suffisamment long pour depasser le seuil minimal "
                        + "de caracteres par page exige par PdfTextExtractor."));

        adminRagIngestionService.ingererPdf(
                fichier, "code-x", 1, null, null, null, "test-doublon", "admin@forsalaw.tn");
        var second = adminRagIngestionService.ingererPdf(
                fichier, "code-x", 1, null, null, null, "test-doublon", "admin@forsalaw.tn");

        assertThat(second.dejaIngere()).isTrue();
        assertThat(second.chunksCrees()).isZero();
        // Un seul appel au total : le second depot ne doit ni re-archiver, ni revectoriser.
        verify(s3StorageService, times(1)).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void sourceReferenceAbsente_estDeduiteDuContenuEtResteIdempotente() throws IOException {
        byte[] contenu = pdfAvecTexte("Article 1\nContenu identique, suffisamment long pour "
                + "depasser le seuil minimal de caracteres par page exige par PdfTextExtractor.");
        var premier = new MockMultipartFile("fichier", "a.pdf", "application/pdf", contenu);
        var second = new MockMultipartFile("fichier", "b.pdf", "application/pdf", contenu);

        var resultat1 = adminRagIngestionService.ingererPdf(
                premier, "code-y", 1, null, null, null, null, "admin@forsalaw.tn");
        var resultat2 = adminRagIngestionService.ingererPdf(
                second, "code-y", 1, null, null, null, null, "admin@forsalaw.tn");

        assertThat(resultat1.sourceReference()).startsWith("upload:sha256:");
        // Meme CONTENU, noms de fichier differents : meme reference deduite -> reconnu comme doublon.
        assertThat(resultat2.sourceReference()).isEqualTo(resultat1.sourceReference());
        assertThat(resultat2.dejaIngere()).isTrue();
    }

    @Test
    void niveau3SansCabinet_estRefuseAvantTouteEcriture() throws IOException {
        var fichier = new MockMultipartFile("fichier", "prive.pdf", "application/pdf",
                pdfAvecTexte("Article 1\nDossier interne, texte suffisamment long pour depasser "
                        + "le seuil minimal de caracteres par page exige par PdfTextExtractor."));

        assertThatThrownBy(() -> adminRagIngestionService.ingererPdf(
                fichier, "dossier-x", 3, null, null, null, "test-niveau3", "admin@forsalaw.tn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verify(s3StorageService, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void fichierNonPdf_estRefuse() {
        var fichier = new MockMultipartFile("fichier", "notes.txt", "text/plain",
                "ceci n'est pas un PDF".getBytes());

        assertThatThrownBy(() -> adminRagIngestionService.ingererPdf(
                fichier, "code-z", 1, null, null, null, "test-non-pdf", "admin@forsalaw.tn"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pdfSansCoucheTexte_estRefuseExplicitement() {
        var fichier = new MockMultipartFile("fichier", "scan.pdf", "application/pdf", pdfSansTexte());

        assertThatThrownBy(() -> adminRagIngestionService.ingererPdf(
                fichier, "code-scan", 1, null, null, null, "test-scan", "admin@forsalaw.tn"))
                .isInstanceOf(ScannedPdfException.class);
    }

    private static byte[] pdfAvecTexte(String contenu) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font police = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream flux = new PDPageContentStream(document, page)) {
                flux.beginText();
                flux.setFont(police, 12);
                flux.newLineAtOffset(50, 700);
                for (String ligne : contenu.split("\n")) {
                    flux.showText(ligne);
                    flux.newLineAtOffset(0, -15);
                }
                flux.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Page valide mais VIDE : aucun flux de contenu, donc aucune couche texte extractible. */
    private static byte[] pdfSansTexte() {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}

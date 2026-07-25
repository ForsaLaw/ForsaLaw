package com.forsalaw.storage;

import com.forsalaw.AbstractIntegrationTest;
import com.forsalaw.util.HashingService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie le cycle complet du stockage objet contre un vrai MinIO (Testcontainers) :
 * depot -> relecture -> integrite SHA-256 -> suppression.
 *
 * <p>Le hash est le point critique : le coffre-fort documentaire compare le SHA-256 recalcule
 * a celui enregistre au depot. Toute alteration du flux (encodage, troncature, buffer mal
 * ferme) casserait la verification d'integrite ET la valeur probante des documents signes.</p>
 *
 * <p><b>Prerequis :</b> un daemon Docker (CI : ubuntu-latest ; en local : Docker Desktop).</p>
 */
class S3StorageIntegrationTest extends AbstractIntegrationTest {

    private static final String BUCKET = "forsalaw-test";

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-06-13T22-53-53Z");

    @DynamicPropertySource
    static void proprietesStockage(DynamicPropertyRegistry registry) {
        registry.add("forsalaw.storage.s3.endpoint-override", MINIO::getS3URL);
        registry.add("forsalaw.storage.s3.access-key", MINIO::getUserName);
        registry.add("forsalaw.storage.s3.secret-key", MINIO::getPassword);
        registry.add("forsalaw.storage.s3.bucket", () -> BUCKET);
        // MinIO ne gere pas les URLs virtual-host (bucket.hote/...) : path-style obligatoire.
        registry.add("forsalaw.storage.s3.path-style-access", () -> true);
    }

    @Autowired
    S3StorageService storageService;

    @Autowired
    S3Client s3Client;

    @Autowired
    HashingService hashingService;

    /** L'application ne cree pas le bucket (idem production) : le test s'en charge. */
    @BeforeEach
    void creerBucket() {
        try {
            s3Client.createBucket(b -> b.bucket(BUCKET));
        } catch (BucketAlreadyOwnedByYouException e) {
            // Bucket deja cree par un test precedent : rien a faire.
        }
    }

    @Test
    void cycleComplet_depotRelectureIntegriteSuppression() throws IOException {
        byte[] pdfOriginal = pdfMinimal();
        String hashOriginal = hashingService.calculerHashSha256(new ByteArrayInputStream(pdfOriginal));
        String cle = S3StorageService.DOCUMENTS_PREFIX + UUID.randomUUID() + ".pdf";

        // 1. Depot
        storageService.upload(cle, new ByteArrayInputStream(pdfOriginal), pdfOriginal.length, "application/pdf");
        assertThat(storageService.exists(cle)).isTrue();

        // 2. Relecture : la taille annoncee doit venir de l'en-tete S3, pas d'une lecture du flux.
        S3ObjectResource resource = storageService.download(cle);
        assertThat(resource.contentLength()).isEqualTo(pdfOriginal.length);

        byte[] relu;
        try (InputStream in = resource.getInputStream()) {
            relu = in.readAllBytes();
        }
        assertThat(relu).isEqualTo(pdfOriginal);

        // 3. Integrite : le hash recalcule apres aller-retour doit etre identique.
        String hashApresRelecture = hashingService.calculerHashSha256(new ByteArrayInputStream(relu));
        assertThat(hashApresRelecture).isEqualTo(hashOriginal);

        // 4. Suppression
        storageService.delete(cle);
        assertThat(storageService.exists(cle)).isFalse();
        assertThatThrownBy(() -> storageService.openStream(cle))
                .isInstanceOf(StorageException.ObjectNotFound.class);
    }

    @Test
    void suppression_dUneCleInexistante_estIdempotente() {
        String cleAbsente = S3StorageService.DOCUMENTS_PREFIX + UUID.randomUUID() + ".pdf";
        storageService.delete(cleAbsente); // S3 ne signale pas l'absence : ne doit pas lever.
        assertThat(storageService.exists(cleAbsente)).isFalse();
    }

    /** PDF valide d'une page, genere via PDFBox (deja utilise par la signature electronique). */
    private byte[] pdfMinimal() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }
}

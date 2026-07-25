package com.forsalaw.storage;

import com.forsalaw.AbstractIntegrationTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Base des tests d'integration du stockage objet : ajoute un vrai MinIO au PostgreSQL
 * de {@link AbstractIntegrationTest}.
 *
 * <p><b>Chaque classe fille declare son PROPRE conteneur MinIO</b> — ne pas le remonter ici
 * pour "mutualiser". Un conteneur {@code static @Container} est arrete a la fin de chaque
 * classe de test, alors qu'un contexte Spring dont toutes les proprietes sont identiques est
 * mis en cache et reutilise d'une classe a l'autre. Le conteneur repart alors sur un nouveau
 * port aleatoire pendant que le S3Client du contexte cache pointe toujours sur l'ancien :
 * "Connection refused". Un conteneur par classe garantit des proprietes distinctes, donc un
 * contexte par classe, cree apres le demarrage de ses propres conteneurs.</p>
 *
 * <p><b>Prerequis :</b> un daemon Docker (CI : ubuntu-latest ; en local : Docker Desktop).</p>
 */
public abstract class AbstractStorageIntegrationTest extends AbstractIntegrationTest {

    protected static final String BUCKET = "forsalaw-test";
    protected static final String IMAGE_MINIO = "minio/minio:RELEASE.2024-06-13T22-53-53Z";

    /** A appeler depuis le {@code @DynamicPropertySource} de chaque classe fille. */
    protected static void enregistrerProprietesStockage(DynamicPropertyRegistry registry,
                                                        MinIOContainer minio) {
        registry.add("forsalaw.storage.s3.endpoint-override", minio::getS3URL);
        registry.add("forsalaw.storage.s3.access-key", minio::getUserName);
        registry.add("forsalaw.storage.s3.secret-key", minio::getPassword);
        registry.add("forsalaw.storage.s3.bucket", () -> BUCKET);
        // MinIO ne gere pas les URLs virtual-host (bucket.hote/...) : path-style obligatoire.
        registry.add("forsalaw.storage.s3.path-style-access", () -> true);
    }

    @Autowired
    protected S3Client s3Client;

    /** L'application ne cree pas le bucket (idem production) : les tests s'en chargent. */
    @BeforeEach
    void creerBucket() {
        try {
            s3Client.createBucket(b -> b.bucket(BUCKET));
        } catch (BucketAlreadyOwnedByYouException e) {
            // Bucket deja cree par un test precedent : rien a faire.
        }
    }

    /** PDF valide d'une page, genere via PDFBox (la meme librairie que la signature). */
    protected static byte[] pdfMinimal() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }
}

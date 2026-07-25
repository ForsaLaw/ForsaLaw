package com.forsalaw.storage;

import com.forsalaw.AbstractIntegrationTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Base des tests d'integration du stockage objet : ajoute un vrai MinIO au PostgreSQL
 * de {@link AbstractIntegrationTest}.
 *
 * <p>Le conteneur et les proprietes sont declares ici plutot que dans chaque test : les classes
 * filles partagent ainsi le meme conteneur ET le meme contexte Spring (les proprietes injectees
 * etant identiques, Spring reutilise le contexte en cache au lieu d'en redemarrer un).</p>
 */
public abstract class AbstractStorageIntegrationTest extends AbstractIntegrationTest {

    protected static final String BUCKET = "forsalaw-test";

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

package com.forsalaw.storage;

import com.forsalaw.util.HashingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
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
class S3StorageIntegrationTest extends AbstractStorageIntegrationTest {

    @Autowired
    S3StorageService storageService;

    @Autowired
    HashingService hashingService;

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
}

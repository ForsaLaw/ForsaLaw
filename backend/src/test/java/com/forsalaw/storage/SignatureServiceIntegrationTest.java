package com.forsalaw.storage;

import com.forsalaw.documentManagement.entity.ContexteDocument;
import com.forsalaw.documentManagement.entity.DocumentMetadata;
import com.forsalaw.documentManagement.model.DocumentMetadataDTO;
import com.forsalaw.documentManagement.repository.DocumentMetadataRepository;
import com.forsalaw.documentManagement.service.SignatureService;
import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.IdSequenceService;
import com.forsalaw.util.HashingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Couvre le seul chemin qui compose plusieurs operations de stockage : la signature
 * electronique (telechargement -> fichier temporaire -> sceau PDFBox -> depot de la nouvelle
 * cle -> suppression de l'ancienne).
 *
 * <p>Les primitives de {@link S3StorageService} sont testees ailleurs ; ce qui est verifie ici
 * c'est leur enchainement, et surtout que les fichiers temporaires sont nettoyes — y compris
 * quand la signature echoue, sinon le disque du serveur se remplit silencieusement.</p>
 */
class SignatureServiceIntegrationTest extends AbstractStorageIntegrationTest {

    private static final String PREFIXE_TEMPORAIRES = "forsalaw-sign-";

    // Conteneur propre a cette classe : voir l'explication dans AbstractStorageIntegrationTest.
    @Container
    static final MinIOContainer MINIO = new MinIOContainer(IMAGE_MINIO);

    @DynamicPropertySource
    static void proprietesStockage(DynamicPropertyRegistry registry) {
        enregistrerProprietesStockage(registry, MINIO);
    }

    @Autowired
    SignatureService signatureService;

    @Autowired
    S3StorageService storageService;

    @Autowired
    DocumentMetadataRepository documentRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    IdSequenceService idSequenceService;

    @Autowired
    HashingService hashingService;

    @Test
    void signature_deposeLObjetSigne_supprimeLOriginal_etNeLaisseAucunTemporaire() throws IOException {
        User signataire = creerUtilisateur();
        byte[] pdfOriginal = pdfMinimal();
        String hashOriginal = hashingService.calculerHashSha256(new ByteArrayInputStream(pdfOriginal));

        String nomStockage = UUID.randomUUID() + ".pdf";
        String cleOriginale = S3StorageService.DOCUMENTS_PREFIX + nomStockage;
        storageService.upload(cleOriginale, new ByteArrayInputStream(pdfOriginal),
                pdfOriginal.length, "application/pdf");

        DocumentMetadata document = creerDocument(signataire, nomStockage, cleOriginale,
                hashOriginal, pdfOriginal.length);

        long temporairesAvant = compterTemporaires();

        // when
        DocumentMetadataDTO dto = signatureService.signerDocument(document.getId(), signataire.getEmail());

        // then — l'etat metier
        assertThat(dto.isEstSigne()).isTrue();
        DocumentMetadata recharge = documentRepository.findById(document.getId()).orElseThrow();
        assertThat(recharge.isEstSigne()).isTrue();
        assertThat(recharge.getSignataireEmail()).isEqualTo(signataire.getEmail());
        assertThat(recharge.getNomOriginal()).endsWith("_signe.pdf");

        // then — les objets dans MinIO : la nouvelle cle existe, l'ancienne a bien ete supprimee
        String cleSignee = S3StorageService.DOCUMENTS_PREFIX + nomStockage.replace(".pdf", "_signe.pdf");
        assertThat(recharge.getCheminFichier()).isEqualTo(cleSignee);
        assertThat(recharge.getNomStockage()).endsWith("_signe.pdf");
        assertThat(storageService.exists(cleSignee)).isTrue();
        assertThat(storageService.exists(cleOriginale)).isFalse();

        // then — le hash enregistre correspond au contenu reellement stocke, et le sceau a
        // bien modifie le PDF (sinon la verification d'integrite validerait un document non scelle)
        byte[] pdfSigne;
        try (InputStream in = storageService.openStream(cleSignee)) {
            pdfSigne = in.readAllBytes();
        }
        assertThat(hashingService.calculerHashSha256(new ByteArrayInputStream(pdfSigne)))
                .isEqualTo(recharge.getHashApresSignature())
                .isNotEqualTo(hashOriginal);
        assertThat(recharge.getTailleFichier()).isEqualTo(pdfSigne.length);

        // then — aucun fichier temporaire laisse derriere
        assertThat(compterTemporaires()).isEqualTo(temporairesAvant);
    }

    @Test
    void signature_dUnContenuIllisible_echoue_maisNeLaisseAucunTemporaire() throws IOException {
        User signataire = creerUtilisateur();
        // Contenu qui n'est pas un PDF : PDFBox echouera APRES la creation des temporaires,
        // ce qui exerce precisement le bloc finally de nettoyage.
        byte[] contenuCorrompu = "ceci n'est pas un PDF".getBytes(StandardCharsets.UTF_8);

        String nomStockage = UUID.randomUUID() + ".pdf";
        String cle = S3StorageService.DOCUMENTS_PREFIX + nomStockage;
        storageService.upload(cle, new ByteArrayInputStream(contenuCorrompu),
                contenuCorrompu.length, "application/pdf");

        DocumentMetadata document = creerDocument(signataire, nomStockage, cle,
                hashingService.calculerHashSha256(new ByteArrayInputStream(contenuCorrompu)),
                contenuCorrompu.length);

        long temporairesAvant = compterTemporaires();

        assertThatThrownBy(() -> signatureService.signerDocument(document.getId(), signataire.getEmail()))
                .hasMessageContaining("Signature PDF");

        assertThat(compterTemporaires()).isEqualTo(temporairesAvant);
        // L'objet source ne doit pas avoir ete supprime par une signature qui a echoue.
        assertThat(storageService.exists(cle)).isTrue();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private User creerUtilisateur() {
        User user = new User();
        user.setId(idSequenceService.generateNextId("USR"));
        user.setNom("Ben Salah");
        user.setPrenom("Amine");
        user.setEmail("signataire-" + UUID.randomUUID() + "@forsalaw.test");
        user.setMotDePasse("$2a$10$notarealhashusedonlyfortests000000000000000000000000");
        user.setRoleUser(RoleUser.client);
        return userRepository.save(user);
    }

    private DocumentMetadata creerDocument(User deposeur, String nomStockage, String cle,
                                           String hash, long taille) {
        DocumentMetadata document = new DocumentMetadata();
        document.setId(idSequenceService.generateNextId("DOC"));
        document.setDeposeur(deposeur);
        document.setNomOriginal("contrat.pdf");
        document.setNomStockage(nomStockage);
        document.setCheminFichier(cle);
        document.setTypeContenu("application/pdf");
        document.setTailleFichier(taille);
        document.setHashSha256(hash);
        document.setContexteType(ContexteDocument.GENERAL);
        document.setSupprime(false);
        return documentRepository.save(document);
    }

    /** Compte les temporaires de signature restants dans le repertoire temporaire du systeme. */
    private long compterTemporaires() throws IOException {
        Path repertoireTemp = Paths.get(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> fichiers = Files.list(repertoireTemp)) {
            return fichiers.filter(p -> p.getFileName().toString().startsWith(PREFIXE_TEMPORAIRES))
                    .count();
        }
    }
}

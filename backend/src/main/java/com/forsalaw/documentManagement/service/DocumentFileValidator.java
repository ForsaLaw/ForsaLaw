package com.forsalaw.documentManagement.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Valide les fichiers déposés dans le coffre-fort numérique :
 * <ul>
 *     <li>extension de fichier sur liste blanche stricte ;</li>
 *     <li>vrai type MIME détecté sur le contenu réel (Apache Tika), et non l'en-tête
 *         {@code Content-Type} fourni par le client (facilement falsifiable).</li>
 * </ul>
 * Un fichier dont le contenu ne correspond pas à son extension est rejeté
 * (ex. un {@code .jsp} renommé en {@code .pdf}).
 */
@Component
public class DocumentFileValidator {

    /** Extension autorisée -> types MIME réels acceptés (détectés par Tika sur le contenu). */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "pdf", Set.of("application/pdf"),
            "png", Set.of("image/png"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            // Les OOXML (docx) sont des archives ZIP ; Tika-core les détecte comme x-tika-ooxml,
            // et retombe sur application/zip si le marqueur interne n'est pas trouvé.
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml",
                    "application/zip")
    );

    private static final String EXTENSIONS_LISIBLES = "pdf, png, jpg, jpeg, docx";

    private final Tika tika = new Tika();

    /**
     * Rejet de validation. Étend {@link IllegalArgumentException} pour être traité en HTTP 400
     * par le gestionnaire global existant ({@code AuthExceptionHandler}).
     */
    public static class InvalidFileException extends IllegalArgumentException {
        public InvalidFileException(String message) {
            super(message);
        }
    }

    /**
     * Valide extension + vrai type MIME.
     *
     * @return l'extension validée en minuscules, préfixée d'un point (ex. {@code ".pdf"}),
     *         à utiliser pour le nom de stockage.
     * @throws InvalidFileException si le fichier est vide, l'extension non autorisée,
     *         ou le contenu ne correspond pas à l'extension.
     */
    public String validateAndResolveExtension(MultipartFile fichier) throws IOException {
        if (fichier == null || fichier.isEmpty()) {
            throw new InvalidFileException("Fichier vide ou absent.");
        }

        String extension = extensionOf(fichier.getOriginalFilename());
        Set<String> typesAutorises = ALLOWED.get(extension);
        if (typesAutorises == null) {
            throw new InvalidFileException("Extension non autorisée. Types acceptés : " + EXTENSIONS_LISIBLES + ".");
        }

        String typeDetecte;
        try (InputStream in = fichier.getInputStream()) {
            // Détection sur le contenu uniquement (aucun indice tiré du nom de fichier fourni par le client).
            typeDetecte = tika.detect(in);
        }

        if (typeDetecte == null || !typesAutorises.contains(typeDetecte.toLowerCase(Locale.ROOT))) {
            throw new InvalidFileException(
                    "Le contenu du fichier ne correspond pas à l'extension ." + extension
                            + " (type détecté : " + typeDetecte + ").");
        }

        return "." + extension;
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
    }
}

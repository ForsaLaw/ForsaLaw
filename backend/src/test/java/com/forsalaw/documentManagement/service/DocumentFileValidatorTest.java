package com.forsalaw.documentManagement.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFileValidatorTest {

    private final DocumentFileValidator validator = new DocumentFileValidator();

    @Test
    void validPdf_isAccepted() throws Exception {
        // Contenu commencant par la signature magique %PDF- : Tika detecte application/pdf.
        byte[] pdf = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("fichier", "contrat.pdf", "application/pdf", pdf);

        assertThat(validator.validateAndResolveExtension(file)).isEqualTo(".pdf");
    }

    @Test
    void jspRenamedAsPdf_isRejected() {
        // Faux .pdf : le contenu reel est du JSP/HTML. L'en-tete Content-Type ment (application/pdf),
        // mais Tika inspecte les octets et ne detecte PAS application/pdf => rejet.
        byte[] jsp = "<%@ page import=\"java.util.*\" %>\n<html><body>pwned</body></html>"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("fichier", "evil.pdf", "application/pdf", jsp);

        assertThatThrownBy(() -> validator.validateAndResolveExtension(file))
                .isInstanceOf(DocumentFileValidator.InvalidFileException.class);
    }
}

package com.forsalaw.ragManagement.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extraction de la couche texte d'un PDF, avec refus explicite des documents numerises.
 */
@Component
@Slf4j
public class PdfTextExtractor {

    /**
     * En dessous de ce nombre de caracteres par page, on considere qu'il n'y a pas de couche
     * texte : un PDF scanne rend zero caractere, et une page de garde quasi vide ne doit pas
     * suffire a faire passer un document entierement numerise pour exploitable.
     */
    @Value("${forsalaw.rag.ingestion.min-chars-per-page:80}")
    private int minCaracteresParPage;

    public String extraire(InputStream flux, String nomSource) throws IOException {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(flux))) {
            int pages = document.getNumberOfPages();

            PDFTextStripper stripper = new PDFTextStripper();
            // L'ordre de position est indispensable en arabe : sans lui, le texte bidirectionnel
            // ressort dans l'ordre interne du PDF, souvent illisible.
            stripper.setSortByPosition(true);
            String texte = stripper.getText(document);

            String utile = texte == null ? "" : texte.strip();
            int seuil = Math.max(1, pages) * minCaracteresParPage;

            if (utile.length() < seuil) {
                throw new ScannedPdfException(String.format(
                        "Le document « %s » ne contient pas de texte exploitable (%d caractere(s) "
                                + "pour %d page(s)). Il s'agit vraisemblablement d'un PDF numerise : "
                                + "une reconnaissance optique arabe/francais est necessaire, elle "
                                + "n'est pas encore disponible. Aucun chunk n'a ete cree.",
                        nomSource, utile.length(), pages));
            }

            log.debug("Texte extrait de « {} » : {} caracteres sur {} page(s).",
                    nomSource, utile.length(), pages);
            return utile;
        }
    }
}

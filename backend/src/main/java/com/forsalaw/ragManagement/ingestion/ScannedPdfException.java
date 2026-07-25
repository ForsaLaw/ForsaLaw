package com.forsalaw.ragManagement.ingestion;

/**
 * Le PDF ne contient pas de couche texte exploitable : c'est une image numerisee.
 *
 * <p>Ce cas doit echouer BRUYAMMENT. PDFBox renvoie une chaine vide sur un document scanne,
 * et sans ce garde-fou l'ingestion signalerait un succes en n'inserant aucun chunk — un
 * bulletin de jurisprudence disparaitrait silencieusement du corpus, et personne ne s'en
 * apercevrait avant qu'une recherche ne renvoie rien.</p>
 *
 * <p>La majorite des bulletins CEJJ (niveau 2) sont dans ce cas : ils exigent une chaine OCR
 * arabe/francais, prevue comme une etape distincte.</p>
 */
public class ScannedPdfException extends RuntimeException {

    public ScannedPdfException(String message) {
        super(message);
    }
}

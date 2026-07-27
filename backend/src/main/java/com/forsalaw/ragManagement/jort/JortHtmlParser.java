package com.forsalaw.ragManagement.jort;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyse du HTML de l'IORT — TOUS les selecteurs CSS du projet sont regroupes ici.
 *
 * <p><b>Selecteurs a calibrer avant activation.</b> Ils n'ont pas pu etre verifies contre le
 * site reel : {@code iort.gov.tn} refuse les connexions depuis l'environnement de
 * developpement utilise pour ecrire ce code. Ils sont donc une hypothese de structure, pas
 * une observation. Ils sont configurables ({@code forsalaw.rag.jort.selector.*}) precisement
 * pour pouvoir etre corriges sans recompiler.</p>
 *
 * <p>Tout le reste de la chaine (planification, verrou, limitation de debit, ingestion) est
 * independant de cette classe : une evolution du site n'impacte que ce fichier.</p>
 */
@Component
@Slf4j
public class JortHtmlParser {

    @Value("${forsalaw.rag.jort.selector.issue-link:a[href*=jort]}")
    private String selecteurLienNumero;

    @Value("${forsalaw.rag.jort.selector.issue-content:.contenu, .content, article, main}")
    private String selecteurContenu;

    /**
     * Un numero du JORT repere sur la page de listing.
     *
     * @param publicationDate date de parution, {@code null} si elle n'a pas pu etre lue.
     *                        Ne JAMAIS y mettre la date du jour en remplacement : le corpus
     *                        couvre 1957-2026 et cette date pilote la distinction
     *                        ACTIVE / SUPERSEDED. Une date absente est exploitable ; une
     *                        date fausse contamine le versionnement.
     */
    public record NumeroJort(String reference, String url, LocalDate publicationDate) {}

    /** Dates rencontrees dans les libelles de numeros : 12/03/2024, 12-03-2024, 2024. */
    private static final Pattern DATE_LIBELLE = Pattern.compile(
            "(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})|\\b(19\\d{2}|20\\d{2})\\b");

    /**
     * Extrait les numeros listes sur une page d'index.
     * Renvoie une liste vide plutot que d'echouer : un changement de structure ne doit pas
     * faire tomber la tache planifiee, mais etre visible dans les journaux.
     */
    public List<NumeroJort> extraireNumeros(String html, String urlBase) {
        List<NumeroJort> numeros = new ArrayList<>();
        try {
            Document document = Jsoup.parse(html, urlBase);
            for (Element lien : document.select(selecteurLienNumero)) {
                String url = lien.absUrl("href");
                String libelle = lien.text().trim();
                if (url.isEmpty() || libelle.isEmpty()) {
                    continue;
                }
                numeros.add(new NumeroJort(libelle, url, lireDate(libelle)));
            }
        } catch (RuntimeException e) {
            log.error("Analyse de la page d'index JORT impossible (structure du site modifiee ?).", e);
        }

        if (numeros.isEmpty()) {
            log.warn("Aucun numero JORT trouve avec le selecteur « {} ». "
                    + "Le site a probablement change : ajuster forsalaw.rag.jort.selector.issue-link.",
                    selecteurLienNumero);
        }
        return numeros;
    }

    /**
     * Lit une date de parution dans le libelle du lien.
     * Renvoie {@code null} plutot qu'une approximation : mieux vaut une date absente qu'une
     * date inventee dans une colonne qui pilote le versionnement.
     */
    LocalDate lireDate(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            return null;
        }
        Matcher m = DATE_LIBELLE.matcher(libelle);
        if (!m.find()) {
            return null;
        }
        try {
            if (m.group(3) != null) {
                return LocalDate.of(Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            }
            // Annee seule : on ne fabrique pas un jour et un mois, on renonce.
            return null;
        } catch (RuntimeException e) {
            log.debug("Date illisible dans le libelle « {} ».", libelle);
            return null;
        }
    }

    /** Extrait le texte utile d'une page de numero. */
    public String extraireTexte(String html, String url) {
        try {
            Document document = Jsoup.parse(html, url);
            Element contenu = document.selectFirst(selecteurContenu);
            // Repli sur le corps entier : mieux vaut du texte bruite que rien du tout, le
            // decoupage par article filtrera l'essentiel.
            String texte = (contenu != null ? contenu : document.body()).wholeText();
            return texte == null ? "" : texte.strip();
        } catch (RuntimeException e) {
            log.error("Analyse du numero JORT « {} » impossible.", url, e);
            return "";
        }
    }
}

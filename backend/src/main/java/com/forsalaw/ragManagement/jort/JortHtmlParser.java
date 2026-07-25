package com.forsalaw.ragManagement.jort;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    /** Un numero du JORT repere sur la page de listing. */
    public record NumeroJort(String reference, String url) {}

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
                numeros.add(new NumeroJort(libelle, url));
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

package com.forsalaw.ragManagement.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detection des abrogations dans un texte ingere.
 *
 * <p><b>Ce service ne modifie JAMAIS le statut d'un article.</b> Il enregistre une proposition
 * en attente, qu'un administrateur doit confirmer.</p>
 *
 * <p>Pourquoi : la detection repose sur des tournures de redaction (« يلغى وتعوض أحكام
 * الفصل… », « sont abrogees et remplacees les dispositions de l'article… »). Un faux positif
 * marquerait du droit en vigueur comme abroge, un faux negatif laisserait l'assistant citer
 * un texte abroge comme applicable. Les deux erreurs sont graves et aucune regex ne les evite
 * de maniere fiable ; la decision revient donc a un humain.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupersedeProposalService {

    /**
     * Formules d'abrogation, arabe et francais. Le numero d'article capture est celui de
     * l'article ABROGE.
     */
    private static final Pattern ABROGATION = Pattern.compile(
            "(?:يلغى\\s+وتعوض\\s+أحكام\\s+الفصل\\s*([0-9\\u0660-\\u0669]+)"
                    + "|تلغى\\s+أحكام\\s+الفصل\\s*([0-9\\u0660-\\u0669]+)"
                    + "|ينقح\\s+الفصل\\s*([0-9\\u0660-\\u0669]+)"
                    + "|(?:sont\\s+)?abrog[ée]{1,2}s?\\s+(?:et\\s+remplac[ée]{1,2}s?\\s+)?"
                    + "(?:les\\s+dispositions\\s+de\\s+)?l'article\\s*([0-9]+)"
                    + "|l'article\\s*([0-9]+)\\s+est\\s+abrog[ée]{1,2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final JdbcTemplate jdbcTemplate;

    /**
     * Analyse un texte et enregistre une proposition par article vise.
     *
     * @return le nombre de propositions enregistrees
     */
    @Transactional
    public int detecterEtProposer(String texte, String codeName, LocalDate effectiveDate,
                                  String sourceReference) {
        if (texte == null || texte.isBlank()) {
            return 0;
        }

        Matcher matcher = ABROGATION.matcher(texte);
        Set<String> articlesVises = new LinkedHashSet<>();
        Set<String> extraits = new LinkedHashSet<>();

        while (matcher.find()) {
            String numero = premierGroupeNonNul(matcher);
            if (numero == null) {
                continue;
            }
            articlesVises.add(normaliserChiffres(numero));
            // On conserve le passage declencheur : l'administrateur doit pouvoir juger sur
            // pieces, sans rouvrir le PDF d'origine.
            int debut = Math.max(0, matcher.start() - 120);
            int fin = Math.min(texte.length(), matcher.end() + 120);
            extraits.add(texte.substring(debut, fin).replaceAll("\\s+", " ").trim());
        }

        if (articlesVises.isEmpty()) {
            return 0;
        }

        int enregistrees = 0;
        for (String article : articlesVises) {
            enregistrees += proposeSupersedeAction(codeName, article, effectiveDate,
                    sourceReference, String.join(" […] ", extraits));
        }

        log.warn("{} proposition(s) d'abrogation detectee(s) dans « {} » pour le code « {} ». "
                        + "AUCUN article n'a ete modifie : confirmation administrateur requise.",
                enregistrees, sourceReference, codeName);
        return enregistrees;
    }

    /**
     * Enregistre une proposition d'abrogation en attente.
     *
     * <p>N'applique rien : {@code legal_document_chunk.status} reste inchange tant qu'un
     * administrateur n'a pas confirme.</p>
     */
    @Transactional
    public int proposeSupersedeAction(String codeName, String articleReference,
                                      LocalDate effectiveDate, String sourceReference,
                                      String extrait) {
        // Une meme abrogation peut etre redetectee lors d'une reingestion : on ne cree pas
        // de doublon en attente.
        Long dejaEnAttente = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM rag_supersede_proposal
                 WHERE code_name = ? AND article_reference = ? AND status = 'PENDING'
                """, Long.class, codeName, articleReference);

        if (dejaEnAttente != null && dejaEnAttente > 0) {
            return 0;
        }

        return jdbcTemplate.update("""
                INSERT INTO rag_supersede_proposal
                    (code_name, article_reference, effective_date, source_reference, matched_text, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING')
                """,
                codeName,
                articleReference,
                effectiveDate == null ? null : Date.valueOf(effectiveDate),
                sourceReference,
                extrait);
    }

    private String premierGroupeNonNul(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null) {
                return matcher.group(i);
            }
        }
        return null;
    }

    private String normaliserChiffres(String numero) {
        StringBuilder sb = new StringBuilder(numero.length());
        for (char c : numero.trim().toCharArray()) {
            sb.append(c >= '٠' && c <= '٩' ? (char) ('0' + (c - '٠')) : c);
        }
        return sb.toString();
    }
}

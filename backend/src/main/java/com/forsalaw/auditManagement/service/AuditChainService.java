package com.forsalaw.auditManagement.service;

import com.forsalaw.auditManagement.entity.AuditLog;
import com.forsalaw.auditManagement.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Chainage cryptographique du journal d'audit.
 *
 * <p>Chaque ligne porte l'empreinte de la precedente : modifier, supprimer ou inserer une
 * entree au milieu du journal casse toutes les empreintes suivantes. C'est ce qui rend
 * l'alteration DETECTABLE, la ou le trigger de V8 se contente de l'empecher via SQL.</p>
 *
 * <p><b>Point d'entree unique en ecriture :</b> {@link #append(AuditLog)}. Toute autre facon
 * d'inserer dans {@code audit_log} produirait une ligne sans empreinte, rejetee par la
 * contrainte NOT NULL posee en V10.</p>
 *
 * <p><b>Concurrence :</b> les appelants obtiennent leur identifiant via
 * {@code IdSequenceService.generateNextId("ADT")}, qui verrouille la ligne de sequence en
 * PESSIMISTIC_WRITE jusqu'au commit. Deux ecritures concurrentes sont donc serialisees et ne
 * peuvent pas lire la meme tete de chaine.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditChainService {

    /** Empreinte conventionnelle precedant la toute premiere ligne du journal. */
    public static final String GENESIS_HASH = "0".repeat(64);

    /**
     * Horodatage a la microseconde : la colonne est un timestamp(6), et le format doit etre
     * IDENTIQUE cote SQL (to_char ... 'US') pour que le remplissage initial de V10 et le calcul
     * Java produisent la meme empreinte.
     */
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    private final AuditLogRepository auditLogRepository;

    /**
     * Horodate, chaine et enregistre une entree d'audit.
     * L'identifiant doit deja etre renseigne par l'appelant.
     */
    @Transactional
    public AuditLog append(AuditLog entry) {
        // La colonne est un timestamp(6) : PostgreSQL arrondirait les nanosecondes, et
        // l'empreinte calculee ici ne correspondrait plus a la valeur relue. On tronque donc
        // AVANT de hacher.
        LocalDateTime horodatage = (entry.getCreatedAt() == null ? LocalDateTime.now() : entry.getCreatedAt())
                .truncatedTo(ChronoUnit.MICROS);
        entry.setCreatedAt(horodatage);

        String prevHash = auditLogRepository.findChainHead().orElse(GENESIS_HASH);
        String canonical = canonical(
                entry.getId(),
                entry.getModuleName(),
                entry.getAction(),
                entry.getMethod(),
                entry.getEndpoint(),
                entry.getResourceId(),
                entry.getHttpStatus(),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.getDetails(),
                entry.getActor() != null ? entry.getActor().getId() : null,
                horodatage
        );

        entry.setPrevHash(prevHash);
        entry.setRowHash(hash(canonical, prevHash));
        return auditLogRepository.save(entry);
    }

    /**
     * Representation canonique d'une ligne.
     *
     * <p>DOIT rester alignee sur l'expression SQL de V10__audit_log_hash_chain.sql : meme ordre
     * de champs, meme separateur, meme format d'horodatage. Toute divergence ferait echouer la
     * verification sur les lignes anterieures a la migration.</p>
     *
     * <p>Limite connue : le separateur '|' peut apparaitre dans {@code details}. Un contenu
     * fabrique pourrait donc theoriquement deplacer une frontiere de champ a empreinte
     * constante. Le scenario reel vise ici est la modification de la base APRES coup, que ce
     * chainage detecte ; la parite exacte avec le SQL a ete jugee plus importante qu'un
     * echappement supplementaire, qu'il faudrait reproduire a l'identique des deux cotes.</p>
     */
    public String canonical(String id, String moduleName, String action, String method,
                            String endpoint, String resourceId, Integer httpStatus,
                            String ipAddress, String userAgent, String details,
                            String actorUserId, LocalDateTime createdAt) {
        return String.join("|",
                nullToEmpty(id),
                nullToEmpty(moduleName),
                nullToEmpty(action),
                nullToEmpty(method),
                nullToEmpty(endpoint),
                nullToEmpty(resourceId),
                httpStatus == null ? "" : httpStatus.toString(),
                nullToEmpty(ipAddress),
                nullToEmpty(userAgent),
                nullToEmpty(details),
                nullToEmpty(actorUserId),
                createdAt.format(TIMESTAMP_FORMAT));
    }

    /** SHA-256 hexadecimal de {@code canonical|prevHash}, encode en UTF-8. */
    public String hash(String canonical, String prevHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] empreinte = digest.digest((canonical + "|" + prevHash).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : empreinte) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithme SHA-256 non disponible.", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

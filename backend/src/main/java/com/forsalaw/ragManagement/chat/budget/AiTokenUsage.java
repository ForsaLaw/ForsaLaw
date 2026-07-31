package com.forsalaw.ragManagement.chat.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Consommation de jetons IA, une ligne par requete.
 *
 * <p>Table dediee plutot qu'un compteur sur {@code users} : un compteur ne permettrait ni les
 * tableaux de bord de cout prevus en Phase 11, ni d'identifier apres coup le compte ayant
 * epuise le budget.</p>
 *
 * <p><b>Cycle de vie.</b> La ligne est creee AVANT la generation avec un depot forfaitaire
 * ({@link Statut#DEPOT}), puis ajustee au reel a l'arrivee de la trame {@code done}
 * ({@link Statut#REGLE}). Les jetons ne sont connus qu'a la toute fin du flux : une requete
 * abandonnee en cours de route n'est donc jamais ajustee et conserve son depot. C'est
 * volontaire — sans ce depot, une boucle d'abandon consommerait du calcul sans etre
 * decomptee.</p>
 */
@Entity
@Table(
        name = "ai_token_usage",
        indexes = {
                @Index(name = "idx_ai_token_usage_user_date", columnList = "user_id, created_at"),
                @Index(name = "idx_ai_token_usage_date", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiTokenUsage {

    public enum Statut {
        /** Depot preleve, generation pas encore terminee (ou jamais terminee). */
        DEPOT,
        /** Ajuste au nombre reel de jetons rapporte par le modele. */
        REGLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 20)
    private String userId;

    @Column(name = "tokens_invite", nullable = false)
    private int tokensInvite;

    @Column(name = "tokens_reponse", nullable = false)
    private int tokensReponse;

    /** Jetons decomptes du budget : le depot tant que DEPOT, le reel une fois REGLE. */
    @Column(name = "tokens_total", nullable = false)
    private int tokensTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 16)
    private Statut statut;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    /** Ouvre une consommation au montant du depot, avant toute generation. */
    public static AiTokenUsage depot(String userId, int jetonsDeposes) {
        AiTokenUsage usage = new AiTokenUsage();
        usage.userId = userId;
        usage.tokensTotal = jetonsDeposes;
        usage.statut = Statut.DEPOT;
        usage.createdAt = LocalDateTime.now();
        return usage;
    }

    /** Ajuste la ligne au nombre reel de jetons rapporte par le modele. */
    public void regler(int jetonsInvite, int jetonsReponse) {
        this.tokensInvite = jetonsInvite;
        this.tokensReponse = jetonsReponse;
        this.tokensTotal = jetonsInvite + jetonsReponse;
        this.statut = Statut.REGLE;
        this.settledAt = LocalDateTime.now();
    }
}

package com.forsalaw.ragManagement.chat.budget;

import com.forsalaw.userManagement.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Coupe-circuit financier de {@code /api/ai/chat} : plafond quotidien par utilisateur et
 * plafond mensuel global.
 *
 * <p><b>Le decompte est necessairement a posteriori.</b> Ollama ne rapporte
 * {@code prompt_eval_count} / {@code eval_count} que dans la trame finale {@code done} : le
 * cout d'une requete n'est donc connu qu'une fois la generation terminee. D'ou le fonctionne-
 * ment en deux temps :</p>
 * <ol>
 *   <li><b>Depot</b> — un forfait est decompte AVANT la generation ;</li>
 *   <li><b>Reglement</b> — la ligne est ajustee au reel a la fin du flux.</li>
 * </ol>
 *
 * <p>Deux consequences assumees :</p>
 * <ul>
 *   <li>Un utilisateur juste sous son plafond peut lancer UNE derniere generation complete et
 *       le depasser : le controle porte sur la consommation deja enregistree, pas sur le cout
 *       de la requete en cours, qui est inconnu a cet instant.</li>
 *   <li>Un client qui se deconnecte en cours de flux ne recoit jamais la trame {@code done} :
 *       sa ligne reste au depot. C'est deliberement non rembourse — sans quoi une boucle
 *       d'abandon consommerait du calcul sans jamais etre decomptee.</li>
 * </ul>
 */
@Service
@Slf4j
public class AiTokenBudgetService {

    private final AiTokenUsageRepository usageRepository;
    private final UserRepository userRepository;
    private final int depotJetons;
    private final long plafondMensuelGlobal;

    public AiTokenBudgetService(
            AiTokenUsageRepository usageRepository,
            UserRepository userRepository,
            @Value("${forsalaw.ai.budget.deposit-tokens:1000}") int depotJetons,
            @Value("${forsalaw.ai.budget.monthly-global-tokens:5000000}") long plafondMensuelGlobal
    ) {
        this.usageRepository = usageRepository;
        this.userRepository = userRepository;
        this.depotJetons = depotJetons;
        this.plafondMensuelGlobal = plafondMensuelGlobal;
    }

    /** Budget epuise : porte le message a renvoyer tel quel a l'utilisateur. */
    public static class BudgetEpuiseException extends RuntimeException {
        public BudgetEpuiseException(String message) {
            super(message);
        }
    }

    /**
     * Verifie les deux plafonds puis ouvre une ligne de consommation au montant du depot.
     *
     * @return l'identifiant de la ligne, a passer a {@link #regler(Long, int, int)}.
     * @throws BudgetEpuiseException si l'un des plafonds est deja atteint.
     */
    @Transactional
    public Long reserver(String email) {
        var utilisateur = userRepository.findByEmail(email)
                .orElseThrow(() -> new BudgetEpuiseException("Compte introuvable."));

        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        long consommeAujourdhui = usageRepository.totalUtilisateurDepuis(utilisateur.getId(), debutJour);
        if (consommeAujourdhui >= utilisateur.getDailyTokenBudget()) {
            log.warn("Budget IA quotidien epuise pour l'utilisateur {} ({} / {} jetons).",
                    utilisateur.getId(), consommeAujourdhui, utilisateur.getDailyTokenBudget());
            throw new BudgetEpuiseException(
                    "Votre quota quotidien d'assistance IA est epuise. Il sera renouvele demain.");
        }

        LocalDateTime debutMois = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long consommeCeMois = usageRepository.totalGlobalDepuis(debutMois);
        if (consommeCeMois >= plafondMensuelGlobal) {
            log.error("Plafond IA mensuel GLOBAL atteint ({} / {} jetons) : service suspendu.",
                    consommeCeMois, plafondMensuelGlobal);
            throw new BudgetEpuiseException(
                    "Le service d'assistance IA est momentanement suspendu. Veuillez reessayer plus tard.");
        }

        AiTokenUsage depot = usageRepository.save(AiTokenUsage.depot(utilisateur.getId(), depotJetons));
        return depot.getId();
    }

    /**
     * Ajuste la ligne au cout reel. Sans appel (client deconnecte), le depot reste acquis.
     *
     * <p>Ne propage aucune exception : la reponse a deja ete servie a l'utilisateur, echouer
     * ici ne ferait que masquer ce succes derriere une erreur de comptabilite.</p>
     */
    @Transactional
    public void regler(Long idUsage, int jetonsInvite, int jetonsReponse) {
        if (idUsage == null) {
            return;
        }
        try {
            usageRepository.findById(idUsage).ifPresent(usage -> {
                usage.regler(jetonsInvite, jetonsReponse);
                usageRepository.save(usage);
            });
        } catch (RuntimeException e) {
            log.error("Impossible d'ajuster la consommation de jetons {} : le depot reste acquis.",
                    idUsage, e);
        }
    }
}

package com.forsalaw.ragManagement.chat.budget;

import com.forsalaw.userManagement.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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

    /**
     * Compteurs de supervision. La consommation etait deja enregistree ligne a ligne dans
     * {@code ai_token_usage} mais rien ne la restituait : constater une derive de cout, ou une
     * serie de refus pour budget epuise, exigeait d'interroger la base a la main. Ces compteurs
     * rendent les deux visibles dans /actuator/prometheus.
     */
    private final Counter jetonsInvite;
    private final Counter jetonsReponse;
    private final Counter refusQuotaUtilisateur;
    private final Counter refusPlafondGlobal;

    public AiTokenBudgetService(
            AiTokenUsageRepository usageRepository,
            UserRepository userRepository,
            MeterRegistry registry,
            @Value("${forsalaw.ai.budget.deposit-tokens:1000}") int depotJetons,
            @Value("${forsalaw.ai.budget.monthly-global-tokens:5000000}") long plafondMensuelGlobal
    ) {
        this.usageRepository = usageRepository;
        this.userRepository = userRepository;
        this.depotJetons = depotJetons;
        this.plafondMensuelGlobal = plafondMensuelGlobal;

        this.jetonsInvite = Counter.builder("forsalaw.ai.tokens")
                .description("Jetons consommes par l'assistant juridique")
                .tag("type", "invite")
                .register(registry);
        this.jetonsReponse = Counter.builder("forsalaw.ai.tokens")
                .description("Jetons consommes par l'assistant juridique")
                .tag("type", "reponse")
                .register(registry);
        // Deux compteurs distincts, car les deux refus n'appellent pas la meme reaction :
        // un quota utilisateur atteint est un usage nominal, le plafond global atteint est
        // une interruption de service pour TOUT LE MONDE.
        this.refusQuotaUtilisateur = Counter.builder("forsalaw.ai.budget.refus")
                .description("Demandes refusees faute de budget")
                .tag("portee", "utilisateur")
                .register(registry);
        this.refusPlafondGlobal = Counter.builder("forsalaw.ai.budget.refus")
                .description("Demandes refusees faute de budget")
                .tag("portee", "global")
                .register(registry);
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
            refusQuotaUtilisateur.increment();
            throw new BudgetEpuiseException(
                    "Votre quota quotidien d'assistance IA est epuise. Il sera renouvele demain.");
        }

        LocalDateTime debutMois = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long consommeCeMois = usageRepository.totalGlobalDepuis(debutMois);
        if (consommeCeMois >= plafondMensuelGlobal) {
            log.error("Plafond IA mensuel GLOBAL atteint ({} / {} jetons) : service suspendu.",
                    consommeCeMois, plafondMensuelGlobal);
            refusPlafondGlobal.increment();
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
            this.jetonsInvite.increment(jetonsInvite);
            this.jetonsReponse.increment(jetonsReponse);
        } catch (RuntimeException e) {
            log.error("Impossible d'ajuster la consommation de jetons {} : le depot reste acquis.",
                    idUsage, e);
        }
    }
}

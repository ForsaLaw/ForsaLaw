package com.forsalaw.sosManagement.service;

import com.forsalaw.sosManagement.entity.SosArrest;
import com.forsalaw.sosManagement.entity.StatutDispatch;
import com.forsalaw.sosManagement.entity.StatutPaiement;
import com.forsalaw.sosManagement.model.SosArrestRequest;
import com.forsalaw.sosManagement.repository.SosArrestRepository;
import com.forsalaw.userManagement.repository.UserRepository;
import com.forsalaw.userManagement.service.IdSequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SosArrestServiceTest {

    private static final String ID = "2026-SOS-00001";

    @Mock SosArrestRepository sosArrestRepository;
    @Mock UserRepository userRepository;
    @Mock IdSequenceService idSequenceService;
    @Mock PaymentService paymentService;
    @Mock DispatchService dispatchService;

    @InjectMocks SosArrestService service;

    private SosArrest signalement;

    @BeforeEach
    void preparer() {
        signalement = new SosArrest();
        signalement.setId(ID);
        signalement.setNomDetenu("Ali Ben Salah");
        signalement.setLieuArrestation("Poste de police, Ariana");
        signalement.setDateHeureArrestation(LocalDateTime.now());
        signalement.setContactUrgence("+216 20 000 000");
    }

    private SosArrestRequest requete() {
        SosArrestRequest r = new SosArrestRequest();
        r.setNomDetenu("Ali Ben Salah");
        r.setLieuArrestation("Poste de police, Ariana");
        r.setDateHeureArrestation(LocalDateTime.now());
        r.setContactUrgence("+216 20 000 000");
        return r;
    }

    @Test
    void signalementAnonyme_estAccepteEtDeclencheLePaiement() {
        when(idSequenceService.generateNextId("SOS")).thenReturn(ID);
        when(sosArrestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var reponse = service.enregistrer(requete(), null);

        // Aucun compte n'est cherche : exiger une inscription a cet instant ferait perdre
        // l'urgence, un proche signalant presque toujours sans compte.
        verify(userRepository, never()).findByEmail(anyString());
        verify(paymentService).demarrerPaiement(ID);
        assertThat(reponse.id()).isEqualTo(ID);
        assertThat(reponse.statutPaiement()).isEqualTo(StatutPaiement.PENDING);
    }

    @Test
    void paiementConfirme_mobiliseLesAvocatsEtMarqueDispatche() {
        when(sosArrestRepository.findById(ID)).thenReturn(Optional.of(signalement));
        when(dispatchService.mobiliserAvocats(signalement)).thenReturn(true);

        service.confirmerPaiement(ID);

        assertThat(signalement.getStatutPaiement()).isEqualTo(StatutPaiement.PAID);
        assertThat(signalement.getStatutDispatch()).isEqualTo(StatutDispatch.DISPATCHED);
        assertThat(signalement.getPaidAt()).isNotNull();
    }

    @Test
    void mobilisationEchouee_conserveLePaiementMaisMarqueEchec() {
        when(sosArrestRepository.findById(ID)).thenReturn(Optional.of(signalement));
        when(dispatchService.mobiliserAvocats(signalement)).thenReturn(false);

        service.confirmerPaiement(ID);

        // Le paiement a bien eu lieu : c'est la mobilisation qui a echoue. Les distinguer est ce
        // qui permet de reprendre la seule etape defaillante, sans re-encaisser.
        assertThat(signalement.getStatutPaiement()).isEqualTo(StatutPaiement.PAID);
        assertThat(signalement.getStatutDispatch()).isEqualTo(StatutDispatch.FAILED);
    }

    @Test
    void doubleNotificationDePaiement_neRemobilisePas() {
        signalement.marquerPaye();
        signalement.marquerDispatche();
        when(sosArrestRepository.findById(ID)).thenReturn(Optional.of(signalement));

        service.confirmerPaiement(ID);

        // Une passerelle de paiement peut notifier deux fois : la seconde ne doit pas relancer
        // une mobilisation deja effectuee.
        verify(dispatchService, never()).mobiliserAvocats(any());
    }
}

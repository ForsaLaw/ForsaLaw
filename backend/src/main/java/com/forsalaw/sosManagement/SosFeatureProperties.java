package com.forsalaw.sosManagement;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Interrupteur de la prise en charge « SOS Arrestation ».
 *
 * <p><b>Desactive par defaut, et ce defaut est deliberé.</b> Le paiement et la mobilisation des
 * avocats sont encore des bouchons : un signalement affiche « Avocats penalistes mobilises »
 * alors que personne n'a ete prevenu. Promettre a un proche qu'un avocat intervient sur une
 * garde a vue, sans que ce soit vrai, expose a une responsabilite sans commune mesure avec le
 * benefice d'une demonstration.</p>
 *
 * <p>L'activation est donc un geste explicite ({@code SOS_ARREST_ENABLED=true}), a ne poser
 * qu'une fois la passerelle de paiement ET l'envoi de SMS reellement branches.</p>
 */
@Component
@ConfigurationProperties(prefix = "forsalaw.features.sos-arrest")
@Getter
@Setter
public class SosFeatureProperties {

    private boolean enabled = false;
}

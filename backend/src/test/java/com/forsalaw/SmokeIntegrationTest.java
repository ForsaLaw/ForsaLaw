package com.forsalaw;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de fumee : verifie que le contexte applicatif complet demarre contre une vraie
 * base PostgreSQL (Testcontainers). Sert de socle pour les futurs tests d'integration
 * de flux (ex. reservation de rendez-vous / OffsetDateTime en Phase 7).
 */
class SmokeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ApplicationContext applicationContext;

    // Spring declare TOUJOURS les deux beans de broker : celui qui n'est pas configure est
    // remplace par une implementation "no-op". Il faut donc les qualifier par nom, sinon
    // l'injection par type est ambigue.
    @Autowired
    @Qualifier("simpleBrokerMessageHandler")
    AbstractBrokerMessageHandler brokerEnMemoire;

    @Autowired
    @Qualifier("stompBrokerRelayMessageHandler")
    AbstractBrokerMessageHandler brokerRelais;

    @Test
    void contextLoadsAgainstRealPostgres() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isPositive();
    }

    /**
     * Sans propriete de relais, l'application doit rester sur le broker en memoire : c'est ce qui
     * permet de lancer le projet (et toute la suite de tests) sans RabbitMQ.
     */
    @Test
    void sansConfigurationDeRelais_leBrokerEnMemoireEstUtilise() {
        assertThat(brokerEnMemoire).isInstanceOf(SimpleBrokerMessageHandler.class);
        assertThat(brokerRelais).isNotInstanceOf(StompBrokerRelayMessageHandler.class);
    }
}

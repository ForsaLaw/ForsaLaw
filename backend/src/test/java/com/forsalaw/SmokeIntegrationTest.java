package com.forsalaw;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de fumee : verifie que le contexte applicatif complet demarre contre une vraie
 * base PostgreSQL (Testcontainers). Sert de socle pour les futurs tests d'integration
 * de flux (ex. reservation de rendez-vous / OffsetDateTime en Phase 7).
 */
class SmokeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ApplicationContext applicationContext;

    // Spring declare TOUJOURS les deux methodes @Bean de broker, mais celle qui ne correspond
    // pas a la configuration retourne null (bean nul). D'ou : qualification par nom (l'injection
    // par type serait ambigue) et required=false pour celui qui doit etre absent.
    @Autowired
    @Qualifier("simpleBrokerMessageHandler")
    AbstractBrokerMessageHandler brokerEnMemoire;

    @Autowired(required = false)
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
        // Le relais n'est pas seulement inactif : son bean n'est pas cree du tout.
        assertThat(brokerRelais).isNull();
    }
}

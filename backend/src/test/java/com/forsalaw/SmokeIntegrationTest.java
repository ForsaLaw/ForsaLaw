package com.forsalaw;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    AbstractBrokerMessageHandler brokerMessageHandler;

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
        assertThat(brokerMessageHandler).isInstanceOf(SimpleBrokerMessageHandler.class);
    }
}

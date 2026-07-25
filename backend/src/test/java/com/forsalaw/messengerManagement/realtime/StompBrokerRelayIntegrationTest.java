package com.forsalaw.messengerManagement.realtime;

import com.forsalaw.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie que le relais STOMP externe se connecte reellement a un broker RabbitMQ
 * (Testcontainers, plugin STOMP actif).
 *
 * <p>Ce qui est teste ici, c'est la connexion "systeme" que Spring ouvre vers le broker : c'est
 * elle qui porte tous les envois cote serveur ({@code convertAndSend}). Si elle ne s'etablit pas
 * — mauvais port, plugin STOMP absent, identifiants refuses — l'application demarre quand meme
 * mais la messagerie temps reel est silencieusement morte. D'ou une verification explicite.</p>
 *
 * <p>Le conteneur est declare dans CETTE classe (et non dans une base partagee) : voir
 * l'explication dans {@code AbstractStorageIntegrationTest}.</p>
 *
 * <p><b>Prerequis :</b> un daemon Docker (CI : ubuntu-latest ; en local : Docker Desktop).</p>
 */
class StompBrokerRelayIntegrationTest extends AbstractIntegrationTest {

    private static final int PORT_STOMP = 61613;

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3-management")
            .withPluginsEnabled("rabbitmq_stomp");

    static {
        // Le plugin STOMP n'expose son port que s'il est explicitement publie par le conteneur.
        RABBITMQ.addExposedPort(PORT_STOMP);
    }

    @DynamicPropertySource
    static void proprietesRelais(DynamicPropertyRegistry registry) {
        registry.add("forsalaw.websocket.broker.relay-enabled", () -> true);
        registry.add("forsalaw.websocket.broker.host", RABBITMQ::getHost);
        registry.add("forsalaw.websocket.broker.port", () -> RABBITMQ.getMappedPort(PORT_STOMP));
        registry.add("forsalaw.websocket.broker.client-login", RABBITMQ::getAdminUsername);
        registry.add("forsalaw.websocket.broker.client-passcode", RABBITMQ::getAdminPassword);
        registry.add("forsalaw.websocket.broker.system-login", RABBITMQ::getAdminUsername);
        registry.add("forsalaw.websocket.broker.system-passcode", RABBITMQ::getAdminPassword);
    }

    // Spring declare TOUJOURS les deux methodes @Bean de broker, mais celle qui ne correspond
    // pas a la configuration retourne null (bean nul). D'ou : qualification par nom (l'injection
    // par type serait ambigue) et required=false pour celui qui doit etre absent.
    @Autowired
    @Qualifier("stompBrokerRelayMessageHandler")
    AbstractBrokerMessageHandler brokerRelais;

    @Autowired(required = false)
    @Qualifier("simpleBrokerMessageHandler")
    AbstractBrokerMessageHandler brokerEnMemoire;

    @Test
    void relaisActive_leBrokerExterneEstUtiliseEtJoignable() throws InterruptedException {
        // Le broker en memoire a bien ete remplace par le relais.
        assertThat(brokerRelais).isInstanceOf(StompBrokerRelayMessageHandler.class);
        // Le broker en memoire n'est pas seulement inactif : son bean n'est pas cree du tout.
        assertThat(brokerEnMemoire).isNull();

        StompBrokerRelayMessageHandler relais = (StompBrokerRelayMessageHandler) brokerRelais;
        assertThat(relais.getRelayPort()).isEqualTo(RABBITMQ.getMappedPort(PORT_STOMP));

        // La connexion systeme s'etablit de maniere asynchrone au demarrage : on attend
        // qu'elle soit effective plutot que de supposer qu'elle l'est deja.
        assertThat(attendreBrokerDisponible(relais, Duration.ofSeconds(30)))
                .as("le relais doit etablir sa connexion systeme vers RabbitMQ")
                .isTrue();
    }

    private boolean attendreBrokerDisponible(StompBrokerRelayMessageHandler relais, Duration delaiMax)
            throws InterruptedException {
        Instant limite = Instant.now().plus(delaiMax);
        while (Instant.now().isBefore(limite)) {
            if (relais.isBrokerAvailable()) {
                return true;
            }
            Thread.sleep(250);
        }
        return relais.isBrokerAvailable();
    }
}

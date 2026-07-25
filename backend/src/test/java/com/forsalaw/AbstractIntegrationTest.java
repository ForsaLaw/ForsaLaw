package com.forsalaw;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base pour les tests d'integration : demarre le contexte Spring complet contre une
 * vraie base PostgreSQL (image pgvector, alignee sur la production) fournie par Testcontainers.
 *
 * <p>{@code @ServiceConnection} injecte automatiquement l'URL/identifiants du conteneur dans
 * la datasource (aucune propriete a surcharger).</p>
 *
 * <p><b>Pourquoi un conteneur "singleton" et non {@code @Container} :</b> un champ
 * {@code @Container} statique est ARRETE a la fin de chaque classe de test, alors que Spring
 * MET EN CACHE et REUTILISE un contexte entre plusieurs classes qui partagent la meme
 * configuration. La deuxieme classe repartait donc sur un nouveau conteneur (nouveau port
 * aleatoire) pendant que le pool Hikari du contexte cache pointait toujours sur l'ancien :
 * « Connection to localhost:32769 refused ». Demarre une fois pour toute la JVM via un bloc
 * statique, le conteneur garde un port stable ; Ryuk le supprime a la fin des tests.</p>
 *
 * <p><b>Consequence :</b> la base est PARTAGEE entre les classes de test. Une classe qui
 * depend d'un etat precis doit le preparer elle-meme (cf. le nettoyage de audit_log dans
 * AuditChainIntegrityIntegrationTest).</p>
 *
 * <p>{@code @Testcontainers} reste necessaire : il gere les conteneurs {@code @Container}
 * declares par les classes filles (MinIO, RabbitMQ), qui eux ont bien une portee par classe.</p>
 *
 * <p><b>Prerequis :</b> un daemon Docker doit tourner (CI : runners ubuntu-latest ; en local :
 * Docker Desktop). Sans Docker, ces tests echouent au demarrage du conteneur.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }
}

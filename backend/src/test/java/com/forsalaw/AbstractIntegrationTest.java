package com.forsalaw;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base pour les tests d'integration : demarre le contexte Spring complet contre une
 * vraie base PostgreSQL (image pgvector, alignee sur la production) fournie par Testcontainers.
 *
 * <p>{@code @ServiceConnection} injecte automatiquement l'URL/identifiants du conteneur dans
 * la datasource (aucune propriete a surcharger). Le conteneur {@code static} est partage entre
 * toutes les methodes de test (demarre une fois).</p>
 *
 * <p><b>Prerequis :</b> un daemon Docker doit tourner (CI : runners ubuntu-latest ; en local :
 * Docker Desktop). Sans Docker, ces tests echouent au demarrage du conteneur.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
}

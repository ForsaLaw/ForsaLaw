package com.forsalaw;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de fumee : verifie que le contexte applicatif complet demarre contre une vraie
 * base PostgreSQL (Testcontainers). Sert de socle pour les futurs tests d'integration
 * de flux (ex. reservation de rendez-vous / OffsetDateTime en Phase 7).
 */
class SmokeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void contextLoadsAgainstRealPostgres() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isPositive();
    }
}

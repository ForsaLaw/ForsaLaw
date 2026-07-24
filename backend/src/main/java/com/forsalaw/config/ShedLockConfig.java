package com.forsalaw.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Active ShedLock : verrou distribue base sur la table {@code shedlock} (migration V4).
 * Garantit qu'une tache planifiee ne s'execute que sur une seule instance backend a la fois.
 *
 * <p>{@code usingDbTime()} : ShedLock utilise l'horloge du serveur PostgreSQL (et non celle de la
 * JVM), ce qui evite tout ecart d'horloge entre instances. {@code defaultLockAtMostFor} borne la
 * duree maximale d'un verrou (filet de securite si une instance meurt en le detenant).</p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(jdbcTemplate)
                        .usingDbTime()
                        .build()
        );
    }
}

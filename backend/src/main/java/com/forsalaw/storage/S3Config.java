package com.forsalaw.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Client S3 unique pour toute l'application.
 *
 * Le meme code sert AWS S3 et MinIO : seul {@code endpoint-override} change. MinIO n'accepte pas
 * les URLs "virtual-host" (bucket.host/...), d'ou {@code path-style-access} active par defaut.
 *
 * Credentials : si S3_ACCESS_KEY / S3_SECRET_KEY sont fournis (dev, MinIO) on les utilise
 * explicitement ; sinon on retombe sur la chaine par defaut du SDK (role IAM en production),
 * ce qui evite d'avoir des secrets en clair sur le serveur.
 */
@Configuration
@Slf4j
public class S3Config {

    @Value("${forsalaw.storage.s3.region}")
    private String region;

    @Value("${forsalaw.storage.s3.endpoint-override:}")
    private String endpointOverride;

    @Value("${forsalaw.storage.s3.path-style-access:true}")
    private boolean pathStyleAccess;

    @Value("${forsalaw.storage.s3.access-key:}")
    private String accessKey;

    @Value("${forsalaw.storage.s3.secret-key:}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build());

        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
            log.info("Stockage objet : endpoint personnalise {} (path-style={})", endpointOverride, pathStyleAccess);
        }

        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.info("Stockage objet : credentials via la chaine par defaut du SDK (role IAM attendu).");
        }

        return builder.build();
    }
}

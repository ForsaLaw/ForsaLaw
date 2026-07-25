package com.forsalaw.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

/**
 * Stockage des fichiers dans un bucket objet (S3 ou MinIO).
 *
 * Remplace l'ecriture sur disque local : le disque d'une instance n'est pas partage avec les
 * autres replicas, ce qui rendait tout deploiement multi-instances impossible.
 *
 * Les cles sont prefixees par domaine, ex. {@code documents/<uuid>.pdf}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    /** Prefixe des objets du coffre-fort numerique. */
    public static final String DOCUMENTS_PREFIX = "documents/";

    private final S3Client s3Client;

    @Value("${forsalaw.storage.s3.bucket}")
    private String bucket;

    /**
     * Depose un objet. {@code contentLength} est obligatoire : sans lui le SDK devrait bufferiser
     * tout le flux en memoire pour le calculer.
     */
    public void upload(String key, InputStream data, long contentLength, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .contentLength(contentLength)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(data, contentLength));
            log.debug("Objet depose : {}", key);
        } catch (S3Exception e) {
            throw new StorageException("Echec du depot de l'objet : " + key, e);
        }
    }

    /**
     * Ouvre l'objet en lecture. Le flux retourne reste ouvert : c'est l'appelant (ou Spring, via
     * la Resource renvoyee au client HTTP) qui doit le fermer.
     */
    public S3ObjectResource download(String key) {
        return new S3ObjectResource(openStream(key), key);
    }

    /** Flux brut, pour un usage interne (recalcul de hash, signature PDF...). */
    public ResponseInputStream<GetObjectResponse> openStream(String key) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new StorageException.ObjectNotFound(key, e);
        } catch (S3Exception e) {
            throw new StorageException("Echec de la lecture de l'objet : " + key, e);
        }
    }

    /** Suppression idempotente : S3 ne signale pas l'absence de la cle. */
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.debug("Objet supprime : {}", key);
        } catch (S3Exception e) {
            throw new StorageException("Echec de la suppression de l'objet : " + key, e);
        }
    }

    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            // headObject renvoie 404 sans corps : le SDK le mappe parfois en S3Exception brute.
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageException("Echec du test d'existence de l'objet : " + key, e);
        }
    }
}

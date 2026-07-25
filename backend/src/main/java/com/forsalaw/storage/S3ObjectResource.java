package com.forsalaw.storage;

import org.springframework.core.io.InputStreamResource;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Resource Spring adossee a un objet S3.
 *
 * On surcharge {@link #contentLength()} : l'implementation heritee de {@link InputStreamResource}
 * consommerait tout le flux pour le mesurer, ce qui laisserait un flux vide au client. La taille
 * est deja connue via l'en-tete Content-Length renvoye par S3.
 */
public class S3ObjectResource extends InputStreamResource {

    private final long contentLength;
    private final String filename;

    public S3ObjectResource(ResponseInputStream<GetObjectResponse> stream, String key) {
        super(stream, "Objet S3 [" + key + "]");
        this.contentLength = stream.response().contentLength();
        this.filename = key.substring(key.lastIndexOf('/') + 1);
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String getFilename() {
        return filename;
    }
}

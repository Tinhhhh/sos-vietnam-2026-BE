package com.sosvietnam.service.implement;

import com.sosvietnam.config.R2StorageProperties;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.service.MediaAccess;
import com.sosvietnam.service.MediaStorageService;
import com.sosvietnam.util.MediaKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;

/**
 * Stores media in a private Cloudflare R2 bucket. Files are never public:
 * viewers get a signed link that expires after app.storage.r2.presign-ttl
 * and download straight from R2 (video seeking works there too).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "r2")
public class R2MediaStorageServiceImpl implements MediaStorageService {

    private final S3Client r2Client;
    private final S3Presigner r2Presigner;
    private final R2StorageProperties props;

    /** Logs whether the bucket is reachable so a wrong key shows up at startup, not at the first SOS. */
    @EventListener(ApplicationReadyEvent.class)
    public void checkBucket() {
        try {
            r2Client.headBucket(b -> b.bucket(props.bucket()));
            log.info("☁️ [MEDIA] Cloudflare R2 bucket '{}' is reachable at {}", props.bucket(), props.endpointUri().getHost());
        } catch (SdkException e) {
            log.warn("⚠️ [MEDIA] Cannot reach R2 bucket '{}' at {}: {}", props.bucket(), props.endpointUri().getHost(), e.getMessage());
        }
    }

    @Override
    public String store(String folder, MultipartFile file, String extension) {
        String fileName = MediaKeys.newFileName(extension);
        String key = MediaKeys.key(folder, fileName);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        // A supplier (not one InputStream) so the SDK can reopen the upload when it retries
        // after a network error; a plain multipart stream can only be read once.
        ContentStreamProvider content = ContentStreamProvider.fromInputStreamSupplier(() -> {
            try {
                return file.getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        try {
            r2Client.putObject(request, RequestBody.fromContentProvider(content, file.getSize(), file.getContentType()));
        } catch (SdkException | UncheckedIOException e) {
            log.error("Could not upload {} to R2: {}", key, e.getMessage());
            throw new SosException(HttpStatus.BAD_GATEWAY, "Không tải được tệp lên kho lưu trữ, vui lòng thử lại");
        }
        return fileName;
    }

    @Override
    public MediaAccess load(String folder, String fileName) {
        String key = MediaKeys.key(folder, fileName);
        // Signing happens locally (no network call); R2 checks the signature when the browser follows the link.
        var presigned = r2Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(props.presignTtl())
                .getObjectRequest(b -> b.bucket(props.bucket()).key(key))
                .build());
        try {
            return MediaAccess.redirect(presigned.url().toURI());
        } catch (URISyntaxException e) {
            throw new SosException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được đường dẫn tệp");
        }
    }
}

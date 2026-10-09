package com.sosvietnam.service.implement;

import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.service.MediaAccess;
import com.sosvietnam.service.MediaStorageService;
import com.sosvietnam.util.MediaKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Stores uploads on the local disk under app.upload-dir (default for development). */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalMediaStorageServiceImpl implements MediaStorageService {

    private final Path root;

    public LocalMediaStorageServiceImpl(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        log.info("📁 [MEDIA] Local storage at {}", root);
    }

    @Override
    public String store(String folder, MultipartFile file, String extension) {
        String fileName = MediaKeys.newFileName(extension);
        Path target = resolve(folder, fileName);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Could not store upload {}: {}", target, e.getMessage());
            throw new SosException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được tệp tải lên");
        }
        return fileName;
    }

    @Override
    public MediaAccess load(String folder, String fileName) {
        Path path = resolve(folder, fileName);
        if (!Files.isRegularFile(path)) {
            throw new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp");
        }
        return MediaAccess.stream(new PathResource(path));
    }

    private Path resolve(String folder, String fileName) {
        Path path = root.resolve(MediaKeys.key(folder, fileName)).normalize();
        if (!path.startsWith(root)) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Đường dẫn không hợp lệ");
        }
        return path;
    }
}

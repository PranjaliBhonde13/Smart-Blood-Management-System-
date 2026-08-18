package com.sben.common.storage;

import com.sben.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded files (medical reports) on the local filesystem under a
 * configurable directory. Validates type and size, and generates a random
 * filename to avoid collisions and path-traversal via the original name.
 *
 * <p>For cloud deployment this is the seam to swap for an S3/GCS adapter — the
 * calling service depends only on {@link #store}/{@link #load}.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of("pdf", "png", "jpg", "jpeg");
    private static final long MAX_BYTES = 5 * 1024 * 1024; // 5 MB

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
            log.info("File storage initialised at {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory", e);
        }
    }

    /** Persist a file and return the stored (relative) filename to save on the entity. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("File exceeds 5 MB limit", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String ext = StringUtils.getFilenameExtension(
                StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (ext == null || !ALLOWED.contains(ext.toLowerCase())) {
            throw new BusinessException("Only PDF, PNG, JPG files are allowed", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        String storedName = UUID.randomUUID() + "." + ext.toLowerCase();
        try {
            Path target = root.resolve(storedName).normalize();
            if (!target.startsWith(root)) { // defence-in-depth against traversal
                throw new BusinessException("Invalid file path", HttpStatus.BAD_REQUEST);
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return storedName;
        } catch (IOException e) {
            throw new BusinessException("Failed to store file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Path load(String filename) {
        Path file = root.resolve(filename).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            throw new BusinessException("File not found", HttpStatus.NOT_FOUND);
        }
        return file;
    }
}

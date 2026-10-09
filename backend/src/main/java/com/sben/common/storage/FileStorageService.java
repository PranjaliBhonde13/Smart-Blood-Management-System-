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
import java.io.InputStream;
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
        return store(file, ALLOWED, MAX_BYTES);
    }

    /**
     * Persist a file restricted to {@code allowedExtensions} (subset of PDF/PNG/JPG)
     * and {@code maxBytes}; e.g. logos accept images only.
     */
    public String store(MultipartFile file, Set<String> allowedExtensions, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("File exceeds " + (maxBytes / (1024 * 1024)) + " MB limit",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String ext = StringUtils.getFilenameExtension(
                StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (ext == null || !allowedExtensions.contains(ext.toLowerCase()) || !ALLOWED.contains(ext.toLowerCase())) {
            String allowed = String.join(", ", allowedExtensions.stream()
                    .filter(e -> !e.equals("jpeg")).map(String::toUpperCase).sorted().toList());
            throw new BusinessException("Only " + allowed + " files are allowed", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (!contentMatches(file, ext.toLowerCase())) {
            throw new BusinessException("File content does not match its ." + ext.toLowerCase() + " extension",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE);
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

    /** Checks the file's leading "magic" bytes against the claimed type. */
    private static boolean contentMatches(MultipartFile file, String ext) {
        byte[] head = new byte[8];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return false;
        }
        return switch (ext) {
            case "pdf" -> n >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            case "png" -> n >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "jpg", "jpeg" -> n >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF;
            default -> false;
        };
    }

    /** Best-effort removal of a previously stored file (e.g. a replaced logo). */
    public void delete(String filename) {
        if (filename == null) return;
        Path file = root.resolve(filename).normalize();
        if (!file.startsWith(root)) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete stored file {}: {}", filename, e.getMessage());
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

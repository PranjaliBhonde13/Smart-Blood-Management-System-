package com.sben.hospital.document;

import com.sben.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Set;

/**
 * Checks that an uploaded document really is what it claims to be, beyond the
 * extension and magic-byte check in FileStorageService: images must have a
 * readable header of the matching format with sane dimensions, and PDFs must be
 * complete and must not carry JavaScript or launch actions.
 */
final class DocumentContentValidator {

    static final Set<String> EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final int MAX_IMAGE_SIDE = 12_000;
    private static final byte[][] PDF_FORBIDDEN = {
            "/JavaScript".getBytes(StandardCharsets.US_ASCII),
            "/Launch".getBytes(StandardCharsets.US_ASCII),
    };

    private DocumentContentValidator() {}

    /** Lower-case extension of the original filename, or a 415 if it is not PDF/JPG/PNG. */
    static String extension(MultipartFile file) {
        String ext = StringUtils.getFilenameExtension(
                StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (ext == null || !EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException("Only PDF, JPG and PNG files are allowed", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        return ext.toLowerCase();
    }

    static void validate(MultipartFile file, String ext) {
        switch (ext) {
            case "pdf" -> validatePdf(file);
            case "png" -> validateImage(file, "png");
            default -> validateImage(file, "jpeg");
        }
    }

    private static void validatePdf(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw invalid("Could not read the PDF");
        }
        if (bytes.length < 8 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw invalid("File content does not match its .pdf extension");
        }
        int tailStart = Math.max(0, bytes.length - 2048);
        if (indexOf(bytes, "%%EOF".getBytes(StandardCharsets.US_ASCII), tailStart) < 0) {
            throw invalid("The PDF appears to be incomplete or damaged");
        }
        for (byte[] token : PDF_FORBIDDEN) {
            if (indexOf(bytes, token, 0) >= 0) {
                throw invalid("PDFs containing scripts or launch actions are not accepted");
            }
        }
    }

    private static void validateImage(MultipartFile file, String format) {
        try (InputStream in = file.getInputStream(); ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) throw invalid("Could not read the image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw invalid("File content is not a valid image");
            ImageReader reader = readers.next();
            try {
                if (!format.equalsIgnoreCase(reader.getFormatName())
                        && !("jpeg".equals(format) && "jpg".equalsIgnoreCase(reader.getFormatName()))) {
                    throw invalid("File content does not match its extension");
                }
                reader.setInput(iis, true, true);
                // Reads only the header: catches truncated/forged files without decoding (no decompression bombs).
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_IMAGE_SIDE || h > MAX_IMAGE_SIDE) {
                    throw invalid("Image dimensions are not acceptable");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid("File content is not a valid image");
        }
    }

    /** Keep only the base name, strip control/quote/path characters, cap the length. */
    static String displayName(String original) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(original == null ? "" : original));
        if (name == null || name.isBlank()) return "document";
        name = name.replaceAll("[\\p{Cntrl}\"\\\\/<>]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    /** Content type from the stored (server-generated) filename, never from the client. */
    static String contentType(String storedName) {
        String lower = storedName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    static String sha256(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException("Failed to read the uploaded file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(message, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
}

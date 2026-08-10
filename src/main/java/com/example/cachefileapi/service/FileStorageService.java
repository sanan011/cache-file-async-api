package com.example.cachefileapi.service;

import com.example.cachefileapi.exception.InvalidFileException;
import com.example.cachefileapi.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

/**
 * Handles file storage for product images.
 *
 * <h3>Validation performed by {@link #storeFile}</h3>
 * <ol>
 * <li>File must not be empty.</li>
 * <li>File size must not exceed {@value #MAX_FILE_SIZE_BYTES} bytes (5
 * MB).</li>
 * <li>Extension must be one of {@link #ALLOWED_EXTENSIONS}.</li>
 * <li>Declared content type must start with {@code image/} (first-pass filter
 * only).</li>
 * <li>File content must match a known image signature (PNG, JPEG, GIF, or WEBP
 * magic bytes).</li>
 * </ol>
 *
 * <p>
 * Stored files are named {@code <UUID>.<extension>} — the raw original filename
 * is never used as a path component, preventing path-traversal attacks.
 * </p>
 */
@Service
@Slf4j
public class FileStorageService {

    /**
     * 5 MB in bytes — defence-in-depth; Spring's multipart resolver enforces this
     * first.
     */
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    /** Allowed image extensions (lower-cased). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** Bytes to read for magic-byte detection (WEBP needs 12). */
    private static final int MAGIC_HEADER_SIZE = 12;

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload.dir}") String uploadDirPath) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    /**
     * Creates the upload directory on startup if it does not already exist.
     */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(uploadDir);
            log.info("Upload directory ready: {}", uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create upload directory: " + uploadDir, e);
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public String storeFile(MultipartFile file, Long productId) {
        // 1. Must not be empty
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file must not be empty.");
        }

        // 2. Size guard (defence-in-depth — multipart resolver already rejects above 5
        // MB)
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException(
                    "File size " + file.getSize() + " bytes exceeds the maximum allowed size of 5 MB.");
        }

        // 3. Extension check
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException(
                    "Unsupported file extension '" + extension + "'. "
                            + "Allowed extensions: " + ALLOWED_EXTENSIONS + ".");
        }

        // 4. Content-type check — first-pass filter only; magic bytes are authoritative
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidFileException(
                    "File content type '" + contentType + "' is not an image. "
                            + "Only image/* content types are accepted.");
        }

        // 5. Build a safe, unique filename and store
        String safeFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadDir.resolve(safeFileName);

        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = readHeader(inputStream, MAGIC_HEADER_SIZE);
            validateImageMagicBytes(header);
            try (OutputStream outputStream = Files.newOutputStream(
                    targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                outputStream.write(header);
                inputStream.transferTo(outputStream);
            }
            log.info("Stored image for product id={} as '{}' (originalName='{}', size={} bytes)",
                    productId, safeFileName, originalFilename, file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to store file for product id=" + productId, e);
        }

        return safeFileName;
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = uploadDir.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException(
                    "Image file not found or not readable: " + fileName);
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException(
                    "Image file not found: " + fileName);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private byte[] readHeader(InputStream inputStream, int size) throws IOException {
        byte[] header = new byte[size];
        int totalRead = 0;
        while (totalRead < size) {
            int read = inputStream.read(header, totalRead, size - totalRead);
            if (read == -1) {
                break;
            }
            totalRead += read;
        }
        if (totalRead < size) {
            byte[] truncated = new byte[totalRead];
            System.arraycopy(header, 0, truncated, 0, totalRead);
            return truncated;
        }
        return header;
    }

    private void validateImageMagicBytes(byte[] header) {
        if (isJpeg(header) || isPng(header) || isGif(header) || isWebp(header)) {
            return;
        }
        throw new InvalidFileException("File content does not match a valid image format.");
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] header) {
        return header.length >= 4
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47;
    }

    private boolean isGif(byte[] header) {
        return header.length >= 4
                && header[0] == 0x47
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x38;
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        // Strip any path component a malicious client might inject
        String name = Paths.get(filename).getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "";
        }
        return name.substring(dotIndex + 1).toLowerCase();
    }
}

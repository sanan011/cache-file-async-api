package com.example.cachefileapi.service;

import com.example.cachefileapi.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Removes orphaned image files from the upload directory — files that exist on
 * disk but are not referenced by any
 * {@link com.example.cachefileapi.entity.Product#imageFileName}.
 */
@Service
@Slf4j
public class FileCleanupService {

    private final ProductRepository productRepository;
    private final Path uploadDir;

    public FileCleanupService(
            ProductRepository productRepository,
            @Value("${app.upload.dir}") String uploadDirPath) {
        this.productRepository = productRepository;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    /**
     * Scheduled daily cleanup driven by {@code app.cleanup.cron} (default: 2 AM).
     */
    @Scheduled(cron = "${app.cleanup.cron}")
    public void scheduledCleanup() {
        cleanupOrphanedFiles();
    }

    public int cleanupOrphanedFiles() {
        log.info("Starting orphaned file cleanup");

        Set<String> referencedFileNames = new HashSet<>(productRepository.findAllImageFileNames());
        int removed = 0;

        if (!Files.isDirectory(uploadDir)) {
            log.warn("Upload directory does not exist, skipping cleanup: {}", uploadDir);
            log.info("Cleanup finished: removed {} orphaned files", removed);
            return removed;
        }

        try (Stream<Path> files = Files.list(uploadDir)) {
            for (Path filePath : files.filter(Files::isRegularFile).toList()) {
                String fileName = filePath.getFileName().toString();
                if (!referencedFileNames.contains(fileName)) {
                    Files.delete(filePath);
                    log.debug("Removed orphaned file: {}", fileName);
                    removed++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to cleanup orphaned files in " + uploadDir, e);
        }

        log.info("Cleanup finished: removed {} orphaned files", removed);
        return removed;
    }
}

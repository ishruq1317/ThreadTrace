package com.threadtrace.evidence;

import com.threadtrace.config.ThreadTraceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "threadtrace.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalDiskEvidenceStorageAdapter implements EvidenceStoragePort {

    private final Path rootDir;

    public LocalDiskEvidenceStorageAdapter(ThreadTraceProperties properties) {
        this.rootDir = Path.of(properties.getStorage().getLocalDir()).toAbsolutePath().normalize();
    }

    @Override
    public String store(String keyPrefix, String originalFilename, String contentType, InputStream content, long sizeBytes) {
        try {
            Files.createDirectories(rootDir);
            String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storageKey = keyPrefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + safeName;
            Path target = rootDir.resolve(storageKey).normalize();
            if (!target.startsWith(rootDir)) {
                throw new IllegalArgumentException("Invalid storage key path");
            }
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return storageKey;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store evidence file locally", e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            Path target = rootDir.resolve(storageKey).normalize();
            if (!target.startsWith(rootDir) || !Files.exists(target)) {
                throw new IllegalStateException("Evidence file not found: " + storageKey);
            }
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read evidence file", e);
        }
    }
}

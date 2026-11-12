package com.threadtrace.evidence;

import java.io.InputStream;

/**
 * Storage port for certificate and test report files (FR5).
 * Implemented by LocalDiskEvidenceStorageAdapter and MinioS3EvidenceStorageAdapter.
 */
public interface EvidenceStoragePort {

    String store(String keyPrefix, String originalFilename, String contentType, InputStream content, long sizeBytes);

    byte[] load(String storageKey);
}

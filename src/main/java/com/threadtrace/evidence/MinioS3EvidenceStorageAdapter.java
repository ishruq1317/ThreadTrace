package com.threadtrace.evidence;

import com.threadtrace.config.ThreadTraceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "threadtrace.storage.type", havingValue = "minio")
public class MinioS3EvidenceStorageAdapter implements EvidenceStoragePort {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ThreadTraceProperties.StorageProperties storage;
    private final RestClient restClient;

    public MinioS3EvidenceStorageAdapter(ThreadTraceProperties properties) {
        this.storage = properties.getStorage();
        this.restClient = RestClient.builder().baseUrl(storage.getMinioEndpoint()).build();
    }

    @Override
    public String store(String keyPrefix, String originalFilename, String contentType, InputStream content, long sizeBytes) {
        try {
            byte[] payload = content.readAllBytes();
            String safePrefix = keyPrefix.replaceAll("[^a-zA-Z0-9._-]", "_");
            String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storageKey = safePrefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + safeName;
            String objectPath = "/" + storage.getMinioBucket() + "/" + storageKey;

            ensureBucketExists();
            SignedHeaders signed = sign("PUT", objectPath, payload);
            restClient.put()
                    .uri(objectPath)
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("Host", signed.host())
                    .header("x-amz-date", signed.amzDate())
                    .header("x-amz-content-sha256", signed.payloadHash())
                    .header("Authorization", signed.authorization())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return storageKey;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload evidence file to MinIO", e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        String objectPath = "/" + storage.getMinioBucket() + "/" + storageKey;
        byte[] empty = new byte[0];
        SignedHeaders signed = sign("GET", objectPath, empty);
        return restClient.get()
                .uri(objectPath)
                .header("Host", signed.host())
                .header("x-amz-date", signed.amzDate())
                .header("x-amz-content-sha256", signed.payloadHash())
                .header("Authorization", signed.authorization())
                .retrieve()
                .body(byte[].class);
    }

    private void ensureBucketExists() {
        String bucketPath = "/" + storage.getMinioBucket();
        byte[] empty = new byte[0];
        SignedHeaders headSigned = sign("HEAD", bucketPath, empty);
        try {
            restClient.head()
                    .uri(bucketPath)
                    .header("Host", headSigned.host())
                    .header("x-amz-date", headSigned.amzDate())
                    .header("x-amz-content-sha256", headSigned.payloadHash())
                    .header("Authorization", headSigned.authorization())
                    .retrieve()
                    .toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ignored) {
            SignedHeaders putSigned = sign("PUT", bucketPath, empty);
            restClient.put()
                    .uri(bucketPath)
                    .header("Host", putSigned.host())
                    .header("x-amz-date", putSigned.amzDate())
                    .header("x-amz-content-sha256", putSigned.payloadHash())
                    .header("Authorization", putSigned.authorization())
                    .body(empty)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private SignedHeaders sign(String method, String canonicalUri, byte[] payload) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String region = "us-east-1";
        String service = "s3";

        URI uri = URI.create(storage.getMinioEndpoint());
        String host = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        String payloadHash = sha256Hex(payload);

        String canonicalHeaders = "host:" + host + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = method + "\n"
                + canonicalUri + "\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;

        String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = getSignatureKey(storage.getMinioSecretKey(), dateStamp, region, service);
        String signature = HexFormat.of().formatHex(hmacSha256(stringToSign, signingKey));
        String authorization = "AWS4-HMAC-SHA256 Credential=" + storage.getMinioAccessKey() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        return new SignedHeaders(host, amzDate, payloadHash, authorization);
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(dateStamp, kSecret);
        byte[] kRegion = hmacSha256(regionName, kDate);
        byte[] kService = hmacSha256(serviceName, kRegion);
        return hmacSha256("aws4_request", kService);
    }

    private static byte[] hmacSha256(String data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign MinIO request", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record SignedHeaders(String host, String amzDate, String payloadHash, String authorization) {
    }
}

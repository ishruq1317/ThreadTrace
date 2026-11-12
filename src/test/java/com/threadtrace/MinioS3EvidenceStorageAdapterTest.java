package com.threadtrace;

import com.sun.net.httpserver.HttpServer;
import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.evidence.MinioS3EvidenceStorageAdapter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioS3EvidenceStorageAdapterTest {

    @Test
    void signsWithSigV4AutoCreatesBucketAndRoundTripsFile() throws Exception {
        Map<String, byte[]> store = new ConcurrentHashMap<>();
        AtomicBoolean bucketExists = new AtomicBoolean(false);
        AtomicBoolean sawSigV4Auth = new AtomicBoolean(false);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test-bucket", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("AWS4-HMAC-SHA256 Credential=minio-key/")
                    && exchange.getRequestHeaders().containsKey("x-amz-date")
                    && exchange.getRequestHeaders().containsKey("x-amz-content-sha256")) {
                sawSigV4Auth.set(true);
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/test-bucket".equals(path)) {
                if ("HEAD".equals(method)) {
                    exchange.sendResponseHeaders(bucketExists.get() ? 200 : 404, -1);
                } else if ("PUT".equals(method)) {
                    bucketExists.set(true);
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } else {
                if ("PUT".equals(method)) {
                    byte[] body = exchange.getRequestBody().readAllBytes();
                    store.put(path, body);
                    exchange.sendResponseHeaders(200, -1);
                } else if ("GET".equals(method)) {
                    byte[] body = store.get(path);
                    if (body == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                    }
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            }
            exchange.close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            ThreadTraceProperties props = new ThreadTraceProperties();
            props.getStorage().setType("minio");
            props.getStorage().setMinioEndpoint("http://127.0.0.1:" + port);
            props.getStorage().setMinioBucket("test-bucket");
            props.getStorage().setMinioAccessKey("minio-key");
            props.getStorage().setMinioSecretKey("minio-secret");

            MinioS3EvidenceStorageAdapter adapter = new MinioS3EvidenceStorageAdapter(props);
            byte[] payload = "GOTS Certificate PDF Content".getBytes(StandardCharsets.UTF_8);
            String key = adapter.store("CERT/2026", "gots cert.pdf", "application/pdf",
                    new ByteArrayInputStream(payload), payload.length);

            assertTrue(bucketExists.get());
            assertTrue(sawSigV4Auth.get());
            assertArrayEquals(payload, adapter.load(key));
        } finally {
            server.stop(0);
        }
    }
}

package com.threadtrace.passport;

import com.threadtrace.config.ThreadTraceProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/public/passports")
@Tag(name = "Public Passport & QR", description = "FR7, QA4: Public QR JSON endpoint with HTTP caching, ETags, and DB-outage cache fallback")
public class PublicPassportController {

    private final PassportService passportService;
    private final QrCodeGenerator qrCodeGenerator;
    private final ThreadTraceProperties properties;

    public PublicPassportController(
            PassportService passportService,
            QrCodeGenerator qrCodeGenerator,
            ThreadTraceProperties properties) {
        this.passportService = passportService;
        this.qrCodeGenerator = qrCodeGenerator;
        this.properties = properties;
    }

    @GetMapping("/{passportCode}")
    @Operation(summary = "Get public passport JSON (no confidential fields, ETag + Cache-Control + fallback cache)")
    public ResponseEntity<PassportDtos.PublicPassportView> getPublicPassportJson(
            @PathVariable String passportCode,
            @RequestParam(required = false) Integer version,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        PassportService.PublicPassportResponse response = passportService.getPublicPassportWithFallback(passportCode, version);
        CacheControl cacheControl = CacheControl.maxAge(properties.getPassport().getCacheTtlSeconds(), TimeUnit.SECONDS)
                .cachePublic()
                .staleWhileRevalidate(properties.getPassport().getStaleWhileRevalidateSeconds(), TimeUnit.SECONDS);

        if (ifNoneMatch != null && ifNoneMatch.equals(response.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(cacheControl)
                    .eTag(response.etag())
                    .build();
        }

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .eTag(response.etag())
                .header("X-ThreadTrace-Cache", response.servedFromFallbackCache() ? "FALLBACK" : "ORIGIN")
                .body(response.view());
    }

    @GetMapping(value = "/{passportCode}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Generate PNG QR code pointing to the public passport URL")
    public ResponseEntity<byte[]> getPassportQr(
            @PathVariable String passportCode,
            jakarta.servlet.http.HttpServletRequest request) {
        String baseUrl = resolvePublicBaseUrl(request);
        String targetUrl = baseUrl + "/p/" + passportCode;
        byte[] png = qrCodeGenerator.generatePng(targetUrl, 256, 256);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    private String resolvePublicBaseUrl(jakarta.servlet.http.HttpServletRequest request) {
        String configured = properties.getPassport().getPublicBaseUrl();
        if (request != null && (configured == null || configured.contains("localhost"))) {
            String forwardedHost = request.getHeader("X-Forwarded-Host");
            String host = (forwardedHost != null && !forwardedHost.isBlank())
                    ? forwardedHost.split(",")[0].trim()
                    : request.getServerName();
            if (host != null && !host.isBlank() && !"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
                String forwardedProto = request.getHeader("X-Forwarded-Proto");
                String scheme = (forwardedProto != null && !forwardedProto.isBlank())
                        ? forwardedProto.split(",")[0].trim()
                        : request.getScheme();
                int port = request.getServerPort();
                boolean standardPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                        || ("https".equalsIgnoreCase(scheme) && port == 443)
                        || (forwardedHost != null && !forwardedHost.isBlank())
                        || (forwardedProto != null && !forwardedProto.isBlank());
                return standardPort ? (scheme + "://" + host) : (scheme + "://" + host + ":" + port);
            }
        }
        return configured != null ? configured.replaceAll("/+$", "") : "http://localhost:8080";
    }
}

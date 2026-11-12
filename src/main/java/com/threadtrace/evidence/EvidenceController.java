package com.threadtrace.evidence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/evidence")
@Tag(name = "Evidence & Certificates", description = "FR5: Attach certificates and test reports, evaluate expiry, download files")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping(value = "/certificates", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Register a certificate or test report via JSON payload")
    public EvidenceDtos.CertificateView registerCertificateJson(
            @Valid @RequestBody EvidenceDtos.RegisterCertificateRequest request) {
        return evidenceService.registerCertificate(request, null);
    }

    @PostMapping(value = "/certificates/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Upload a certificate or test report file with metadata")
    public EvidenceDtos.CertificateView uploadCertificateMultipart(
            @Valid @RequestPart("metadata") EvidenceDtos.RegisterCertificateRequest request,
            @RequestPart("file") MultipartFile file) {
        return evidenceService.registerCertificate(request, file);
    }

    @GetMapping("/certificates")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "List certificates with pagination")
    public Page<EvidenceDtos.CertificateView> listCertificates(
            @RequestParam(required = false) CertificateStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return evidenceService.listCertificates(status, pageable);
    }

    @GetMapping("/certificates/{certificateNumber}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Download stored certificate or test report file")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String certificateNumber) {
        Certificate cert = evidenceService.requireCertificate(certificateNumber);
        byte[] data = evidenceService.loadCertificateFile(certificateNumber);
        String safeFilename = cert.getOriginalFilename().replaceAll("[\"\\r\\n]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename + "\"")
                .contentType(MediaType.parseMediaType(cert.getContentType()))
                .body(data);
    }

    @PostMapping("/certificates/evaluate-expiry")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'OPERATOR')")
    @Operation(summary = "Evaluate certificate expiry and emit alerts/recalculation events")
    public EvidenceDtos.ExpiryEvaluationSummary evaluateExpiry(
            @RequestParam(required = false) LocalDate asOfDate) {
        return evidenceService.evaluateExpiry(asOfDate);
    }
}

package com.threadtrace.evidence;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotRepository;
import com.threadtrace.parties.Party;
import com.threadtrace.parties.PartyService;
import com.threadtrace.shared.Audience;
import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final CertificateRepository certificateRepository;
    private final EvidenceStoragePort storagePort;
    private final PartyService partyService;
    private final LotRepository lotRepository;
    private final ThreadTraceProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final AudienceAccessService audienceAccessService;

    public EvidenceService(
            CertificateRepository certificateRepository,
            EvidenceStoragePort storagePort,
            PartyService partyService,
            LotRepository lotRepository,
            ThreadTraceProperties properties,
            ApplicationEventPublisher eventPublisher,
            AudienceAccessService audienceAccessService) {
        this.certificateRepository = certificateRepository;
        this.storagePort = storagePort;
        this.partyService = partyService;
        this.lotRepository = lotRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.audienceAccessService = audienceAccessService;
    }

    @Transactional
    public EvidenceDtos.CertificateView registerCertificate(
            EvidenceDtos.RegisterCertificateRequest req,
            MultipartFile uploadedFile) {
        if (certificateRepository.existsByCertificateNumber(req.certificateNumber())) {
            throw new DomainExceptions.DuplicateResourceException(
                    "Certificate already exists: " + req.certificateNumber());
        }
        if (req.validTo().isBefore(req.validFrom())) {
            throw new DomainExceptions.DomainValidationException("validTo cannot be before validFrom");
        }
        String normalizedType = req.certificateType().trim().toUpperCase();
        boolean allowed = properties.getEvidence().getAllowedCertificateTypes().stream()
                .anyMatch(t -> t.equalsIgnoreCase(normalizedType));
        if (!allowed) {
            throw new DomainExceptions.DomainValidationException(
                    "Unsupported certificateType '" + req.certificateType() + "'. Allowed: "
                            + properties.getEvidence().getAllowedCertificateTypes());
        }

        if ((req.supplierCode() == null || req.supplierCode().isBlank())
                && (req.lotCode() == null || req.lotCode().isBlank())) {
            throw new DomainExceptions.DomainValidationException(
                    "Certificate must attach to either a supplierCode or a lotCode");
        }

        Long supplierId = null;
        if (req.supplierCode() != null && !req.supplierCode().isBlank()) {
            Party supplier = partyService.requirePartyByCode(req.supplierCode());
            supplierId = supplier.getId();
        }
        Long lotId = null;
        if (req.lotCode() != null && !req.lotCode().isBlank()) {
            Lot lot = lotRepository.findByLotCode(req.lotCode())
                    .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Lot not found: " + req.lotCode()));
            lotId = lot.getId();
        }

        byte[] contentBytes;
        String filename;
        String contentType;
        if (uploadedFile != null && !uploadedFile.isEmpty()) {
            try {
                contentBytes = uploadedFile.getBytes();
                filename = uploadedFile.getOriginalFilename() != null ? uploadedFile.getOriginalFilename() : "evidence.bin";
                contentType = uploadedFile.getContentType() != null ? uploadedFile.getContentType() : "application/octet-stream";
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read uploaded certificate file", e);
            }
        } else if (req.fileContentBase64() != null && !req.fileContentBase64().isBlank()) {
            contentBytes = Base64.getDecoder().decode(req.fileContentBase64());
            filename = (req.filename() != null && !req.filename().isBlank()) ? req.filename() : req.certificateNumber() + ".pdf";
            contentType = (req.contentType() != null && !req.contentType().isBlank()) ? req.contentType() : "application/pdf";
        } else {
            String stub = "Certificate " + req.certificateNumber() + " (" + normalizedType + ") issued by " + req.issuer();
            contentBytes = stub.getBytes(StandardCharsets.UTF_8);
            filename = (req.filename() != null && !req.filename().isBlank()) ? req.filename() : req.certificateNumber() + ".txt";
            contentType = "text/plain";
        }

        String storageKey;
        try (InputStream is = new ByteArrayInputStream(contentBytes)) {
            storageKey = storagePort.store(req.certificateNumber(), filename, contentType, is, contentBytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store evidence file", e);
        }

        LocalDate today = LocalDate.now();
        int warningDays = properties.getEvidence().getExpiryWarningDays();
        CertificateStatus initialStatus = computeStatus(req.validTo(), today, warningDays);

        Certificate cert = certificateRepository.save(new Certificate(
                req.certificateNumber(),
                normalizedType,
                req.issuer(),
                req.scope(),
                req.claimedAttribute() != null ? req.claimedAttribute().trim().toUpperCase() : null,
                req.certifiedPct() != null ? req.certifiedPct() : BigDecimal.valueOf(100),
                supplierId,
                lotId,
                req.validFrom(),
                req.validTo(),
                initialStatus,
                storageKey,
                filename,
                contentType,
                contentBytes.length
        ));

        if (initialStatus == CertificateStatus.EXPIRING_SOON) {
            eventPublisher.publishEvent(new CertificateEvents.CertificateExpiringSoonEvent(
                    cert.getId(), cert.getCertificateNumber(), cert.getCertificateType(),
                    cert.getSupplierId(), cert.getLotId(), cert.getValidTo()));
        } else if (initialStatus == CertificateStatus.EXPIRED) {
            eventPublisher.publishEvent(new CertificateEvents.CertificateExpiredEvent(
                    cert.getId(), cert.getCertificateNumber(), cert.getCertificateType(),
                    cert.getClaimedAttribute(), cert.getSupplierId(), cert.getLotId(), cert.getValidTo(), today));
        }

        log.info("Registered certificate number={} type={} status={} storageKey={}",
                cert.getCertificateNumber(), cert.getCertificateType(), initialStatus, storageKey);
        return toView(cert);
    }

    @Transactional
    public EvidenceDtos.ExpiryEvaluationSummary evaluateExpiry(LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        int warningDays = properties.getEvidence().getExpiryWarningDays();
        List<Certificate> candidates = certificateRepository.findByStatusIn(
                List.of(CertificateStatus.ACTIVE, CertificateStatus.EXPIRING_SOON));

        int expiringSoonCount = 0;
        int expiredCount = 0;
        for (Certificate cert : candidates) {
            CertificateStatus next = cert.evaluateStatus(effectiveDate, warningDays);
            if (next != cert.getStatus()) {
                cert.updateStatus(next);
                certificateRepository.save(cert);
                if (next == CertificateStatus.EXPIRING_SOON) {
                    expiringSoonCount++;
                    eventPublisher.publishEvent(new CertificateEvents.CertificateExpiringSoonEvent(
                            cert.getId(), cert.getCertificateNumber(), cert.getCertificateType(),
                            cert.getSupplierId(), cert.getLotId(), cert.getValidTo()));
                } else if (next == CertificateStatus.EXPIRED) {
                    expiredCount++;
                    eventPublisher.publishEvent(new CertificateEvents.CertificateExpiredEvent(
                            cert.getId(), cert.getCertificateNumber(), cert.getCertificateType(),
                            cert.getClaimedAttribute(), cert.getSupplierId(), cert.getLotId(), cert.getValidTo(), effectiveDate));
                }
            }
        }
        log.info("Evaluated certificate expiry asOfDate={} expiringSoon={} expired={}",
                effectiveDate, expiringSoonCount, expiredCount);
        return new EvidenceDtos.ExpiryEvaluationSummary(effectiveDate, expiringSoonCount, expiredCount);
    }

    public Page<EvidenceDtos.CertificateView> listCertificates(CertificateStatus status, Pageable pageable) {
        Audience audience = audienceAccessService.resolveAudience();
        if (audience == Audience.BRAND) {
            String brandCode = audienceAccessService.currentBrandScope().orElse("");
            Page<Certificate> page = status != null
                    ? certificateRepository.findVisibleToBrandByStatus(brandCode, status, pageable)
                    : certificateRepository.findVisibleToBrand(brandCode, pageable);
            return page.map(this::toView);
        }
        Page<Certificate> page = status != null
                ? certificateRepository.findByStatus(status, pageable)
                : certificateRepository.findAll(pageable);
        return page.map(this::toView);
    }

    public Certificate requireCertificate(String certificateNumber) {
        Certificate cert = certificateRepository.findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException(
                        "Certificate not found: " + certificateNumber));
        if (cert.getLotId() != null) {
            lotRepository.findById(cert.getLotId())
                    .ifPresent(lot -> audienceAccessService.assertBrandAccess(lot.getBrandCode()));
        }
        return cert;
    }

    public byte[] loadCertificateFile(String certificateNumber) {
        Certificate cert = requireCertificate(certificateNumber);
        return storagePort.load(cert.getStorageKey());
    }

    private CertificateStatus computeStatus(LocalDate validTo, LocalDate today, int warningDays) {
        if (today.isAfter(validTo)) {
            return CertificateStatus.EXPIRED;
        }
        if (!today.plusDays(warningDays).isBefore(validTo)) {
            return CertificateStatus.EXPIRING_SOON;
        }
        return CertificateStatus.ACTIVE;
    }

    public EvidenceDtos.CertificateView toView(Certificate c) {
        return new EvidenceDtos.CertificateView(
                c.getId(),
                c.getCertificateNumber(),
                c.getCertificateType(),
                c.getIssuer(),
                c.getScope(),
                c.getClaimedAttribute(),
                c.getCertifiedPct(),
                c.getSupplierId(),
                c.getLotId(),
                c.getValidFrom(),
                c.getValidTo(),
                c.getStatus(),
                c.getOriginalFilename(),
                c.getContentType(),
                c.getFileSizeBytes(),
                c.getCreatedAt()
        );
    }
}

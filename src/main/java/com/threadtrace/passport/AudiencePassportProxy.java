package com.threadtrace.passport;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.shared.Audience;
import com.threadtrace.shared.AudienceAccessService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Proxy Pattern enforcing audience-specific projections and brand scoping (FR8, QA3).
 * Ensures confidential fields (supplier prices, exact addresses, worker counts) never leak to public views.
 */
@Component
public class AudiencePassportProxy {

    private final AudienceAccessService audienceAccessService;
    private final ThreadTraceProperties properties;

    public AudiencePassportProxy(AudienceAccessService audienceAccessService, ThreadTraceProperties properties) {
        this.audienceAccessService = audienceAccessService;
        this.properties = properties;
    }

    public Object projectForCurrentAudience(ProductPassport entity, PassportDtos.PassportCanonicalPayload payload) {
        Audience audience = audienceAccessService.resolveAudience();
        return switch (audience) {
            case PUBLIC -> toPublicView(payload);
            case BRAND -> {
                audienceAccessService.assertBrandAccess(entity.getBrandCode());
                yield toBrandView(payload);
            }
            case AUDITOR -> toAuditorView(entity, payload);
        };
    }

    public PassportDtos.PublicPassportView toPublicView(PassportDtos.PassportCanonicalPayload payload) {
        List<PassportDtos.PublicOriginStep> publicSteps = payload.originSteps().stream()
                .map(s -> new PassportDtos.PublicOriginStep(
                        s.depth(),
                        s.processStep(),
                        s.partyName(),
                        s.partyType(),
                        s.country(),
                        s.region()
                ))
                .toList();
        String qrUrl = properties.getPassport().getPublicBaseUrl()
                + "/api/v1/public/passports/" + payload.passportCode() + "/qr";
        return new PassportDtos.PublicPassportView(
                payload.passportCode(),
                payload.version(),
                payload.styleCode(),
                payload.styleName(),
                payload.brandCode(),
                payload.status(),
                payload.fibreComposition(),
                publicSteps,
                payload.certifications(),
                payload.careAndRepair(),
                payload.endOfLifeGuidance(),
                payload.footprint(),
                payload.additionalFields(),
                qrUrl,
                payload.generatedAt()
        );
    }

    public PassportDtos.BrandPassportView toBrandView(PassportDtos.PassportCanonicalPayload payload) {
        List<PassportDtos.BrandOriginStep> brandSteps = payload.originSteps().stream()
                .map(s -> new PassportDtos.BrandOriginStep(
                        s.depth(),
                        s.processStep(),
                        s.lotCode(),
                        s.partyCode(),
                        s.partyName(),
                        s.partyType(),
                        s.country(),
                        s.region()
                ))
                .toList();
        return new PassportDtos.BrandPassportView(
                payload.passportCode(),
                payload.version(),
                payload.styleCode(),
                payload.styleName(),
                payload.shipmentLotCode(),
                payload.brandCode(),
                payload.status(),
                payload.fibreComposition(),
                brandSteps,
                payload.certifications(),
                payload.careAndRepair(),
                payload.endOfLifeGuidance(),
                payload.footprint(),
                payload.additionalFields(),
                payload.complianceViolations(),
                payload.generatedAt()
        );
    }

    public PassportDtos.AuditorPassportView toAuditorView(
            ProductPassport entity,
            PassportDtos.PassportCanonicalPayload payload) {
        return new PassportDtos.AuditorPassportView(
                payload.passportCode(),
                payload.version(),
                payload.styleCode(),
                payload.styleName(),
                payload.shipmentLotCode(),
                payload.brandCode(),
                payload.status(),
                payload.fibreComposition(),
                payload.originSteps(),
                payload.certifications(),
                payload.careAndRepair(),
                payload.endOfLifeGuidance(),
                payload.footprint(),
                payload.additionalFields(),
                payload.complianceViolations(),
                entity.getContentHash(),
                entity.getEtag(),
                payload.generatedAt()
        );
    }
}

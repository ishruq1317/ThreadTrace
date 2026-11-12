package com.threadtrace.parties;

import com.threadtrace.shared.Audience;
import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PartyService {

    private final PartyRepository partyRepository;
    private final ProductionLineRepository productionLineRepository;
    private final AudienceAccessService audienceAccessService;

    public PartyService(
            PartyRepository partyRepository,
            ProductionLineRepository productionLineRepository,
            AudienceAccessService audienceAccessService) {
        this.partyRepository = partyRepository;
        this.productionLineRepository = productionLineRepository;
        this.audienceAccessService = audienceAccessService;
    }

    @Transactional
    public PartyDtos.PartyAuditorView registerParty(PartyDtos.CreatePartyRequest req) {
        if (partyRepository.existsByCode(req.code())) {
            throw new DomainExceptions.DuplicateResourceException("Party with code '" + req.code() + "' already exists");
        }
        Party saved = partyRepository.save(new Party(
                req.code(),
                req.name(),
                req.partyType(),
                req.country(),
                req.region(),
                req.exactAddress(),
                req.workerCount(),
                req.unitPriceUsd()
        ));
        return toAuditorView(saved);
    }

    public Page<?> listParties(PartyType partyType, Pageable pageable) {
        Page<Party> page = partyType != null
                ? partyRepository.findByPartyType(partyType, pageable)
                : partyRepository.findAll(pageable);
        Audience audience = audienceAccessService.resolveAudience();
        return page.map(party -> projectParty(party, audience));
    }

    public Object getParty(String code) {
        Party party = partyRepository.findByCode(code)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Party not found: " + code));
        return projectParty(party, audienceAccessService.resolveAudience());
    }

    @Transactional
    public PartyDtos.ProductionLineView registerProductionLine(String factoryCode, PartyDtos.CreateProductionLineRequest req) {
        Party factory = partyRepository.findByCode(factoryCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Factory not found: " + factoryCode));
        if (factory.getPartyType() != PartyType.FACTORY) {
            throw new DomainExceptions.DomainValidationException(
                    "Production lines can only be registered under FACTORY parties, found: " + factory.getPartyType());
        }
        if (productionLineRepository.existsByLineCode(req.lineCode())) {
            throw new DomainExceptions.DuplicateResourceException("Production line already exists: " + req.lineCode());
        }
        ProductionLine line = productionLineRepository.save(new ProductionLine(
                req.lineCode(),
                req.name(),
                factory,
                req.processType(),
                req.locationDetail()
        ));
        return new PartyDtos.ProductionLineView(
                line.getId(),
                line.getLineCode(),
                line.getName(),
                factory.getCode(),
                line.getProcessType(),
                line.getLocationDetail(),
                line.getCreatedAt()
        );
    }

    public Page<PartyDtos.ProductionLineView> listProductionLines(String factoryCode, Pageable pageable) {
        Party factory = partyRepository.findByCode(factoryCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Factory not found: " + factoryCode));
        return productionLineRepository.findByFactoryId(factory.getId(), pageable)
                .map(line -> new PartyDtos.ProductionLineView(
                        line.getId(),
                        line.getLineCode(),
                        line.getName(),
                        factory.getCode(),
                        line.getProcessType(),
                        line.getLocationDetail(),
                        line.getCreatedAt()
                ));
    }

    public Party requirePartyByCode(String code) {
        return partyRepository.findByCode(code)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Party not found: " + code));
    }

    public Party requirePartyById(Long id) {
        return partyRepository.findById(id)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Party not found id=" + id));
    }

    public ProductionLine requireLineByCode(String lineCode) {
        return productionLineRepository.findByLineCode(lineCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Production line not found: " + lineCode));
    }

    private Object projectParty(Party party, Audience audience) {
        return switch (audience) {
            case AUDITOR -> toAuditorView(party);
            case BRAND -> new PartyDtos.PartyBrandView(
                    party.getCode(), party.getName(), party.getPartyType(), party.getCountry(), party.getRegion());
            case PUBLIC -> new PartyDtos.PartyPublicView(
                    party.getCode(), party.getName(), party.getPartyType(), party.getCountry(), party.getRegion());
        };
    }

    private PartyDtos.PartyAuditorView toAuditorView(Party p) {
        return new PartyDtos.PartyAuditorView(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getPartyType(),
                p.getCountry(),
                p.getRegion(),
                p.getExactAddress(),
                p.getWorkerCount(),
                p.getUnitPriceUsd(),
                p.getCreatedAt()
        );
    }
}

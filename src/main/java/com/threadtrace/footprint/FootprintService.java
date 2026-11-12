package com.threadtrace.footprint;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.lineage.LineageCompositeBuilder;
import com.threadtrace.lineage.LineageNode;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotRepository;
import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class FootprintService {

    private static final Logger log = LoggerFactory.getLogger(FootprintService.class);

    private final Map<ThreadTraceProperties.AllocationMethod, AllocationStrategy> strategies;
    private final ThreadTraceProperties properties;
    private final LineageCompositeBuilder compositeBuilder;
    private final LotRepository lotRepository;
    private final AudienceAccessService audienceAccessService;

    public FootprintService(
            List<AllocationStrategy> strategyList,
            ThreadTraceProperties properties,
            LineageCompositeBuilder compositeBuilder,
            LotRepository lotRepository,
            AudienceAccessService audienceAccessService) {
        this.strategies = new EnumMap<>(ThreadTraceProperties.AllocationMethod.class);
        for (AllocationStrategy strategy : strategyList) {
            this.strategies.put(strategy.method(), strategy);
        }
        this.properties = properties;
        this.compositeBuilder = compositeBuilder;
        this.lotRepository = lotRepository;
        this.audienceAccessService = audienceAccessService;
    }

    public FootprintRollupVisitor.FootprintSummary calculateForLot(
            String lotCode,
            ThreadTraceProperties.AllocationMethod requestedMethod) {
        Lot lot = lotRepository.findByLotCode(lotCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Lot not found: " + lotCode));
        audienceAccessService.assertBrandAccess(lot.getBrandCode());
        log.info("Calculating footprint for lot={} requestedMethod={}", lotCode, requestedMethod);
        LineageCompositeBuilder.LineageResult lineage = compositeBuilder.buildBackwardTree(lot);
        return calculateForLineageRoot(lineage.root(), requestedMethod);
    }

    public FootprintRollupVisitor.FootprintSummary calculateForLineageRoot(
            LineageNode root,
            ThreadTraceProperties.AllocationMethod requestedMethod) {
        ThreadTraceProperties.AllocationMethod effective = requestedMethod != null
                ? requestedMethod
                : properties.getFootprint().getDefaultAllocationMethod();
        AllocationStrategy strategy = strategies.get(effective);
        if (strategy == null) {
            throw new DomainExceptions.DomainValidationException("No AllocationStrategy configured for " + effective);
        }
        FootprintRollupVisitor visitor = new FootprintRollupVisitor(strategy);
        root.accept(visitor);
        FootprintRollupVisitor.FootprintSummary summary = visitor.getSummary();
        log.info("Calculated lineage footprint method={} waterLiters={} energyKwh={} co2eKg={}",
                effective, summary.totalWaterLiters(), summary.totalEnergyKwh(), summary.totalCo2eKg());
        return summary;
    }
}

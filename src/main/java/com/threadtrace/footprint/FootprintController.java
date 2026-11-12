package com.threadtrace.footprint;

import com.threadtrace.config.ThreadTraceProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/footprint")
@Tag(name = "Footprint Roll-up", description = "FR6: Water, energy, and CO2e roll-up with configurable allocation strategy")
public class FootprintController {

    private final FootprintService footprintService;

    public FootprintController(FootprintService footprintService) {
        this.footprintService = footprintService;
    }

    @GetMapping("/lots/{lotCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Calculate cumulative and per-piece water, energy, and CO2e footprint for a lot")
    public FootprintRollupVisitor.FootprintSummary calculateFootprint(
            @PathVariable String lotCode,
            @RequestParam(required = false) ThreadTraceProperties.AllocationMethod allocationMethod) {
        return footprintService.calculateForLot(lotCode, allocationMethod);
    }
}

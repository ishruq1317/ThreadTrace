package com.threadtrace.parties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parties")
@Tag(name = "Parties & Facilities", description = "FR1: Suppliers, factories, brands, and production lines")
public class PartyController {

    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Register a supplier, factory, or brand")
    public PartyDtos.PartyAuditorView registerParty(@Valid @RequestBody PartyDtos.CreatePartyRequest request) {
        return partyService.registerParty(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "List parties with pagination and audience-filtered projection")
    public Page<?> listParties(
            @RequestParam(required = false) PartyType partyType,
            @PageableDefault(size = 20) Pageable pageable) {
        return partyService.listParties(partyType, pageable);
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Get party details with audience-filtered projection")
    public Object getParty(@PathVariable String code) {
        return partyService.getParty(code);
    }

    @PostMapping("/{factoryCode}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Register a production line inside a factory")
    public PartyDtos.ProductionLineView registerProductionLine(
            @PathVariable String factoryCode,
            @Valid @RequestBody PartyDtos.CreateProductionLineRequest request) {
        return partyService.registerProductionLine(factoryCode, request);
    }

    @GetMapping("/{factoryCode}/lines")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "List production lines for a factory")
    public Page<PartyDtos.ProductionLineView> listProductionLines(
            @PathVariable String factoryCode,
            @PageableDefault(size = 20) Pageable pageable) {
        return partyService.listProductionLines(factoryCode, pageable);
    }
}

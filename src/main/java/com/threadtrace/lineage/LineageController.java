package com.threadtrace.lineage;

import com.threadtrace.shared.AudienceAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Lots, Events & Lineage", description = "FR2, FR3, FR10: Immutable lot events, splits/merges, recursive trace, and hash verification")
public class LineageController {

    private final LineageService lineageService;
    private final EventIntegrityService integrityService;
    private final AudienceAccessService audienceAccessService;

    public LineageController(
            LineageService lineageService,
            EventIntegrityService integrityService,
            AudienceAccessService audienceAccessService) {
        this.lineageService = lineageService;
        this.integrityService = integrityService;
        this.audienceAccessService = audienceAccessService;
    }

    @PostMapping("/events/receive")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Record a RECEIVE event for a new raw material or fabric lot")
    public LineageDtos.LotView recordReceive(@Valid @RequestBody LineageDtos.ReceiveLotRequest request) {
        return lineageService.recordReceive(request);
    }

    @PostMapping("/events/transform")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Record a production transformation event (CUT, SEW, WASH_OR_DYE, FINISH, PACK, SHIP)")
    public LineageDtos.LotView recordTransform(@Valid @RequestBody LineageDtos.TransformLotRequest request) {
        return lineageService.recordTransform(request);
    }

    @PostMapping("/events/split")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Split one parent lot into multiple child lots")
    public List<LineageDtos.LotView> recordSplit(@Valid @RequestBody LineageDtos.SplitLotRequest request) {
        return lineageService.recordSplit(request);
    }

    @PostMapping("/events/merge")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Merge multiple parent lots into one output lot")
    public LineageDtos.LotView recordMerge(@Valid @RequestBody LineageDtos.MergeLotRequest request) {
        return lineageService.recordMerge(request);
    }

    @PostMapping("/events/correction")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
    @Operation(summary = "Append an immutable correction event referencing a previous event")
    public LineageDtos.LotEventView recordCorrection(@Valid @RequestBody LineageDtos.CorrectionEventRequest request) {
        return lineageService.recordCorrection(request);
    }

    @GetMapping("/lots")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "List lots with pagination and brand scoping")
    public Page<LineageDtos.LotView> listLots(
            @RequestParam(required = false) LotStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return lineageService.listLots(status, pageable);
    }

    @GetMapping("/lots/{lotCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Get a lot by code (enforces brand scope)")
    public LineageDtos.LotView getLot(@PathVariable String lotCode) {
        return lineageService.getLot(lotCode);
    }

    @GetMapping("/lots/{lotCode}/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "List immutable events for a lot (paginated)")
    public Page<LineageDtos.LotEventView> listEvents(
            @PathVariable String lotCode,
            @PageableDefault(size = 20) Pageable pageable) {
        return lineageService.listEventsForLot(lotCode, pageable);
    }

    @GetMapping("/traces/backward/{lotCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Backward trace from a carton/shipment lot to every source lot and supplier")
    public LineageDtos.TraceResponse traceBackward(
            @PathVariable String lotCode,
            @RequestParam(defaultValue = "12") @Min(1) @Max(20) int maxDepth) {
        return lineageService.traceBackward(lotCode, maxDepth);
    }

    @GetMapping("/traces/backward/style/{styleCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Backward trace from a garment style back to every source lot and supplier")
    public LineageDtos.TraceResponse traceBackwardByStyle(
            @PathVariable String styleCode,
            @RequestParam(defaultValue = "12") @Min(1) @Max(20) int maxDepth) {
        return lineageService.traceBackwardByStyle(styleCode, maxDepth);
    }

    @GetMapping("/traces/forward/{lotCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Forward trace from a fabric/raw lot to every carton and shipment")
    public LineageDtos.TraceResponse traceForward(
            @PathVariable String lotCode,
            @RequestParam(defaultValue = "12") @Min(1) @Max(20) int maxDepth) {
        return lineageService.traceForward(lotCode, maxDepth);
    }

    @GetMapping("/integrity/lots/{lotCode}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Verify SHA-256 event hash chain for a lot")
    public LineageDtos.LotChainVerificationResult verifyLotChain(@PathVariable String lotCode) {
        Lot lot = lineageService.requireLotByCode(lotCode);
        audienceAccessService.assertBrandAccess(lot.getBrandCode());
        return integrityService.verifyLot(lot);
    }
}

package com.threadtrace.passport;

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
@RequestMapping("/api/v1")
@Tag(name = "Digital Product Passports", description = "FR7, FR8, FR10: Versioned passports, audience views, and integrity")
public class PassportController {

    private final PassportService passportService;

    public PassportController(PassportService passportService) {
        this.passportService = passportService;
    }

    @PostMapping("/passports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'BRAND')")
    @Operation(summary = "Generate a new version of a Digital Product Passport for a shipment lot")
    public PassportDtos.AuditorPassportView generatePassport(
            @Valid @RequestBody PassportDtos.GeneratePassportRequest request) {
        return passportService.generatePassport(request);
    }

    @GetMapping("/passports")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "List latest product passports (paginated, brand-scoped)")
    public Page<PassportDtos.PassportVersionSummary> listPassports(
            @PageableDefault(size = 20) Pageable pageable) {
        return passportService.listLatestPassports(pageable);
    }

    @GetMapping("/passports/{passportCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Get latest (or specific version) passport projected for caller's audience")
    public Object getPassport(
            @PathVariable String passportCode,
            @RequestParam(required = false) Integer version) {
        return passportService.getPassportForAudience(passportCode, version);
    }

    @GetMapping("/passports/{passportCode}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "List historical versions of a passport (paginated)")
    public Page<PassportDtos.PassportVersionSummary> listVersions(
            @PathVariable String passportCode,
            @PageableDefault(size = 20) Pageable pageable) {
        return passportService.listPassportVersions(passportCode, pageable);
    }

    @GetMapping("/passports/{passportCode}/versions/{version}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Retrieve a specific historical passport version")
    public Object getPassportVersion(
            @PathVariable String passportCode,
            @PathVariable int version) {
        return passportService.getPassportForAudience(passportCode, version);
    }

    @GetMapping("/integrity/passports/{passportCode}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'BRAND')")
    @Operation(summary = "Verify hash-chain integrity of all lot events behind a passport")
    public PassportDtos.PassportIntegrityVerificationView verifyPassportIntegrity(
            @PathVariable String passportCode) {
        return passportService.verifyPassportIntegrity(passportCode);
    }
}

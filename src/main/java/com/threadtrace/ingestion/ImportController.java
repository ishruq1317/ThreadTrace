package com.threadtrace.ingestion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Bulk Imports", description = "FR9: Spring Batch CSV/Excel import with row validation and rejection reporting")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Upload and execute a CSV or Excel factory import batch")
    public ImportService.ImportBatchReportView uploadAndRunImport(
            @RequestParam ImportType importType,
            @RequestParam ImportFileFormat fileFormat,
            @RequestPart("file") MultipartFile file) {
        return importService.importFactoryFile(importType, fileFormat, file);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
    @Operation(summary = "List import batches (paginated)")
    public Page<ImportService.ImportBatchSummaryView> listBatches(
            @PageableDefault(size = 20) Pageable pageable) {
        return importService.listBatches(pageable);
    }

    @GetMapping("/{batchCode}/rejected")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
    @Operation(summary = "List rejected rows with failure reasons for an import batch (paginated)")
    public Page<ImportService.RejectedRowView> listRejectedRows(
            @PathVariable String batchCode,
            @PageableDefault(size = 50) Pageable pageable) {
        return importService.listRejectedRows(batchCode, pageable);
    }
}

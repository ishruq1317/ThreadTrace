package com.threadtrace.ingestion;

import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final List<ImportRowAdapter> adapters;
    private final ImportBatchRepository batchRepository;
    private final ImportRejectedRowRepository rejectedRowRepository;
    private final ImportBatchStagingStore stagingStore;
    private final Job factoryImportJob;
    private final JobOperator jobOperator;
    private final AudienceAccessService audienceAccessService;

    public ImportService(
            List<ImportRowAdapter> adapters,
            ImportBatchRepository batchRepository,
            ImportRejectedRowRepository rejectedRowRepository,
            ImportBatchStagingStore stagingStore,
            Job factoryImportJob,
            JobOperator jobOperator,
            AudienceAccessService audienceAccessService) {
        this.adapters = adapters;
        this.batchRepository = batchRepository;
        this.rejectedRowRepository = rejectedRowRepository;
        this.stagingStore = stagingStore;
        this.factoryImportJob = factoryImportJob;
        this.jobOperator = jobOperator;
        this.audienceAccessService = audienceAccessService;
    }

    public ImportBatchReportView importFactoryFile(
            ImportType importType,
            ImportFileFormat fileFormat,
            MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainExceptions.DomainValidationException("Uploaded import file must not be empty");
        }
        ImportRowAdapter adapter = adapters.stream()
                .filter(a -> a.supports(fileFormat))
                .findFirst()
                .orElseThrow(() -> new DomainExceptions.DomainValidationException(
                        "Unsupported import file format: " + fileFormat));

        List<FactoryImportRow> rows;
        try (InputStream is = file.getInputStream()) {
            rows = adapter.parse(is, importType);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded import file", e);
        }

        String batchCode = "IMP-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        ImportBatch batch = batchRepository.save(new ImportBatch(
                batchCode,
                importType,
                fileFormat,
                filename,
                rows.size(),
                audienceAccessService.currentUsername()
        ));

        stagingStore.init(batchCode, rows);
        log.info("Starting batch import batchCode={} type={} format={} rows={}",
                batchCode, importType, fileFormat, rows.size());
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("batchId", batch.getId())
                    .addString("batchCode", batchCode)
                    .addString("importType", importType.name())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            JobExecution execution = jobOperator.start(factoryImportJob, params);

            int accepted = stagingStore.acceptedCount(batchCode);
            int rejected = stagingStore.rejectedCount(batchCode);
            batch.markCompleted(accepted, rejected, execution.getStatus().name());
            batch = batchRepository.save(batch);
            log.info("Completed batch import batchCode={} status={} accepted={} rejected={}",
                    batch.getBatchCode(), batch.getStatus(), accepted, rejected);

            List<RejectedRowView> rejectedViews = rejectedRowRepository
                    .findByBatchIdOrderByRowNumberAsc(batch.getId())
                    .stream()
                    .map(r -> new RejectedRowView(r.getRowNumber(), r.getRawPayload(), r.getReason()))
                    .toList();

            return new ImportBatchReportView(
                    batch.getBatchCode(),
                    batch.getImportType(),
                    batch.getFileFormat(),
                    batch.getFilename(),
                    batch.getTotalRows(),
                    batch.getAcceptedRows(),
                    batch.getRejectedRows(),
                    batch.getStatus(),
                    batch.getCreatedAt(),
                    batch.getCompletedAt(),
                    rejectedViews
            );
        } catch (Exception e) {
            batch.markCompleted(stagingStore.acceptedCount(batchCode), stagingStore.rejectedCount(batchCode), "FAILED");
            batchRepository.save(batch);
            throw new IllegalStateException("Batch import job failed: " + e.getMessage(), e);
        } finally {
            stagingStore.clear(batchCode);
        }
    }

    public Page<ImportBatchSummaryView> listBatches(Pageable pageable) {
        return batchRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(b -> new ImportBatchSummaryView(
                        b.getBatchCode(), b.getImportType(), b.getFileFormat(), b.getFilename(),
                        b.getTotalRows(), b.getAcceptedRows(), b.getRejectedRows(),
                        b.getStatus(), b.getUploadedBy(), b.getCreatedAt(), b.getCompletedAt()));
    }

    public Page<RejectedRowView> listRejectedRows(String batchCode, Pageable pageable) {
        ImportBatch batch = batchRepository.findByBatchCode(batchCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Import batch not found: " + batchCode));
        return rejectedRowRepository.findByBatchIdOrderByRowNumberAsc(batch.getId(), pageable)
                .map(r -> new RejectedRowView(r.getRowNumber(), r.getRawPayload(), r.getReason()));
    }

    public byte[] buildSampleWorkbook(String parentLotCode, String validOutputLotCode, String styleCode) {
        String effectiveParent = (parentLotCode != null && !parentLotCode.isBlank()) ? parentLotCode : "LOT-RAW-001";
        String effectiveOut = (validOutputLotCode != null && !validOutputLotCode.isBlank()) ? validOutputLotCode : "LOT-XLSX-OK-1";
        String effectiveStyle = (styleCode != null && !styleCode.isBlank()) ? styleCode : "HOODIE-2026";
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("FactoryBatch");
            Row header = sheet.createRow(0);
            String[] cols = {"inputLotCode", "outputLotCode", "outputLotType", "styleCode", "factoryCode",
                    "productionLineCode", "inputQuantity", "outputQuantity", "outputMassKg", "outputPieceCount", "unit"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            Row r1 = sheet.createRow(1);
            String[] v1 = {effectiveParent, effectiveOut, "CUT_BUNDLE", effectiveStyle, "", "", "10.0", "20", "9.5", "20", "PIECES"};
            for (int i = 0; i < v1.length; i++) {
                r1.createCell(i).setCellValue(v1[i]);
            }
            Row r2 = sheet.createRow(2);
            String[] v2 = {"MISSING-LOT-999", "LOT-XLSX-BAD-1", "CUT_BUNDLE", effectiveStyle, "", "", "10.0", "20", "9.5", "20", "PIECES"};
            for (int i = 0; i < v2.length; i++) {
                r2.createCell(i).setCellValue(v2[i]);
            }
            Row r3 = sheet.createRow(3);
            String[] v3 = {effectiveParent, "LOT-XLSX-BAD-2", "CUT_BUNDLE", effectiveStyle, "", "", "INVALID_DECIMAL", "20", "9.5", "20", "PIECES"};
            for (int i = 0; i < v3.length; i++) {
                r3.createCell(i).setCellValue(v3[i]);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build sample Excel workbook", e);
        }
    }

    public record RejectedRowView(int rowNumber, String rawPayload, String reason) {
    }

    public record ImportBatchSummaryView(
            String batchCode,
            ImportType importType,
            ImportFileFormat fileFormat,
            String filename,
            int totalRows,
            int acceptedRows,
            int rejectedRows,
            String status,
            String uploadedBy,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    public record ImportBatchReportView(
            String batchCode,
            ImportType importType,
            ImportFileFormat fileFormat,
            String filename,
            int totalRows,
            int acceptedRows,
            int rejectedRows,
            String status,
            Instant createdAt,
            Instant completedAt,
            List<RejectedRowView> rejectedRowDetails
    ) {
    }
}

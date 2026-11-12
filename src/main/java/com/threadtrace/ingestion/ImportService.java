package com.threadtrace.ingestion;

import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ImportService {

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

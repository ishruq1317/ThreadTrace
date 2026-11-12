package com.threadtrace.ingestion;

import com.threadtrace.lineage.EventType;
import com.threadtrace.lineage.LineageDtos;
import com.threadtrace.lineage.LineageService;
import com.threadtrace.lineage.LotType;
import com.threadtrace.lineage.LotUnit;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Configuration
public class FactoryImportJobConfig {

    @Bean
    public Job factoryImportJob(JobRepository jobRepository, Step factoryImportStep) {
        return new JobBuilder("factoryImportJob", jobRepository)
                .start(factoryImportStep)
                .build();
    }

    @Bean
    public Step factoryImportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<FactoryImportRow> factoryImportItemReader,
            ItemWriter<FactoryImportRow> factoryImportItemWriter) {
        return new StepBuilder("factoryImportStep", jobRepository)
                .<FactoryImportRow, FactoryImportRow>chunk(50)
                .transactionManager(transactionManager)
                .reader(factoryImportItemReader)
                .writer(factoryImportItemWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<FactoryImportRow> factoryImportItemReader(
            @Value("#{jobParameters['batchCode']}") String batchCode,
            ImportBatchStagingStore stagingStore) {
        List<FactoryImportRow> rows = stagingStore.getRows(batchCode);
        return new ListItemReader<>(rows);
    }

    @Bean
    @StepScope
    public ItemWriter<FactoryImportRow> factoryImportItemWriter(
            @Value("#{jobParameters['batchId']}") Long batchId,
            @Value("#{jobParameters['batchCode']}") String batchCode,
            @Value("#{jobParameters['importType']}") String importTypeName,
            ImportBatchStagingStore stagingStore,
            LineageService lineageService,
            ImportRejectedRowRepository rejectedRowRepository,
            PlatformTransactionManager transactionManager) {
        ImportType importType = ImportType.valueOf(importTypeName);
        TransactionTemplate requiresNewTx = new TransactionTemplate(transactionManager);
        requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return chunk -> {
            for (FactoryImportRow row : chunk) {
                String validationError = validateRow(row);
                if (validationError != null) {
                    requiresNewTx.executeWithoutResult(status ->
                            rejectedRowRepository.save(new ImportRejectedRow(
                                    batchId, row.rowNumber(), row.rawPayload(), validationError)));
                    stagingStore.incrementRejected(batchCode);
                    continue;
                }

                try {
                    requiresNewTx.executeWithoutResult(status -> {
                        EventType eventType = importType == ImportType.CUTTING_REPORT
                                ? EventType.CUT
                                : EventType.PACK;
                        String processStep = importType == ImportType.CUTTING_REPORT ? "cut" : "pack";
                        LotType outType = LotType.valueOf(row.outputLotType().toUpperCase());
                        LotUnit unit = LotUnit.valueOf(row.unit().toUpperCase());

                        lineageService.recordTransform(new LineageDtos.TransformLotRequest(
                                row.inputLotCode(),
                                row.outputLotCode(),
                                outType,
                                eventType,
                                processStep,
                                row.styleCode(),
                                row.factoryCode(),
                                row.productionLineCode(),
                                row.inputQuantity(),
                                row.outputQuantity(),
                                row.outputMassKg(),
                                row.outputPieceCount(),
                                unit,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "Bulk import batch " + batchCode + " row " + row.rowNumber(),
                                Instant.now()
                        ));
                    });
                    stagingStore.incrementAccepted(batchCode);
                } catch (Exception ex) {
                    requiresNewTx.executeWithoutResult(status ->
                            rejectedRowRepository.save(new ImportRejectedRow(
                                    batchId, row.rowNumber(), row.rawPayload(), ex.getMessage())));
                    stagingStore.incrementRejected(batchCode);
                }
            }
        };
    }

    private static String validateRow(FactoryImportRow row) {
        if (row.parseError() != null && !row.parseError().isBlank()) {
            return row.parseError();
        }
        if (row.inputLotCode() == null || row.inputLotCode().isBlank()) {
            return "inputLotCode is required";
        }
        if (row.outputLotCode() == null || row.outputLotCode().isBlank()) {
            return "outputLotCode is required";
        }
        if (row.inputQuantity() == null || row.inputQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return "inputQuantity must be positive";
        }
        if (row.outputQuantity() == null || row.outputQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return "outputQuantity must be positive";
        }
        if (row.outputMassKg() == null || row.outputMassKg().compareTo(BigDecimal.ZERO) <= 0) {
            return "outputMassKg must be positive";
        }
        if (row.outputPieceCount() == null || row.outputPieceCount() < 0) {
            return "outputPieceCount must be >= 0";
        }
        try {
            LotType.valueOf(row.outputLotType().toUpperCase());
        } catch (Exception e) {
            return "Invalid outputLotType: " + row.outputLotType();
        }
        try {
            LotUnit.valueOf(row.unit().toUpperCase());
        } catch (Exception e) {
            return "Invalid unit: " + row.unit();
        }
        return null;
    }
}

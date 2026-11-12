package com.threadtrace.ingestion;

import java.math.BigDecimal;

public record FactoryImportRow(
        int rowNumber,
        String rawPayload,
        String inputLotCode,
        String outputLotCode,
        String outputLotType,
        String styleCode,
        String factoryCode,
        String productionLineCode,
        BigDecimal inputQuantity,
        BigDecimal outputQuantity,
        BigDecimal outputMassKg,
        Integer outputPieceCount,
        String unit,
        String parseError
) {
}

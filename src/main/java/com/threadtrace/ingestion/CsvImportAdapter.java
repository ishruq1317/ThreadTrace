package com.threadtrace.ingestion;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvImportAdapter implements ImportRowAdapter {

    @Override
    public boolean supports(ImportFileFormat format) {
        return format == ImportFileFormat.CSV;
    }

    @Override
    public List<FactoryImportRow> parse(InputStream input, ImportType importType) {
        List<FactoryImportRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            int rowNum = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) {
                    continue;
                }
                rows.add(parseLine(rowNum, line));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read CSV import stream", e);
        }
        return rows;
    }

    private FactoryImportRow parseLine(int rowNum, String line) {
        String[] cols = splitCsv(line);
        if (cols.length < 11) {
            return new FactoryImportRow(rowNum, line, null, null, null, null, null, null,
                    null, null, null, null, null,
                    "Expected 11 CSV columns but found " + cols.length);
        }
        try {
            return new FactoryImportRow(
                    rowNum,
                    line,
                    cols[0].trim(),
                    cols[1].trim(),
                    cols[2].trim(),
                    cols[3].trim(),
                    cols[4].trim(),
                    cols[5].trim(),
                    parseBigDecimal(cols[6], "inputQuantity"),
                    parseBigDecimal(cols[7], "outputQuantity"),
                    parseBigDecimal(cols[8], "outputMassKg"),
                    parseInt(cols[9], "outputPieceCount"),
                    cols[10].trim(),
                    null
            );
        } catch (IllegalArgumentException ex) {
            return new FactoryImportRow(rowNum, line, cols[0].trim(), cols[1].trim(),
                    cols[2].trim(), cols[3].trim(), cols[4].trim(), cols[5].trim(),
                    null, null, null, null, cols[10].trim(), ex.getMessage());
        }
    }

    private static String[] splitCsv(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        tokens.add(current.toString());
        return tokens.toArray(String[]::new);
    }

    private static BigDecimal parseBigDecimal(String raw, String field) {
        try {
            return new BigDecimal(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid decimal for " + field + ": '" + raw + "'");
        }
    }

    private static Integer parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid integer for " + field + ": '" + raw + "'");
        }
    }
}

package com.threadtrace.ingestion;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class ExcelImportAdapter implements ImportRowAdapter {

    private final DataFormatter formatter = new DataFormatter();

    @Override
    public boolean supports(ImportFileFormat format) {
        return format == ImportFileFormat.EXCEL;
    }

    @Override
    public List<FactoryImportRow> parse(InputStream input, ImportType importType) {
        List<FactoryImportRow> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                return rows;
            }
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> cols = IntStream.range(0, 11)
                        .mapToObj(i -> cellString(row.getCell(i)))
                        .toList();
                String raw = String.join(",", cols);
                if (cols.stream().allMatch(String::isBlank)) {
                    continue;
                }
                rows.add(toRow(r + 1, raw, cols));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Excel import workbook", e);
        }
        return rows;
    }

    private FactoryImportRow toRow(int rowNum, String raw, List<String> cols) {
        try {
            return new FactoryImportRow(
                    rowNum,
                    raw,
                    cols.get(0),
                    cols.get(1),
                    cols.get(2),
                    cols.get(3),
                    cols.get(4),
                    cols.get(5),
                    new BigDecimal(cols.get(6)),
                    new BigDecimal(cols.get(7)),
                    new BigDecimal(cols.get(8)),
                    Integer.parseInt(cols.get(9)),
                    cols.get(10),
                    null
            );
        } catch (Exception ex) {
            return new FactoryImportRow(
                    rowNum, raw, cols.get(0), cols.get(1), cols.get(2),
                    cols.get(3), cols.get(4), cols.get(5),
                    null, null, null, null, cols.get(10),
                    "Invalid numeric value in Excel row: " + ex.getMessage());
        }
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }
}

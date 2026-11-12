package com.threadtrace.ingestion;

import java.io.InputStream;
import java.util.List;

/**
 * Adapter Pattern for factory export formats (CSV, Excel) into canonical FactoryImportRow items (FR9).
 */
public interface ImportRowAdapter {

    boolean supports(ImportFileFormat format);

    List<FactoryImportRow> parse(InputStream input, ImportType importType);
}

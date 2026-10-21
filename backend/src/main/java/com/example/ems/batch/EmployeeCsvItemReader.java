package com.example.ems.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

public class EmployeeCsvItemReader implements ItemStreamReader<EmployeeImportProcessor.Input> {
    private final Path path;
    private BufferedReader reader;
    private long rowNumber = 1;

    public EmployeeCsvItemReader(Path path) {
        this.path = path;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            String header = reader.readLine();
            if (header != null && !header.isEmpty() && header.charAt(0) == '\uFEFF')
                header = header.substring(1);
            List<String> columns = header == null ? List.of() : EmployeeCsvParser.parse(header);
            if (columns.size() != 3 || !columns.get(0).trim().equalsIgnoreCase("name")
                    || !columns.get(1).trim().equalsIgnoreCase("department")
                    || !columns.get(2).trim().equalsIgnoreCase("salary")) {
                throw new IllegalArgumentException("CSV header must be: name,department,salary");
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new ItemStreamException("Unable to open employee CSV: " + exception.getMessage(), exception);
        }
    }

    @Override
    public EmployeeImportProcessor.Input read() throws Exception {
        String line = reader.readLine();
        if (line == null)
            return null;
        return new EmployeeImportProcessor.Input(++rowNumber, line);
    }

    @Override
    public void update(ExecutionContext executionContext) {
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (reader != null)
                reader.close();
        } catch (IOException exception) {
            throw new ItemStreamException("Unable to close employee CSV", exception);
        }
    }
}

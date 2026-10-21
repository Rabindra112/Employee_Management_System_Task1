package com.example.ems.batch;

import com.example.ems.service.EmployeeCreator;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

public class EmployeeImportWriter implements ItemWriter<EmployeeImportRow> {
    private final EmployeeCreator employees;
    private final JdbcTemplate jdbc;
    private Long executionId;
    private String uploadId;

    public EmployeeImportWriter(EmployeeCreator employees, JdbcTemplate jdbc) {
        this.employees = employees;
        this.jdbc = jdbc;
    }

    @BeforeStep
    public void captureExecution(StepExecution stepExecution) {
        this.executionId = stepExecution.getJobExecutionId();
        this.uploadId = stepExecution.getJobExecution().getJobParameters().getString("uploadId");
    }

    @Override
    public void write(Chunk<? extends EmployeeImportRow> chunk) {
        for (EmployeeImportRow row : chunk.getItems()) {
            if (row.valid()) {
                employees.create(row.employee());
                jdbc.update("UPDATE employee_import_uploads SET imported_count=imported_count+1 WHERE upload_id=?",
                        uploadId);
            } else {
                String errors = String.join("; ", row.errors());
                jdbc.update(
                        "INSERT INTO employee_import_errors(execution_id,source_row_number,row_data,error_message) VALUES (?,?,?,?)",
                        executionId, row.rowNumber(), row.source(), errors);
            }
        }
    }
}

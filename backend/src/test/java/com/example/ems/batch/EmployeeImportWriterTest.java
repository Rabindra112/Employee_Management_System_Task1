package com.example.ems.batch;

import com.example.ems.service.EmployeeCreator;
import com.example.ems.dto.EmployeeRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;

class EmployeeImportWriterTest {
    @Test void writesValidRowsAndStoresRowLevelErrors() throws Exception {
        EmployeeCreator employees = mock(EmployeeCreator.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EmployeeImportWriter writer = new EmployeeImportWriter(employees, jdbc);
        JobExecution execution = new JobExecution(27L, new JobParametersBuilder().addString("uploadId", "upload-27").toJobParameters());
        writer.captureExecution(new StepExecution("employeeImportStep", execution));

        EmployeeImportRow valid = new EmployeeImportRow(2, "Alice,IT,10", new EmployeeRequest("Alice", "IT", new BigDecimal("10")), List.of());
        EmployeeImportRow invalid = new EmployeeImportRow(3, "Bad,IT,0", null, List.of("salary: must be greater than zero"));
        writer.write(new Chunk<>(List.of(valid, invalid)));

        verify(employees).create(valid.employee());
        verify(jdbc).update("UPDATE employee_import_uploads SET imported_count=imported_count+1 WHERE upload_id=?", "upload-27");
        verify(jdbc).update("INSERT INTO employee_import_errors(execution_id,source_row_number,row_data,error_message) VALUES (?,?,?,?)",
                27L, 3L, "Bad,IT,0", "salary: must be greater than zero");
    }
}

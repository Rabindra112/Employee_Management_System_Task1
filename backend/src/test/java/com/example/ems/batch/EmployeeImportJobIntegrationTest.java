package com.example.ems.batch;

import com.example.ems.dto.EmployeeRequest;
import com.example.ems.entity.Employee;
import com.example.ems.service.EmployeeCreator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(EmployeeImportJobIntegrationTest.BatchTestConfiguration.class)
class EmployeeImportJobIntegrationTest {
    @Autowired JobLauncher launcher;
    @Autowired Job employeeImportJob;
    @Autowired JobExplorer jobExplorer;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingEmployeeCreator employees;
    @TempDir Path temp;

    @BeforeEach void resetImports() { employees.imported.clear(); }

    @Test void successfulImportSkipsInvalidAndRepeatedRowsAndStoresRowErrors() throws Exception {
        String uploadId = prepare("name,department,salary\r\nAlice,IT,45000\r\nBad,,0\r\nAlice,IT,45000.00\r\n");
        JobExecution execution = run(uploadId);
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(1, employees.imported.size());
        assertEquals(1, jdbc.queryForObject("SELECT imported_count FROM employee_import_uploads WHERE upload_id=?", Integer.class, uploadId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM employee_import_errors WHERE execution_id=?", Integer.class, execution.getId()));
    }

    @Test void invalidRecordsAreCompletedWithRowLevelValidationErrors() throws Exception {
        String uploadId = prepare("name,department,salary\r\n,IT,0\r\n");
        JobExecution execution = run(uploadId);
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM employee_import_errors WHERE execution_id=?", Integer.class, execution.getId()));
        assertTrue(employees.imported.isEmpty());
    }

    @Test void malformedHeaderFailsTheBatchJob() throws Exception {
        String uploadId = prepare("wrong,headers\r\nAlice,IT,45000\r\n");
        JobExecution execution = run(uploadId);
        assertEquals(BatchStatus.FAILED, execution.getStatus());
    }

    @Test void controllerStartsOutsideCallerTransactionAndRejectsDuplicateLaunches() throws Exception {
        String uploadId = prepare("name,department,salary\r\nAlice,IT,45000\r\n");
        EmployeeImportController controller = new EmployeeImportController(
                jdbc, launcher, jobExplorer, employeeImportJob, temp.toString());

        var result = controller.start(new EmployeeImportController.StartImportRequest(uploadId));

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(1, result.get("importedCount"));
        assertTrue(jdbc.queryForObject("SELECT launch_claimed FROM employee_import_uploads WHERE upload_id=?", Boolean.class, uploadId));
        assertNotNull(jdbc.queryForObject("SELECT execution_id FROM employee_import_uploads WHERE upload_id=?", Long.class, uploadId));
        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> controller.start(new EmployeeImportController.StartImportRequest(uploadId)));
        assertEquals(409, duplicate.getStatusCode().value());
    }

    private String prepare(String csv) throws Exception {
        String id = UUID.randomUUID().toString();
        Path path = temp.resolve(id + ".csv");
        Files.writeString(path, csv, StandardCharsets.UTF_8);
        jdbc.update("INSERT INTO employee_import_uploads(upload_id,original_filename,storage_path,uploaded_by,uploaded_at,imported_count) VALUES (?,?,?,?,?,0)",
                id, "integration.csv", path.toString(), "admin@example.com", Instant.now());
        return id;
    }

    private JobExecution run(String uploadId) throws Exception {
        return launcher.run(employeeImportJob, new JobParametersBuilder()
                .addString("uploadId", uploadId).addString("launchId", UUID.randomUUID().toString()).toJobParameters());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableBatchProcessing
    @Import(EmployeeImportBatchConfig.class)
    static class BatchTestConfiguration {
        @Bean DataSource dataSource() {
            DataSource source = new EmbeddedDatabaseBuilder().setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
            new ResourceDatabasePopulator(new ClassPathResource("batch-schema-h2.sql")).execute(source);
            return source;
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
        @Bean RecordingEmployeeCreator employeeService() { return new RecordingEmployeeCreator(); }

        @Bean
        @Primary
        JobLauncher employeeImportJobLauncher(JobRepository repository) throws Exception {
            TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
            launcher.setJobRepository(repository);
            launcher.setTaskExecutor(new org.springframework.core.task.SyncTaskExecutor());
            launcher.afterPropertiesSet();
            return launcher;
        }
    }

    static class RecordingEmployeeCreator implements EmployeeCreator {
        private final List<EmployeeRequest> imported = new ArrayList<>();
        @Override public Employee create(EmployeeRequest request) {
            imported.add(request);
            return new Employee(null, request.name(), request.department(), request.salary());
        }
    }
}

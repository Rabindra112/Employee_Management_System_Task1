package com.example.ems.batch;

import jakarta.validation.Validator;
import java.nio.file.Path;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.ems.service.EmployeeCreator;

@Configuration
@ConditionalOnProperty(name = "app.employee-import.enabled", havingValue = "true", matchIfMissing = true)
public class EmployeeImportBatchConfig {
    @Bean
    @StepScope
    EmployeeImportProcessor employeeImportProcessor(Validator validator) {
        return new EmployeeImportProcessor(validator);
    }

    @Bean
    @StepScope
    EmployeeCsvItemReader employeeCsvItemReader(JdbcTemplate jdbc,
            @Value("#{jobParameters['uploadId']}") String uploadId) {
        String location = jdbc.queryForObject("SELECT storage_path FROM employee_import_uploads WHERE upload_id=?",
                String.class, uploadId);
        return new EmployeeCsvItemReader(Path.of(location));
    }

    @Bean
    @StepScope
    EmployeeImportWriter employeeImportWriter(EmployeeCreator employees, JdbcTemplate jdbc) {
        return new EmployeeImportWriter(employees, jdbc);
    }

    @Bean
    Step employeeImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            EmployeeCsvItemReader employeeCsvItemReader, EmployeeImportProcessor employeeImportProcessor,
            EmployeeImportWriter employeeImportWriter) {
        return new StepBuilder("employeeImportStep", jobRepository)
                .<EmployeeImportProcessor.Input, EmployeeImportRow>chunk(100, transactionManager)
                .reader(employeeCsvItemReader)
                .processor(employeeImportProcessor)
                .writer(employeeImportWriter)
                .listener(employeeImportWriter)
                .build();
    }

    @Bean
    Job employeeImportJob(JobRepository jobRepository, Step employeeImportStep) {
        return new JobBuilder("employeeImportJob", jobRepository).start(employeeImportStep).build();
    }

    @Bean
    JobLauncher employeeImportJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("employee-import-"));
        launcher.afterPropertiesSet();
        return launcher;
    }
}

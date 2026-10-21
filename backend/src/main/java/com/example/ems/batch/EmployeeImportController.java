package com.example.ems.batch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/admin/employee-import")
@ConditionalOnProperty(name = "app.employee-import.enabled", havingValue = "true", matchIfMissing = true)
public class EmployeeImportController {
    private final JdbcTemplate jdbc;
    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job job;
    private final Path storage;

    public EmployeeImportController(JdbcTemplate jdbc, JobLauncher jobLauncher, JobExplorer jobExplorer,
            Job employeeImportJob, @Value("${app.employee-import.storage-path}") String storagePath) {
        this.jdbc = jdbc;
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.job = employeeImportJob;
        this.storage = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file, Authentication authentication) {
        if (file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is empty");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".csv"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a .csv file");
        String id = UUID.randomUUID().toString();
        try {
            Files.createDirectories(storage);
            Path target = storage.resolve(id + ".csv").normalize();
            if (!target.getParent().equals(storage))
                throw new IOException("Invalid upload path");
            file.transferTo(target);
            jdbc.update(
                    "INSERT INTO employee_import_uploads(upload_id,original_filename,storage_path,uploaded_by,uploaded_at,imported_count) VALUES (?,?,?,?,?,0)",
                    id, Path.of(file.getOriginalFilename()).getFileName().toString(), target.toString(),
                    authentication.getName(), Instant.now());
            return Map.of("uploadId", id, "filename", file.getOriginalFilename());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store CSV file");
        }
    }

    @PostMapping("/jobs")
    public Map<String, Object> start(@RequestBody StartImportRequest request) throws Exception {
        String uploadId = request.uploadId();
        if (uploadId == null || uploadId.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uploadId is required");
        int claimed = jdbc.update(
                "UPDATE employee_import_uploads SET launch_claimed=TRUE WHERE upload_id=? AND execution_id IS NULL AND launch_claimed=FALSE",
                uploadId);
        if (claimed == 0) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM employee_import_uploads WHERE upload_id=?",
                    Integer.class, uploadId);
            if (exists == null || exists == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This upload has already been started");
        }
        JobParameters parameters = new JobParametersBuilder()
                .addString("uploadId", uploadId)
                .addString("launchId", UUID.randomUUID().toString())
                .toJobParameters();
        try {
            JobExecution execution = jobLauncher.run(job, parameters);
            jdbc.update("UPDATE employee_import_uploads SET execution_id=? WHERE upload_id=?", execution.getId(),
                    uploadId);
            return jobResponse(execution);
        } catch (Exception exception) {
            jdbc.update(
                    "UPDATE employee_import_uploads SET launch_claimed=FALSE WHERE upload_id=? AND execution_id IS NULL",
                    uploadId);
            throw exception;
        }
    }

    @GetMapping("/jobs/{executionId}")
    public Map<String, Object> status(@PathVariable long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null || !job.getName().equals(execution.getJobInstance().getJobName()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import job not found");
        return jobResponse(execution);
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> history() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (var instance : jobExplorer.findJobInstancesByJobName(job.getName(), 0, 100)) {
            for (JobExecution execution : jobExplorer.getJobExecutions(instance))
                results.add(jobResponse(execution));
        }
        return results;
    }

    @GetMapping(value = "/jobs/{executionId}/errors", produces = "text/csv")
    public ResponseEntity<byte[]> errors(@PathVariable long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null || !job.getName().equals(execution.getJobInstance().getJobName()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import job not found");
        StringBuilder csv = new StringBuilder("rowNumber,rowData,error\r\n");
        jdbc.query(
                "SELECT source_row_number,row_data,error_message FROM employee_import_errors WHERE execution_id=? ORDER BY source_row_number",
                rs -> {
                    csv.append(rs.getLong(1)).append(',').append(quote(rs.getString(2))).append(',')
                            .append(quote(rs.getString(3))).append("\r\n");
                }, executionId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("employee-import-errors-" + executionId + ".csv").build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> jobResponse(JobExecution execution) {
        BatchStatus current = execution.getStatus();
        String status = current == BatchStatus.STARTING ? "STARTING"
                : current == BatchStatus.STARTED ? "STARTED"
                        : current == BatchStatus.COMPLETED ? "COMPLETED" : "FAILED";
        String uploadId = execution.getJobParameters().getString("uploadId");
        String filename = jdbc.query("SELECT original_filename FROM employee_import_uploads WHERE upload_id=?",
                rs -> rs.next() ? rs.getString(1) : "", uploadId);
        Integer errors = jdbc.queryForObject("SELECT COUNT(*) FROM employee_import_errors WHERE execution_id=?",
                Integer.class, execution.getId());
        Integer imported = jdbc.queryForObject("SELECT imported_count FROM employee_import_uploads WHERE upload_id=?",
                Integer.class, uploadId);
        String message = "FAILED".equals(status)
                ? "The job stopped before completing. Check the CSV header and file format."
                : "";
        return Map.of("executionId", execution.getId(), "uploadId", uploadId, "filename", filename,
                "status", status, "importedCount", imported == null ? 0 : imported,
                "errorCount", errors == null ? 0 : errors,
                "startedAt", execution.getStartTime() == null ? "" : execution.getStartTime().toString(),
                "finishedAt", execution.getEndTime() == null ? "" : execution.getEndTime().toString(),
                "message", message);
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    public record StartImportRequest(String uploadId) {
    }
}

package com.example.ems.batch;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/admin/employees")
@ConditionalOnProperty(name = "app.employee-export.enabled", havingValue = "true", matchIfMissing = true)
public class EmployeeExportController {
    private static final String SELECT_EMPLOYEES = "SELECT id, name, department, salary FROM employees ORDER BY id";
    private final JdbcTemplate jdbc;

    public EmployeeExportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping(value = "/template", produces = "text/csv")
    public ResponseEntity<byte[]> template() {
        byte[] csv = "name,department,salary\r\n".getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("employee-import-template.csv").build().toString())
                .body(csv);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> csv() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("employees.csv").build().toString())
                .body(output -> {
                    output.write("id,name,department,salary\r\n".getBytes(StandardCharsets.UTF_8));
                    streamEmployees(output, rs -> writeCsvRow(output, rs));
                });
    }

    @GetMapping(value = "/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<StreamingResponseBody> excel() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("employees.xlsx").build().toString())
                .body(output -> {
                    try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                        workbook.setCompressTempFiles(true);
                        Sheet sheet = workbook.createSheet("Employees");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("id");
                        header.createCell(1).setCellValue("name");
                        header.createCell(2).setCellValue("department");
                        header.createCell(3).setCellValue("salary");
                        final int[] rowNumber = {1};
                        streamEmployees(null, rs -> {
                            Row row = sheet.createRow(rowNumber[0]++);
                            row.createCell(0).setCellValue(rs.getLong("id"));
                            row.createCell(1).setCellValue(rs.getString("name"));
                            row.createCell(2).setCellValue(rs.getString("department"));
                            row.createCell(3).setCellValue(rs.getBigDecimal("salary").doubleValue());
                        });
                        workbook.write(output);
                        output.flush();
                    }
                });
    }

    private void streamEmployees(OutputStream output, EmployeeRowConsumer consumer) throws IOException {
        try {
            jdbc.execute(connection -> {
                PreparedStatement statement = connection.prepareStatement(SELECT_EMPLOYEES,
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                boolean mysql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
                statement.setFetchSize(mysql ? Integer.MIN_VALUE : 1000);
                return statement;
            }, (PreparedStatementCallback<Void>) statement -> {
                try (ResultSet rs = statement.executeQuery()) {
                    int rowCount = 0;
                    while (rs.next()) {
                        consumer.accept(rs);
                        if (output != null && ++rowCount % 1000 == 0) output.flush();
                    }
                    if (output != null) output.flush();
                } catch (IOException exception) {
                    throw new SQLException("Unable to write employee export", exception);
                }
                return null;
            });
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new IOException("Unable to stream employee export", exception);
        }
    }

    private static void writeCsvRow(OutputStream output, ResultSet rs) throws SQLException, IOException {
        String row = rs.getLong("id") + "," + csv(rs.getString("name")) + "," + csv(rs.getString("department"))
                + "," + rs.getBigDecimal("salary") + "\r\n";
        output.write(row.getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String value) {
        String safeValue = value == null ? "" : value;
        if (!safeValue.isEmpty() && "=+-@\t\r".indexOf(safeValue.charAt(0)) >= 0) safeValue = "'" + safeValue;
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    @FunctionalInterface
    private interface EmployeeRowConsumer {
        void accept(ResultSet row) throws SQLException, IOException;
    }
}

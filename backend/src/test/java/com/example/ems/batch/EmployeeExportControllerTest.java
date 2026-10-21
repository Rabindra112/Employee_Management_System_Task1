package com.example.ems.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class EmployeeExportControllerTest {
    private EmbeddedDatabase database;
    private EmployeeExportController controller;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE employees (id BIGINT PRIMARY KEY, name VARCHAR(100), department VARCHAR(80), salary DECIMAL(12,2))");
        jdbc.update("INSERT INTO employees VALUES (1, ?, ?, ?)", "=SUM(1,2)", "Engineering", new java.math.BigDecimal("45000.00"));
        jdbc.update("INSERT INTO employees VALUES (2, ?, ?, ?)", "Sam O'Neil", "People", new java.math.BigDecimal("52000.50"));
        controller = new EmployeeExportController(jdbc);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void templateContainsEmployeeImportHeaders() {
        assertEquals("name,department,salary\r\n", new String(controller.template().getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void csvExportStreamsAllFieldsAndEscapesSpreadsheetFormulas() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        controller.csv().getBody().writeTo(output);
        String csv = output.toString(StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("id,name,department,salary\r\n"));
        assertTrue(csv.contains("1,\"'=SUM(1,2)\",\"Engineering\",45000.00\r\n"));
        assertTrue(csv.contains("2,\"Sam O'Neil\",\"People\",52000.50\r\n"));
    }

    @Test
    void excelExportProducesReadableWorkbookWithEmployeeRows() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ResponseEntity<StreamingResponseBody> response = controller.excel();
        response.getBody().writeTo(output);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheet("Employees");
            assertEquals("name", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Sam O'Neil", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals(52000.5, sheet.getRow(2).getCell(3).getNumericCellValue());
        }
    }
}

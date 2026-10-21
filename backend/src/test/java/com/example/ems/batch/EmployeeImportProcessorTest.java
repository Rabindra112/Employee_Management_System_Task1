package com.example.ems.batch;

import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeImportProcessorTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private EmployeeImportProcessor processor;

    @BeforeEach void setUp() { processor = new EmployeeImportProcessor(FACTORY.getValidator()); }
    @AfterAll static void close() { FACTORY.close(); }

    @Test void acceptsAndNormalizesAValidRecord() {
        EmployeeImportRow row = processor.process(new EmployeeImportProcessor.Input(2, " Alice , IT ,45000.00"));
        assertTrue(row.valid());
        assertEquals("Alice", row.employee().name());
        assertEquals(new BigDecimal("45000.00"), row.employee().salary());
    }

    @Test void reportsExistingEmployeeRequestValidationErrors() {
        EmployeeImportRow row = processor.process(new EmployeeImportProcessor.Input(2, " , ,0"));
        assertFalse(row.valid());
        assertTrue(String.join(";", row.errors()).contains("Name is required"));
        assertTrue(String.join(";", row.errors()).contains("Salary must be greater than zero"));
    }

    @Test void reportsRepeatedRowsWithinTheUploadedFile() {
        assertTrue(processor.process(new EmployeeImportProcessor.Input(2, "Alice,IT,45000")).valid());
        EmployeeImportRow duplicate = processor.process(new EmployeeImportProcessor.Input(3, "alice,it,45000.00"));
        assertEquals(java.util.List.of("Duplicate employee row in this CSV"), duplicate.errors());
    }

    @Test void reportsWrongColumnCountAndMalformedQuotesAsRowErrors() {
        assertFalse(processor.process(new EmployeeImportProcessor.Input(2, "Alice,IT")).valid());
        assertFalse(processor.process(new EmployeeImportProcessor.Input(3, "\"Alice,IT,45000")).valid());
    }
}

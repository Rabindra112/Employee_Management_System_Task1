package com.example.ems.batch;

import com.example.ems.dto.EmployeeRequest;
import java.util.List;

public record EmployeeImportRow(long rowNumber, String source, EmployeeRequest employee, List<String> errors) {
    public boolean valid() {
        return errors.isEmpty();
    }
}

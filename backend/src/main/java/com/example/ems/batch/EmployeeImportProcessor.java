package com.example.ems.batch;

import com.example.ems.dto.EmployeeRequest;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.batch.item.ItemProcessor;

public class EmployeeImportProcessor implements ItemProcessor<EmployeeImportProcessor.Input, EmployeeImportRow> {
    private final Validator validator;
    private final Set<String> seen = new HashSet<>();

    public EmployeeImportProcessor(Validator validator) { this.validator = validator; }

    @Override
    public EmployeeImportRow process(Input input) {
        List<String> errors = new ArrayList<>();
        List<String> values;
        try {
            values = EmployeeCsvParser.parse(input.source());
        } catch (IllegalArgumentException exception) {
            return new EmployeeImportRow(input.rowNumber(), input.source(), null, List.of(exception.getMessage()));
        }
        if (values.size() != 3) {
            return new EmployeeImportRow(input.rowNumber(), input.source(), null,
                    List.of("Expected exactly 3 columns: name,department,salary"));
        }
        BigDecimal salary = null;
        try { salary = new BigDecimal(values.get(2).trim()); }
        catch (NumberFormatException exception) { errors.add("salary: must be a valid number"); }
        EmployeeRequest request = new EmployeeRequest(values.get(0).trim(), values.get(1).trim(), salary);
        validator.validate(request).forEach(violation -> errors.add(
                violation.getPropertyPath() + ": " + violation.getMessage()));
        if (errors.isEmpty()) {
            String key = request.name().toLowerCase(Locale.ROOT) + "\u0000" + request.department().toLowerCase(Locale.ROOT)
                    + "\u0000" + request.salary().stripTrailingZeros().toPlainString();
            if (!seen.add(key)) errors.add("Duplicate employee row in this CSV");
        }
        return new EmployeeImportRow(input.rowNumber(), input.source(), request, errors.stream().distinct().toList());
    }

    public record Input(long rowNumber, String source) {}
}

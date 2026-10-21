package com.example.ems.batch;

import java.util.ArrayList;
import java.util.List;

final class EmployeeCsvParser {
    private EmployeeCsvParser() {
    }

    static List<String> parse(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else
                    quoted = !quoted;
            } else if (c == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else
                field.append(c);
        }
        if (quoted)
            throw new IllegalArgumentException("Unclosed quoted CSV field");
        fields.add(field.toString());
        return fields;
    }
}

package io.prodaccess.common.param;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal RFC 4180 parser: first record is the header; quotes and embedded newlines are supported. */
public final class Csv {
    private Csv() {}

    public static List<Map<String, String>> parse(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                records.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("unterminated quote in CSV");
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            records.add(row);
        }
        records.removeIf(r -> r.size() == 1 && r.get(0).isBlank());
        if (records.isEmpty()) return List.of();
        List<String> header = records.get(0).stream().map(String::trim).toList();
        List<Map<String, String>> out = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> rec = records.get(r);
            if (rec.size() != header.size()) {
                throw new IllegalArgumentException("row " + r + " has " + rec.size() + " fields, expected " + header.size());
            }
            Map<String, String> m = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) m.put(header.get(c), rec.get(c));
            out.add(m);
        }
        return out;
    }
}

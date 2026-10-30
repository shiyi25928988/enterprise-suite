package io.github.shiyi.auth.user.sync;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M10: plain HR-export CSV as a directory source. The most offline-friendly
 * provider there is — HR systems that speak neither WeCom nor DingTalk still
 * export spreadsheets, and this turns one into a DirectorySnapshot.
 *
 * <p>File format (header row mandatory, column order free, unknown columns
 * ignored, quoting per RFC 4180 — embedded commas/newlines in names work):</p>
 * <pre>
 * type,external_id,name,parent_external_id,sort_no,mobile,employee_no,org_external_id
 * org,100,总部,,,0,,
 * org,110,制造中心,100,,1,,
 * user,u001,张三,,,,E001,110
 * </pre>
 *
 * <p>The file is re-read on every {@link #fetch()} — dropping a fresh export
 * and hitting the sync API is the whole workflow; nothing is cached and the
 * read is side-effect free per the SPI contract.</p>
 */
public class CsvDirectory implements DirectoryProvider {

    private final SyncProperties.Csv props;

    public CsvDirectory(SyncProperties.Csv props) {
        this.props = props;
    }

    @Override
    public String source() {
        return "csv";
    }

    @Override
    public DirectorySnapshot fetch() throws Exception {
        Path file = Path.of(props.getPath());
        if (!Files.isReadable(file)) {
            throw new IOException("CSV file not readable: " + file.toAbsolutePath());
        }
        Charset charset = props.getCharset() == null || props.getCharset().isBlank()
                ? StandardCharsets.UTF_8
                : Charset.forName(props.getCharset());

        List<ExternalOrg> orgs = new ArrayList<>();
        List<ExternalUser> users = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file, charset)) {
            String headerLine = skipBom(reader.readLine());
            if (headerLine == null || headerLine.isBlank()) {
                throw new IOException("CSV file is empty: " + file.toAbsolutePath());
            }
            Map<String, Integer> cols = headerIndex(headerLine);
            String[] required = {"type", "external_id", "name"};
            for (String r : required) {
                if (!cols.containsKey(r)) {
                    throw new IOException("CSV header missing required column '" + r
                            + "': " + headerLine);
                }
            }

            int lineNo = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String physical = line;
                // RFC 4180 quoting: a field may contain the record separator.
                while (isTruncated(line) && (line = reader.readLine()) != null) {
                    line = physical + "\n" + line;
                    physical = line;
                }
                if (line.isBlank()) {
                    continue;
                }
                List<String> f = fields(line);
                String type = value(f, cols, "type");
                String externalId = value(f, cols, "external_id");
                String name = value(f, cols, "name");
                if (externalId == null || externalId.isBlank()) {
                    continue; // nothing to match on — silently skip blank key rows
                }
                switch (type == null ? "" : type.trim().toLowerCase()) {
                    case "org" -> orgs.add(new ExternalOrg(externalId, name,
                            value(f, cols, "parent_external_id"),
                            parseInt(value(f, cols, "sort_no"))));
                    case "user" -> users.add(new ExternalUser(externalId, name,
                            value(f, cols, "mobile"),
                            value(f, cols, "employee_no"),
                            value(f, cols, "org_external_id")));
                    default -> throw new IOException(
                            "CSV line " + lineNo + ": unknown type '" + type + "'");
                }
            }
        }
        return new DirectorySnapshot(orgs, users);
    }

    // ---- CSV plumbing ----

    private static String skipBom(String s) {
        return s == null ? null : s.strip().replaceFirst("^\uFEFF", "");
    }

    private static Map<String, Integer> headerIndex(String headerLine) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        List<String> f = fields(headerLine);
        for (int i = 0; i < f.size(); i++) {
            idx.put(f.get(i).strip().toLowerCase(), i);
        }
        return idx;
    }

    /** One CSV record split on unquoted commas; "" and quoted "..." both resolve. */
    static List<String> fields(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.stream().map(String::strip).toList();
    }

    /** True when quote count is odd — the record continues on the next line. */
    private static boolean isTruncated(String line) {
        long q = line.chars().filter(c -> c == '"').count();
        return q % 2 != 0;
    }

    private static String value(List<String> f, Map<String, Integer> cols, String col) {
        Integer i = cols.get(col);
        if (i == null || i >= f.size()) {
            return null;
        }
        String v = f.get(i);
        return v == null || v.isBlank() ? null : v;
    }

    private static Integer parseInt(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

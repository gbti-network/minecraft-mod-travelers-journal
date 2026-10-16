package com.gbti;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading and writing the journal files. Text is UTF-8. A file is replaced by writing a temporary
 * file and moving it into place, with the previous version kept beside it as .bak, so a failed
 * write never leaves a half-written journal.
 */
final class JournalFiles {
    private JournalFiles() {
    }

    // Lines the way earlier releases read them: no line for a final newline, bad bytes replaced
    static List<String> readLines(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lines.add(line);
            }
        }
        return lines;
    }

    // key=value lines. As in earlier releases, values are kept as written and a later line wins
    static Map<String, String> readSettings(Path file) throws IOException {
        Map<String, String> settings = new LinkedHashMap<>();
        for (String line : readLines(file)) {
            int equals = line.indexOf('=');
            if (equals > 0) {
                settings.put(line.substring(0, equals), line.substring(equals + 1));
            }
        }
        return settings;
    }

    static void writeIfMissing(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }

    static void replace(Path file, String content) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        if (Files.exists(file)) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Rewrites the given keys in place and appends the ones the file lacks; every other line stays
    static void updateSettings(Path file, Map<String, String> values) throws IOException {
        List<String> lines = Files.exists(file) ? readLines(file) : new ArrayList<>();
        Map<String, String> pending = new LinkedHashMap<>(values);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            int equals = line.indexOf('=');
            String key = equals > 0 ? line.substring(0, equals) : null;
            if (key != null && values.containsKey(key)) {
                if (pending.containsKey(key)) {
                    out.add(key + "=" + pending.remove(key));
                }
                // a repeated key is dropped, so the file says one thing and the new value wins
            } else {
                out.add(line);
            }
        }
        pending.forEach((key, value) -> out.add(key + "=" + value));
        replace(file, String.join("\n", out) + "\n");
    }

    static void appendLine(Path file, String line) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}

package org.deepsetk.crud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Exporta e importa una base MySQL a/desde un archivo .sql. */
final class DbBackup {

    private DbBackup() {
    }

    static void exportJdbc(DbConfig cfg, Path file) throws Exception {
        try (Connection connection = cfg.connect(); Statement statement = connection.createStatement()) {
            StringBuilder out = new StringBuilder();
            out.append("-- Respaldo de `").append(cfg.database).append("`\n");
            out.append("SET FOREIGN_KEY_CHECKS=0;\n\n");
            for (String table : tables(statement)) {
                out.append("DROP TABLE IF EXISTS `").append(table).append("`;\n");
                try (ResultSet rs = statement.executeQuery(
                        "SHOW CREATE TABLE `" + table.replace("`", "``") + "`")) {
                    if (rs.next()) {
                        out.append(rs.getString(2)).append(";\n\n");
                    }
                }
                StringBuilder rows = new StringBuilder();
                try (ResultSet rs = statement.executeQuery(
                        "SELECT * FROM `" + table.replace("`", "``") + "`")) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columns = meta.getColumnCount();
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) {
                            rows.append(",\n");
                        }
                        first = false;
                        rows.append('(');
                        for (int i = 1; i <= columns; i++) {
                            if (i > 1) {
                                rows.append(", ");
                            }
                            rows.append(literal(rs.getObject(i)));
                        }
                        rows.append(')');
                    }
                }
                if (rows.length() > 0) {
                    out.append("INSERT INTO `").append(table).append("` VALUES\n")
                            .append(rows).append(";\n\n");
                }
            }
            out.append("SET FOREIGN_KEY_CHECKS=1;\n");
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        }
    }

    static int importJdbc(DbConfig cfg, Path file) throws Exception {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<String> statements = splitStatements(text);
        int count = 0;
        try (Connection connection = cfg.connect(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
                count++;
            }
        }
        return count;
    }

    static boolean hasDumpTool() {
        return toolAvailable("mysqldump");
    }

    static boolean hasMysqlTool() {
        return toolAvailable("mysql");
    }

    static void exportDump(DbConfig cfg, Path file) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("mysqldump",
                "--host=" + cfg.host, "--port=" + cfg.port, "--user=" + cfg.user,
                "--single-transaction", "--routines", "--events", cfg.database);
        builder.environment().put("MYSQL_PWD", cfg.password);
        builder.redirectOutput(file.toFile());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("mysqldump tardo demasiado.");
        }
        if (process.exitValue() != 0) {
            throw new IOException("mysqldump fallo (codigo " + process.exitValue()
                    + "). Comprueba que este en el PATH y las credenciales.");
        }
    }

    static void importDump(DbConfig cfg, Path file) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("mysql",
                "--host=" + cfg.host, "--port=" + cfg.port, "--user=" + cfg.user,
                cfg.database);
        builder.environment().put("MYSQL_PWD", cfg.password);
        builder.redirectInput(file.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("mysql tardo demasiado.");
        }
        if (process.exitValue() != 0) {
            throw new IOException("mysql fallo (codigo " + process.exitValue() + "): " + output.strip());
        }
    }

    private static List<String> tables(Statement statement) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    private static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            StringBuilder sb = new StringBuilder("0x");
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        String text = value.toString();
        return "'" + text.replace("\\", "\\\\").replace("'", "''")
                .replace("\n", "\\n").replace("\r", "\\r") + "'";
    }

    private static boolean toolAvailable(String tool) {
        try {
            Process process = new ProcessBuilder(tool, "--version")
                    .redirectErrorStream(true).start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Divide un dump en sentencias respetando comillas y comentarios. */
    static List<String> splitStatements(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    current.append('\n');
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (quote != 0) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '-' && next == '-') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '#') {
                lineComment = true;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == ';') {
                addStatement(out, current);
                continue;
            }
            current.append(c);
        }
        addStatement(out, current);
        return out;
    }

    private static void addStatement(List<String> out, StringBuilder current) {
        String sql = current.toString().strip();
        if (!sql.isEmpty()) {
            out.add(sql);
        }
        current.setLength(0);
    }
}

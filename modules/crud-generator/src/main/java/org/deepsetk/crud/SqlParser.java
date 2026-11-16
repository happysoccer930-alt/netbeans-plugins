package org.deepsetk.crud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser minimo de CREATE TABLE de MySQL. */
final class SqlParser {

    private static final Pattern CREATE = Pattern.compile(
            "(?is)\\bcreate\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?"
            + "(`[^`]+`|\"[^\"]+\"|\\[[^\\]]+\\]|[\\w$.]+)\\s*\\(");
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "record", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "try", "var", "void", "volatile", "while", "yield",
            "sealed", "permits", "non-sealed", "true", "false", "null");

    private SqlParser() {
    }

    static SqlTable parse(String ddl) {
        if (ddl == null || ddl.isBlank()) {
            throw new IllegalArgumentException("Pega un CREATE TABLE de MySQL.");
        }
        String sql = stripComments(ddl);
        Matcher matcher = CREATE.matcher(sql);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No encuentro un CREATE TABLE. Ejemplo: CREATE TABLE usuarios (...)");
        }
        String tableName = simpleIdentifier(matcher.group(1));
        int open = matcher.end() - 1;
        int close = matchingParen(sql, open);
        if (close < 0) {
            throw new IllegalArgumentException("Falta el paréntesis de cierre del CREATE TABLE.");
        }
        String body = sql.substring(open + 1, close);
        List<String> definitions = splitTopLevel(body);

        Set<String> tableKeys = new HashSet<>();
        for (String definition : definitions) {
            String upper = definition.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (upper.startsWith("PRIMARY KEY")) {
                collectKeys(definition, tableKeys);
            } else if (upper.startsWith("CONSTRAINT") && upper.contains("PRIMARY KEY")) {
                collectKeys(definition, tableKeys);
            }
        }

        List<SqlTable.Column> columns = new ArrayList<>();
        for (String definition : definitions) {
            String text = definition.trim();
            if (text.isEmpty()) {
                continue;
            }
            String upper = text.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (upper.startsWith("PRIMARY KEY")
                    || upper.startsWith("UNIQUE")
                    || upper.startsWith("KEY ")
                    || upper.startsWith("KEY(")
                    || upper.startsWith("INDEX")
                    || upper.startsWith("FOREIGN")
                    || upper.startsWith("CONSTRAINT")
                    || upper.startsWith("CHECK")
                    || upper.startsWith("FULLTEXT")
                    || upper.startsWith("SPATIAL")) {
                continue;
            }
            columns.add(parseColumn(text, tableKeys));
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("No encontré columnas en el CREATE TABLE.");
        }
        return new SqlTable(tableName, columns);
    }

    private static SqlTable.Column parseColumn(String definition, Set<String> tableKeys) {
        int[] pos = {0};
        String sqlName = readIdentifier(definition, pos);
        String typeToken = readType(definition, pos);
        if (sqlName.isEmpty() || typeToken.isEmpty()) {
            throw new IllegalArgumentException("No pude leer la columna: " + definition);
        }
        String upper = definition.toUpperCase(Locale.ROOT);
        String base = typeToken.toUpperCase(Locale.ROOT).replaceAll("\\(.*", "").trim();
        boolean primary = upper.contains("PRIMARY KEY") || containsIgnoreCase(tableKeys, sqlName);
        boolean auto = upper.contains("AUTO_INCREMENT") || "SERIAL".equals(base);
        boolean nullable = !upper.contains("NOT NULL") && !primary;
        String javaType = javaType(base, typeToken);
        return new SqlTable.Column(sqlName, toJavaField(sqlName), base, javaType,
                nullable, auto, primary);
    }

    private static boolean containsIgnoreCase(Set<String> values, String wanted) {
        for (String value : values) {
            if (value.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    private static void collectKeys(String definition, Set<String> keys) {
        Matcher matcher = Pattern.compile("(?is)primary\\s+key\\s*\\(([^)]*)\\)")
                .matcher(definition);
        if (matcher.find()) {
            for (String key : splitTopLevel(matcher.group(1))) {
                String name = simpleIdentifier(key.trim());
                if (!name.isEmpty()) {
                    keys.add(name);
                }
            }
        }
    }

    private static String javaType(String base, String typeToken) {
        String type = typeToken.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return switch (base) {
            case "TINYINT" -> type.contains("(1)") ? "Boolean" : "Integer";
            case "BIT" -> type.equals("BIT(1)") ? "Boolean" : "byte[]";
            case "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "YEAR" -> "Integer";
            case "BIGINT", "SERIAL" -> "Long";
            case "FLOAT" -> "Float";
            case "DOUBLE", "REAL" -> "Double";
            case "DECIMAL", "NUMERIC" -> "BigDecimal";
            case "DATE" -> "LocalDate";
            case "DATETIME", "TIMESTAMP" -> "LocalDateTime";
            case "TIME" -> "LocalTime";
            case "BOOLEAN", "BOOL" -> "Boolean";
            case "BLOB", "TINYBLOB", "MEDIUMBLOB", "LONGBLOB",
                 "BINARY", "VARBINARY" -> "byte[]";
            default -> "String";
        };
    }

    private static String readType(String text, int[] pos) {
        int index = pos[0];
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        int start = index;
        int depth = 0;
        char quote = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                index++;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                index++;
                continue;
            }
            if (c == '(') {
                depth++;
                index++;
                continue;
            }
            if (c == ')') {
                depth--;
                index++;
                continue;
            }
            if (depth == 0 && Character.isWhitespace(c)) {
                break;
            }
            index++;
        }
        pos[0] = index;
        return text.substring(start, index).trim();
    }

    private static String readIdentifier(String text, int[] pos) {
        int index = pos[0];
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        if (index >= text.length()) {
            pos[0] = index;
            return "";
        }
        char first = text.charAt(index);
        if (first == '`' || first == '"' || first == '\'' || first == '[') {
            char close = first == '[' ? ']' : first;
            int start = index + 1;
            index++;
            StringBuilder sb = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == close) {
                    if (index + 1 < text.length() && text.charAt(index + 1) == close) {
                        sb.append(c);
                        index += 2;
                        continue;
                    }
                    index++;
                    break;
                }
                sb.append(c);
                index++;
            }
            pos[0] = index;
            return sb.toString().trim();
        }
        int start = index;
        while (index < text.length() && !Character.isWhitespace(text.charAt(index))
                && text.charAt(index) != '(' && text.charAt(index) != ',') {
            index++;
        }
        pos[0] = index;
        return text.substring(start, index).trim();
    }

    private static int matchingParen(String text, int open) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
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
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '(') {
                depth++;
                current.append(c);
                continue;
            }
            if (c == ')') {
                depth--;
                current.append(c);
                continue;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                out.append(c);
                continue;
            }
            if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
                out.append('\n');
                continue;
            }
            if (c == '#') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
                out.append('\n');
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < text.length()
                        && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                    i++;
                }
                i++;
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String simpleIdentifier(String token) {
        String text = token.trim();
        if (text.startsWith("`") && text.endsWith("`")) {
            text = text.substring(1, text.length() - 1);
        } else if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        } else if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        int dot = text.lastIndexOf('.');
        return dot < 0 ? text : text.substring(dot + 1);
    }

    static String toJavaField(String sqlName) {
        String[] parts = sqlName.split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(Character.toLowerCase(part.charAt(0))).append(part.substring(1));
            } else {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        String name = sb.length() == 0 ? "campo" : sb.toString();
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            name = "_" + name;
        }
        if (KEYWORDS.contains(name)) {
            name = name + "_";
        }
        return name;
    }

    static String toClassName(String tableName) {
        String[] parts = tableName.split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        String name = sb.length() == 0 ? "Tabla" : sb.toString();
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            name = "_" + name;
        }
        if (KEYWORDS.contains(name.toLowerCase(Locale.ROOT))) {
            name = name + "Entidad";
        }
        return name;
    }
}

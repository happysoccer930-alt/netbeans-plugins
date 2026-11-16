package org.deepsetk.crud;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Genera Entity, DAO JDBC, Service y SQL a partir de una tabla. */
final class CrudGenerator {

    record Generated(String entity, String dao, String service, String sql, String notes) {
    }

    private CrudGenerator() {
    }

    /** Genera una clase de conexion JDBC sencilla. */
    static String connectionCode(String pkg, String className, String url, String user, String password) {
        String packageName = pkg == null || pkg.isBlank() ? "com.ejemplo.sistema.conexion" : pkg.trim();
        while (packageName.endsWith(".")) {
            packageName = packageName.substring(0, packageName.length() - 1);
        }
        String name = className == null || className.isBlank() ? "Conexion" : className.trim();
        return """
                package %s;

                import java.sql.Connection;
                import java.sql.DriverManager;
                import java.sql.SQLException;

                /**
                 * Conexion MySQL generada por DeepSetk.
                 * Cambia la URL, el usuario y la clave si hace falta.
                 * Para produccion es mejor leerlos de variables de entorno.
                 */
                public class %s {

                    private static final String URL = "%s";
                    private static final String USUARIO = "%s";
                    private static final String CLAVE = "%s";

                    private %s() {
                    }

                    public static Connection conectar() throws SQLException {
                        return DriverManager.getConnection(URL, USUARIO, CLAVE);
                    }
                }
                """.formatted(packageName, name, escapeJava(url), escapeJava(user),
                escapeJava(password), name);
    }

    static Generated generate(SqlTable table, String basePackage, String entityName) {
        String pkg = basePackage == null ? "" : basePackage.trim();
        while (pkg.endsWith(".")) {
            pkg = pkg.substring(0, pkg.length() - 1);
        }
        if (pkg.isEmpty()) {
            pkg = "com.ejemplo.sistema";
        }
        String entity = entityName == null || entityName.isBlank()
                ? SqlParser.toClassName(table.name) : entityName.trim();

        List<SqlTable.Column> keys = table.primaryKeys();
        List<SqlTable.Column> insertColumns = new ArrayList<>();
        List<SqlTable.Column> updateColumns = new ArrayList<>();
        for (SqlTable.Column column : table.columns) {
            if (!column.autoIncrement) {
                insertColumns.add(column);
            }
            if (!column.primaryKey && !column.autoIncrement) {
                updateColumns.add(column);
            }
        }

        String entityCode = entityCode(pkg, entity, table);
        String daoCode = daoCode(pkg, entity, table, keys, insertColumns, updateColumns);
        String serviceCode = serviceCode(pkg, entity, keys, updateColumns);
        String sqlCode = sqlCode(table, keys, insertColumns, updateColumns);
        String notes = notes(table, keys, updateColumns);
        return new Generated(entityCode, daoCode, serviceCode, sqlCode, notes);
    }

    /**
     * Genera una vista Swing con JTable y botones Nuevo/Editar/Eliminar/
     * Actualizar. Usa el DAO generado.
     */
    static String viewCode(String pkg, String entity, SqlTable table) {
        String base = pkg == null ? "" : pkg.trim();
        while (base.endsWith(".")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            base = "com.ejemplo.sistema";
        }
        List<SqlTable.Column> keys = table.primaryKeys();
        List<SqlTable.Column> updateColumns = new ArrayList<>();
        for (SqlTable.Column column : table.columns) {
            if (!column.primaryKey && !column.autoIncrement) {
                updateColumns.add(column);
            }
        }
        boolean canUpdate = !keys.isEmpty() && !updateColumns.isEmpty();
        boolean canDelete = !keys.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(base).append(".view;\n\n");
        Set<String> imports = new TreeSet<>(Set.of(
                "java.awt.BorderLayout", "java.awt.FlowLayout", "java.awt.GridBagConstraints",
                "java.awt.GridBagLayout", "java.awt.Insets", "java.math.BigDecimal",
                "java.sql.Connection", "java.sql.SQLException", "java.time.LocalDate",
                "java.time.LocalDateTime", "java.time.LocalTime", "java.util.List",
                "javax.swing.JButton", "javax.swing.JLabel", "javax.swing.JOptionPane",
                "javax.swing.JPanel", "javax.swing.JScrollPane", "javax.swing.JTable",
                "javax.swing.JTextField", "javax.swing.table.DefaultTableModel",
                base + ".dao." + entity + "DAO", base + ".model." + entity));
        appendImports(sb, imports);
        sb.append("/** Vista CRUD con JTable generada para `").append(table.name).append("`. */\n");
        sb.append("public class ").append(entity).append("View extends JPanel {\n\n");
        sb.append("    private final ").append(entity).append("DAO dao;\n");
        sb.append("    private final DefaultTableModel model;\n");
        sb.append("    private final JTable table;\n\n");

        sb.append("    public ").append(entity).append("View(Connection connection) {\n");
        sb.append("        this.dao = new ").append(entity).append("DAO(connection);\n");
        sb.append("        this.model = new DefaultTableModel(new Object[]{");
        for (int i = 0; i < table.columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(escapeString(table.columns.get(i).sqlName)).append('"');
        }
        sb.append("}, 0) {\n");
        sb.append("            @Override\n");
        sb.append("            public boolean isCellEditable(int row, int column) {\n");
        sb.append("                return false;\n");
        sb.append("            }\n");
        sb.append("        };\n");
        sb.append("        this.table = new JTable(model);\n");
        sb.append("        setLayout(new BorderLayout());\n");
        sb.append("        add(new JScrollPane(table), BorderLayout.CENTER);\n");
        sb.append("        JButton add = new JButton(\"Nuevo\");\n");
        if (canUpdate) {
            sb.append("        JButton edit = new JButton(\"Editar\");\n");
        }
        if (canDelete) {
            sb.append("        JButton delete = new JButton(\"Eliminar\");\n");
        }
        sb.append("        JButton refresh = new JButton(\"Actualizar\");\n");
        sb.append("        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));\n");
        sb.append("        buttons.add(add);\n");
        if (canUpdate) {
            sb.append("        buttons.add(edit);\n");
        }
        if (canDelete) {
            sb.append("        buttons.add(delete);\n");
        }
        sb.append("        buttons.add(refresh);\n");
        sb.append("        add(buttons, BorderLayout.SOUTH);\n");
        sb.append("        add.addActionListener(e -> editar(null));\n");
        if (canUpdate) {
            sb.append("        edit.addActionListener(e -> {\n");
            sb.append("            int row = table.getSelectedRow();\n");
            sb.append("            if (row >= 0) {\n");
            sb.append("                editar(fila(row));\n");
            sb.append("            }\n");
            sb.append("        });\n");
        }
        if (canDelete) {
            sb.append("        delete.addActionListener(e -> eliminar());\n");
        }
        sb.append("        refresh.addActionListener(e -> cargar());\n");
        sb.append("        cargar();\n");
        sb.append("    }\n\n");

        sb.append("    private void cargar() {\n");
        sb.append("        model.setRowCount(0);\n");
        sb.append("        try {\n");
        sb.append("            List<").append(entity).append("> lista = dao.listar();\n");
        sb.append("            for (").append(entity).append(" e : lista) {\n");
        sb.append("                model.addRow(new Object[]{");
        for (int i = 0; i < table.columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("e.get").append(capitalize(table.columns.get(i).javaName)).append("()");
        }
        sb.append("});\n");
        sb.append("            }\n");
        sb.append("        } catch (SQLException ex) {\n");
        sb.append("            JOptionPane.showMessageDialog(this, \"Error: \" + ex.getMessage(),"
                + " \"Error\", JOptionPane.ERROR_MESSAGE);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    private ").append(entity).append(" fila(int row) {\n");
        sb.append("        ").append(entity).append(" e = new ").append(entity).append("();\n");
        for (int i = 0; i < table.columns.size(); i++) {
            SqlTable.Column column = table.columns.get(i);
            sb.append("        e.set").append(capitalize(column.javaName)).append("((")
                    .append(column.javaType).append(") model.getValueAt(row, ").append(i)
                    .append("));\n");
        }
        sb.append("        return e;\n");
        sb.append("    }\n\n");

        if (canDelete) {
            sb.append("    private void eliminar() {\n");
            sb.append("        int row = table.getSelectedRow();\n");
            sb.append("        if (row < 0) {\n            return;\n        }\n");
            sb.append("        try {\n");
            sb.append("            dao.eliminar(");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                SqlTable.Column key = keys.get(i);
                int index = table.columns.indexOf(key);
                sb.append("(").append(key.javaType).append(") model.getValueAt(row, ")
                        .append(index).append(")");
            }
            sb.append(");\n");
            sb.append("            cargar();\n");
            sb.append("        } catch (SQLException ex) {\n");
            sb.append("            JOptionPane.showMessageDialog(this, \"Error: \" + ex.getMessage(),"
                    + " \"Error\", JOptionPane.ERROR_MESSAGE);\n");
            sb.append("        }\n");
            sb.append("    }\n\n");
        }

        sb.append("    private void editar(").append(entity).append(" original) {\n");
        sb.append("        JPanel form = new JPanel(new GridBagLayout());\n");
        sb.append("        GridBagConstraints gbc = new GridBagConstraints();\n");
        sb.append("        gbc.insets = new Insets(3, 4, 3, 4);\n");
        sb.append("        gbc.anchor = GridBagConstraints.WEST;\n");
        sb.append("        gbc.gridy = 0;\n");
        for (int i = 0; i < table.columns.size(); i++) {
            SqlTable.Column column = table.columns.get(i);
            String field = "f" + i;
            sb.append("        JTextField ").append(field).append(" = new JTextField(original == null ? \"\" : String.valueOf(original.get")
                    .append(capitalize(column.javaName)).append("()), 20);\n");
            if (!"byte[]".equals(column.javaType)) {
                sb.append("        gbc.gridx = 0;\n        gbc.weightx = 0;\n        gbc.fill = GridBagConstraints.NONE;\n");
                sb.append("        form.add(new JLabel(\"").append(escapeString(column.sqlName))
                        .append("\"), gbc);\n");
                sb.append("        gbc.gridx = 1;\n        gbc.weightx = 1;\n        gbc.fill = GridBagConstraints.HORIZONTAL;\n");
                sb.append("        form.add(").append(field).append(", gbc);\n");
                sb.append("        gbc.gridy++;\n");
            }
        }
        sb.append("        int result = JOptionPane.showConfirmDialog(this, form, original == null ? \"Nuevo\" : \"Editar\","
                + " JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);\n");
        sb.append("        if (result != JOptionPane.OK_OPTION) {\n            return;\n        }\n");
        sb.append("        try {\n");
        sb.append("            ").append(entity).append(" e = original == null ? new ")
                .append(entity).append("() : original;\n");
        for (int i = 0; i < table.columns.size(); i++) {
            SqlTable.Column column = table.columns.get(i);
            sb.append("            e.set").append(capitalize(column.javaName)).append("(")
                    .append(parseExpression(column.javaType, "f" + i + ".getText()")).append(");\n");
        }
        sb.append("            if (original == null) {\n");
        sb.append("                dao.insertar(e);\n");
        sb.append("            }");
        if (canUpdate) {
            sb.append(" else {\n");
            sb.append("                dao.actualizar(e);\n");
            sb.append("            }\n");
        } else {
            sb.append("\n            }\n");
        }
        sb.append("            cargar();\n");
        sb.append("        } catch (RuntimeException ex) {\n");
        sb.append("            JOptionPane.showMessageDialog(this, \"Datos no validos: \" + ex.getMessage(),"
                + " \"Error\", JOptionPane.ERROR_MESSAGE);\n");
        sb.append("        } catch (SQLException ex) {\n");
        sb.append("            JOptionPane.showMessageDialog(this, \"Error: \" + ex.getMessage(),"
                + " \"Error\", JOptionPane.ERROR_MESSAGE);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append(parseHelpers());
        sb.append("}\n");
        return sb.toString();
    }

    private static String parseExpression(String javaType, String text) {
        return switch (javaType) {
            case "Integer" -> "parseInteger(" + text + ")";
            case "Long" -> "parseLong(" + text + ")";
            case "Float" -> "parseFloat(" + text + ")";
            case "Double" -> "parseDouble(" + text + ")";
            case "Boolean" -> "parseBoolean(" + text + ")";
            case "BigDecimal" -> "parseDecimal(" + text + ")";
            case "LocalDate" -> "parseDate(" + text + ")";
            case "LocalDateTime" -> "parseDateTime(" + text + ")";
            case "LocalTime" -> "parseTime(" + text + ")";
            case "byte[]" -> "null";
            default -> text;
        };
    }

    private static String parseHelpers() {
        return """
                    private static Integer parseInteger(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null : Integer.valueOf(text);
                    }

                    private static Long parseLong(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null : Long.valueOf(text);
                    }

                    private static Float parseFloat(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null : Float.valueOf(text);
                    }

                    private static Double parseDouble(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null : Double.valueOf(text);
                    }

                    private static Boolean parseBoolean(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null
                                : (text.equalsIgnoreCase("true") || text.equals("1"));
                    }

                    private static BigDecimal parseDecimal(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null : new BigDecimal(text);
                    }

                    private static LocalDate parseDate(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null : LocalDate.parse(text);
                    }

                    private static LocalDateTime parseDateTime(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null : LocalDateTime.parse(text);
                    }

                    private static LocalTime parseTime(String value) {
                        String text = value == null ? "" : value.trim();
                        return text.isEmpty() ? null : LocalTime.parse(text);
                    }
                """;
    }

    private static String entityCode(String pkg, String entity, SqlTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".model;\n\n");
        Set<String> imports = new TreeSet<>();
        for (SqlTable.Column column : table.columns) {
            switch (column.javaType) {
                case "BigDecimal" -> imports.add("java.math.BigDecimal");
                case "LocalDate" -> imports.add("java.time.LocalDate");
                case "LocalDateTime" -> imports.add("java.time.LocalDateTime");
                case "LocalTime" -> imports.add("java.time.LocalTime");
                default -> {
                }
            }
        }
        appendImports(sb, imports);
        sb.append("/** Entidad generada para la tabla `").append(table.name).append("`. */\n");
        sb.append("public class ").append(entity).append(" {\n\n");
        for (SqlTable.Column column : table.columns) {
            sb.append("    private ").append(column.javaType).append(' ')
                    .append(column.javaName).append(";\n");
        }
        sb.append("\n    public ").append(entity).append("() {\n    }\n\n");

        sb.append("    public ").append(entity).append('(');
        for (int i = 0; i < table.columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            SqlTable.Column column = table.columns.get(i);
            sb.append(column.javaType).append(' ').append(column.javaName);
        }
        sb.append(") {\n");
        for (SqlTable.Column column : table.columns) {
            sb.append("        this.").append(column.javaName).append(" = ")
                    .append(column.javaName).append(";\n");
        }
        sb.append("    }\n");

        for (SqlTable.Column column : table.columns) {
            String cap = capitalize(column.javaName);
            sb.append("\n    public ").append(column.javaType).append(" get").append(cap)
                    .append("() {\n        return ").append(column.javaName).append(";\n    }\n");
            sb.append("\n    public void set").append(cap).append('(')
                    .append(column.javaType).append(' ').append(column.javaName)
                    .append(") {\n        this.").append(column.javaName).append(" = ")
                    .append(column.javaName).append(";\n    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String daoCode(String pkg, String entity, SqlTable table,
            List<SqlTable.Column> keys, List<SqlTable.Column> insertColumns,
            List<SqlTable.Column> updateColumns) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".dao;\n\n");
        Set<String> imports = new TreeSet<>(Set.of(
                "java.sql.Connection", "java.sql.PreparedStatement", "java.sql.ResultSet",
                "java.sql.SQLException", "java.sql.Statement", "java.util.ArrayList",
                "java.util.List", "java.util.Optional", pkg + ".model." + entity));
        appendImports(sb, imports);
        sb.append("/** DAO generado para la tabla `").append(table.name).append("`. */\n");
        sb.append("public class ").append(entity).append("DAO {\n\n");
        sb.append("    private final Connection connection;\n\n");
        sb.append("    public ").append(entity).append("DAO(Connection connection) {\n")
                .append("        this.connection = connection;\n    }\n");

        sb.append("\n    public void insertar(").append(entity)
                .append(" objeto) throws SQLException {\n");
        String insertSql = insertSql(table, insertColumns);
        sb.append("        String sql = \"").append(escapeString(insertSql)).append("\";\n");
        SqlTable.Column autoKey = autoKey(table);
        boolean generated = autoKey != null
                && ("Integer".equals(autoKey.javaType) || "Long".equals(autoKey.javaType));
        if (generated) {
            sb.append("        try (PreparedStatement ps = connection.prepareStatement(sql, "
                    + "Statement.RETURN_GENERATED_KEYS)) {\n");
        } else {
            sb.append("        try (PreparedStatement ps = connection.prepareStatement(sql)) {\n");
        }
        for (int i = 0; i < insertColumns.size(); i++) {
            sb.append("            ").append(parameterSet(insertColumns.get(i), i + 1)).append('\n');
        }
        sb.append("            ps.executeUpdate();\n");
        if (generated) {
            sb.append("            try (ResultSet claves = ps.getGeneratedKeys()) {\n")
                    .append("                if (claves.next()) {\n")
                    .append("                    objeto.set").append(capitalize(autoKey.javaName))
                    .append("(claves.get").append("Integer".equals(autoKey.javaType) ? "Int" : "Long")
                    .append("(1));\n")
                    .append("                }\n")
                    .append("            }\n");
        }
        sb.append("        }\n    }\n");

        if (!keys.isEmpty() && !updateColumns.isEmpty()) {
            sb.append("\n    public void actualizar(").append(entity)
                    .append(" objeto) throws SQLException {\n")
                    .append("        String sql = \"").append(escapeString(updateSql(table, keys, updateColumns)))
                    .append("\";\n")
                    .append("        try (PreparedStatement ps = connection.prepareStatement(sql)) {\n");
            int index = 1;
            for (SqlTable.Column column : updateColumns) {
                sb.append("            ").append(parameterSet(column, index++)).append('\n');
            }
            for (SqlTable.Column key : keys) {
                sb.append("            ps.setObject(").append(index++).append(", objeto.get")
                        .append(capitalize(key.javaName)).append("());\n");
            }
            sb.append("            ps.executeUpdate();\n        }\n    }\n");
        }

        if (!keys.isEmpty()) {
            sb.append("\n    public void eliminar(").append(keyParameters(keys))
                    .append(") throws SQLException {\n")
                    .append("        String sql = \"").append(escapeString(deleteSql(table, keys)))
                    .append("\";\n")
                    .append("        try (PreparedStatement ps = connection.prepareStatement(sql)) {\n");
            for (int i = 0; i < keys.size(); i++) {
                sb.append("            ps.setObject(").append(i + 1).append(", ")
                        .append(keys.get(i).javaName).append(");\n");
            }
            sb.append("            ps.executeUpdate();\n        }\n    }\n");

            sb.append("\n    public Optional<").append(entity).append("> buscarPorId(")
                    .append(keyParameters(keys)).append(") throws SQLException {\n")
                    .append("        String sql = \"").append(escapeString(selectByIdSql(table, keys)))
                    .append("\";\n")
                    .append("        try (PreparedStatement ps = connection.prepareStatement(sql)) {\n");
            for (int i = 0; i < keys.size(); i++) {
                sb.append("            ps.setObject(").append(i + 1).append(", ")
                        .append(keys.get(i).javaName).append(");\n");
            }
            sb.append("            try (ResultSet rs = ps.executeQuery()) {\n")
                    .append("                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();\n")
                    .append("            }\n        }\n    }\n");
        }

        sb.append("\n    public List<").append(entity).append("> listar() throws SQLException {\n")
                .append("        String sql = \"").append(escapeString(selectAllSql(table, keys)))
                .append("\";\n")
                .append("        List<").append(entity).append("> lista = new ArrayList<>();\n")
                .append("        try (PreparedStatement ps = connection.prepareStatement(sql);\n")
                .append("                ResultSet rs = ps.executeQuery()) {\n")
                .append("            while (rs.next()) {\n")
                .append("                lista.add(mapear(rs));\n")
                .append("            }\n")
                .append("        }\n")
                .append("        return lista;\n    }\n");

        sb.append("\n    private ").append(entity).append(" mapear(ResultSet rs) throws SQLException {\n")
                .append("        ").append(entity).append(" objeto = new ").append(entity).append("();\n");
        int variable = 0;
        for (SqlTable.Column column : table.columns) {
            variable = appendRead(sb, column, variable);
        }
        sb.append("        return objeto;\n    }\n}\n");
        return sb.toString();
    }

    private static String serviceCode(String pkg, String entity,
            List<SqlTable.Column> keys, List<SqlTable.Column> updateColumns) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".service;\n\n");
        Set<String> imports = new TreeSet<>(Set.of(
                "java.sql.Connection", "java.sql.SQLException", "java.util.List",
                "java.util.Optional", pkg + ".dao." + entity + "DAO",
                pkg + ".model." + entity));
        appendImports(sb, imports);
        sb.append("/** Servicio generado para ").append(entity).append(". */\n");
        sb.append("public class ").append(entity).append("Service {\n\n");
        sb.append("    private final ").append(entity).append("DAO dao;\n\n");
        sb.append("    public ").append(entity).append("Service(Connection connection) {\n")
                .append("        this.dao = new ").append(entity).append("DAO(connection);\n")
                .append("    }\n\n");
        sb.append("    public void guardar(").append(entity)
                .append(" objeto) throws SQLException {\n");
        if (!keys.isEmpty() && !updateColumns.isEmpty()) {
            SqlTable.Column key = keys.get(0);
            sb.append("        if (objeto.get").append(capitalize(key.javaName))
                    .append("() == null) {\n")
                    .append("            dao.insertar(objeto);\n")
                    .append("        } else {\n")
                    .append("            dao.actualizar(objeto);\n")
                    .append("        }\n");
        } else {
            sb.append("        dao.insertar(objeto);\n");
        }
        sb.append("    }\n\n");
        sb.append("    public List<").append(entity)
                .append("> listar() throws SQLException {\n")
                .append("        return dao.listar();\n    }\n");
        if (!keys.isEmpty()) {
            sb.append("\n    public Optional<").append(entity).append("> buscarPorId(")
                    .append(keyParameters(keys)).append(") throws SQLException {\n")
                    .append("        return dao.buscarPorId(")
                    .append(keyArguments(keys)).append(");\n    }\n");
            sb.append("\n    public void eliminar(").append(keyParameters(keys))
                    .append(") throws SQLException {\n")
                    .append("        dao.eliminar(").append(keyArguments(keys))
                    .append(");\n    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String sqlCode(SqlTable table, List<SqlTable.Column> keys,
            List<SqlTable.Column> insertColumns, List<SqlTable.Column> updateColumns) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Inserta un registro\n").append(insertSql(table, insertColumns)).append(";\n\n");
        if (!keys.isEmpty() && !updateColumns.isEmpty()) {
            sb.append("-- Actualiza por clave\n").append(updateSql(table, keys, updateColumns))
                    .append(";\n\n");
        }
        if (!keys.isEmpty()) {
            sb.append("-- Elimina por clave\n").append(deleteSql(table, keys)).append(";\n\n");
            sb.append("-- Busca por clave\n").append(selectByIdSql(table, keys)).append(";\n\n");
        }
        sb.append("-- Lista todos\n").append(selectAllSql(table, keys)).append(";\n");
        return sb.toString();
    }

    private static String notes(SqlTable table, List<SqlTable.Column> keys,
            List<SqlTable.Column> updateColumns) {
        List<String> notes = new ArrayList<>();
        if (keys.isEmpty()) {
            notes.add("La tabla no tiene PRIMARY KEY: solo se generan insertar y listar.");
        } else if (keys.size() > 1) {
            notes.add("Clave compuesta: " + keys.stream().map(c -> c.sqlName)
                    .collect(Collectors.joining(", ")));
        }
        if (!keys.isEmpty() && updateColumns.isEmpty()) {
            notes.add("No hay columnas editables aparte de la clave: no se genera actualizar.");
        }
        if (keys.size() == 1 && keys.get(0).autoIncrement) {
            notes.add("La clave " + keys.get(0).sqlName + " es AUTO_INCREMENT.");
        }
        return String.join("\n", notes);
    }

    private static SqlTable.Column autoKey(SqlTable table) {
        for (SqlTable.Column column : table.columns) {
            if (column.primaryKey && column.autoIncrement) {
                return column;
            }
        }
        return null;
    }

    private static String insertSql(SqlTable table, List<SqlTable.Column> columns) {
        if (columns.isEmpty()) {
            return "INSERT INTO `" + table.name + "` () VALUES ()";
        }
        String names = columns.stream().map(c -> "`" + c.sqlName + "`")
                .collect(Collectors.joining(", "));
        String values = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO `" + table.name + "` (" + names + ") VALUES (" + values + ")";
    }

    private static String updateSql(SqlTable table, List<SqlTable.Column> keys,
            List<SqlTable.Column> columns) {
        String set = columns.stream().map(c -> "`" + c.sqlName + "` = ?")
                .collect(Collectors.joining(", "));
        return "UPDATE `" + table.name + "` SET " + set + " WHERE " + where(keys);
    }

    private static String deleteSql(SqlTable table, List<SqlTable.Column> keys) {
        return "DELETE FROM `" + table.name + "` WHERE " + where(keys);
    }

    private static String selectByIdSql(SqlTable table, List<SqlTable.Column> keys) {
        return "SELECT * FROM `" + table.name + "` WHERE " + where(keys);
    }

    private static String selectAllSql(SqlTable table, List<SqlTable.Column> keys) {
        String order = keys.isEmpty() ? ""
                : " ORDER BY " + keys.stream().map(c -> "`" + c.sqlName + "`")
                        .collect(Collectors.joining(", "));
        return "SELECT * FROM `" + table.name + "`" + order;
    }

    private static String where(List<SqlTable.Column> keys) {
        return keys.stream().map(c -> "`" + c.sqlName + "` = ?")
                .collect(Collectors.joining(" AND "));
    }

    private static String parameterSet(SqlTable.Column column, int index) {
        String getter = "objeto.get" + capitalize(column.javaName) + "()";
        return switch (column.javaType) {
            case "String" -> "ps.setString(" + index + ", " + getter + ");";
            case "byte[]" -> "ps.setBytes(" + index + ", " + getter + ");";
            case "BigDecimal" -> "ps.setBigDecimal(" + index + ", " + getter + ");";
            default -> "ps.setObject(" + index + ", " + getter + ");";
        };
    }

    private static int appendRead(StringBuilder sb, SqlTable.Column column, int variable) {
        String setter = "objeto.set" + capitalize(column.javaName);
        String columnRef = "\"" + column.sqlName + "\"";
        switch (column.javaType) {
            case "Integer" -> {
                sb.append("        int v").append(variable).append(" = rs.getInt(")
                        .append(columnRef).append(");\n")
                        .append("        ").append(setter).append("(rs.wasNull() ? null : v")
                        .append(variable).append(");\n");
                return variable + 1;
            }
            case "Long" -> {
                sb.append("        long v").append(variable).append(" = rs.getLong(")
                        .append(columnRef).append(");\n")
                        .append("        ").append(setter).append("(rs.wasNull() ? null : v")
                        .append(variable).append(");\n");
                return variable + 1;
            }
            case "Float" -> {
                sb.append("        float v").append(variable).append(" = rs.getFloat(")
                        .append(columnRef).append(");\n")
                        .append("        ").append(setter).append("(rs.wasNull() ? null : v")
                        .append(variable).append(");\n");
                return variable + 1;
            }
            case "Double" -> {
                sb.append("        double v").append(variable).append(" = rs.getDouble(")
                        .append(columnRef).append(");\n")
                        .append("        ").append(setter).append("(rs.wasNull() ? null : v")
                        .append(variable).append(");\n");
                return variable + 1;
            }
            case "Boolean" -> {
                sb.append("        boolean v").append(variable).append(" = rs.getBoolean(")
                        .append(columnRef).append(");\n")
                        .append("        ").append(setter).append("(rs.wasNull() ? null : v")
                        .append(variable).append(");\n");
                return variable + 1;
            }
            case "BigDecimal" -> {
                sb.append("        ").append(setter).append("(rs.getBigDecimal(")
                        .append(columnRef).append("));\n");
                return variable;
            }
            case "LocalDate" -> {
                sb.append("        java.sql.Date v").append(variable).append(" = rs.getDate(")
                        .append(columnRef).append(");\n")
                        .append("        ").append(setter).append("(v").append(variable)
                        .append(" == null ? null : v").append(variable).append(".toLocalDate());\n");
                return variable + 1;
            }
            case "LocalDateTime" -> {
                sb.append("        java.sql.Timestamp v").append(variable).append(" = rs.getTimestamp(")
                        .append(columnRef).append(");\n")
                        .append("        ").append(setter).append("(v").append(variable)
                        .append(" == null ? null : v").append(variable).append(".toLocalDateTime());\n");
                return variable + 1;
            }
            case "LocalTime" -> {
                sb.append("        java.sql.Time v").append(variable).append(" = rs.getTime(")
                        .append(columnRef).append(");\n")
                        .append("        ").append(setter).append("(v").append(variable)
                        .append(" == null ? null : v").append(variable).append(".toLocalTime());\n");
                return variable + 1;
            }
            case "byte[]" -> {
                sb.append("        ").append(setter).append("(rs.getBytes(")
                        .append(columnRef).append("));\n");
                return variable;
            }
            default -> {
                sb.append("        ").append(setter).append("(rs.getString(")
                        .append(columnRef).append("));\n");
                return variable;
            }
        }
    }

    private static String keyParameters(List<SqlTable.Column> keys) {
        return keys.stream().map(c -> c.javaType + " " + c.javaName)
                .collect(Collectors.joining(", "));
    }

    private static String keyArguments(List<SqlTable.Column> keys) {
        return keys.stream().map(c -> c.javaName).collect(Collectors.joining(", "));
    }

    private static void appendImports(StringBuilder sb, Set<String> imports) {
        if (!imports.isEmpty()) {
            for (String value : imports) {
                sb.append("import ").append(value).append(";\n");
            }
            sb.append('\n');
        }
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeJava(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

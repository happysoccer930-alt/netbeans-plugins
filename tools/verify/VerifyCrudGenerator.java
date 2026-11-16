package org.deepsetk.crud;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.openide.filesystems.XMLFileSystem;

/** Verificacion offline del generador CRUD: parser, tipos y codigo generado. */
public final class VerifyCrudGenerator {

    private static int failures;

    public static void main(String[] args) throws Exception {
        String resDir = args[0];
        XMLFileSystem fs = new XMLFileSystem(new File(resDir, "layer.xml").toURI().toURL());
        check(fs.findResource("Actions/Tools/org-deepsetk-crud-generate.instance") != null,
                "accion del generador registrada");
        check(fs.findResource("Menu/Tools/org-deepsetk-crud-generate.shadow") != null,
                "menu Herramientas -> Generar CRUD");
        check(fs.findResource("Toolbars/File/org-deepsetk-crud-generate.shadow") != null,
                "boton del generador en la barra");
        check(CrudIcon.image().getWidth() == 16 && CrudIcon.image().getHeight() == 16,
                "icono del generador 16x16");

        String ddl = """
                -- tabla de ejemplo
                CREATE TABLE `usuarios` (
                  `id` INT AUTO_INCREMENT PRIMARY KEY,
                  `nombre` VARCHAR(100) NOT NULL,
                  `correo` VARCHAR(150),
                  `activo` TINYINT(1) NOT NULL DEFAULT 1,
                  `saldo` DECIMAL(10,2) DEFAULT 0.00,
                  `creado_en` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `foto` LONGBLOB,
                  `estado` ENUM('activo','inactivo') NOT NULL DEFAULT 'activo'
                );
                """;
        SqlTable table = SqlParser.parse(ddl);
        check("usuarios".equals(table.name), "parser: nombre de tabla");
        check(table.columns.size() == 8, "parser: 8 columnas");
        check(table.primaryKeys().size() == 1 && table.primaryKeys().get(0).autoIncrement,
                "parser: PK autoincremental");
        check(type(table, "id").equals("Integer"), "parser: INT -> Integer");
        check(type(table, "nombre").equals("String"), "parser: VARCHAR -> String");
        check(type(table, "activo").equals("Boolean"), "parser: TINYINT(1) -> Boolean");
        check(type(table, "saldo").equals("BigDecimal"), "parser: DECIMAL -> BigDecimal");
        check(type(table, "creado_en").equals("LocalDateTime"), "parser: DATETIME -> LocalDateTime");
        check(type(table, "foto").equals("byte[]"), "parser: LONGBLOB -> byte[]");
        check(type(table, "estado").equals("String"), "parser: ENUM con comas");
        check(column(table, "nombre").nullable == false, "parser: NOT NULL");
        check(column(table, "correo").nullable == true, "parser: columna opcional");

        CrudGenerator.Generated generated = CrudGenerator.generate(table, "com.ejemplo.sistema", "Usuario");
        check(generated.entity().contains("package com.ejemplo.sistema.model;"), "entity: paquete");
        check(generated.entity().contains("public class Usuario"), "entity: clase");
        check(generated.entity().contains("private BigDecimal saldo;"), "entity: BigDecimal");
        check(generated.entity().contains("public LocalDateTime getCreadoEn()"), "entity: getter camelCase");
        check(generated.dao().contains("INSERT INTO `usuarios`"), "dao: INSERT");
        check(generated.dao().contains("UPDATE `usuarios` SET"), "dao: UPDATE");
        check(generated.dao().contains("DELETE FROM `usuarios`"), "dao: DELETE");
        check(generated.dao().contains("getGeneratedKeys"), "dao: clave generada");
        check(generated.dao().contains("objeto.setId(claves.getInt(1))"), "dao: asigna id");
        check(generated.dao().contains("private Usuario mapear(ResultSet rs)"), "dao: mapear");
        check(generated.service().contains("public void guardar(Usuario objeto)"), "service: guardar");
        check(generated.sql().contains("SELECT * FROM `usuarios`"), "sql: SELECT");
        String connection = CrudGenerator.connectionCode("lab_11.pkg4.conexion", "Conexion",
                "jdbc:mysql://localhost:3306/lab?useSSL=false", "root", "a\"b");
        check(connection.contains("public class Conexion"), "conexion: clase");
        check(connection.contains("jdbc:mysql://localhost:3306/lab"), "conexion: URL");
        check(connection.contains("a\\\"b"), "conexion: escapa la clave");
        check(compiles(generated), "el codigo generado compila con javac");

        String composite = """
                CREATE TABLE matriculas (
                  id INT NOT NULL,
                  estudiante_id INT NOT NULL,
                  nota DECIMAL(4,2),
                  PRIMARY KEY (id, estudiante_id)
                );
                """;
        SqlTable matriculas = SqlParser.parse(composite);
        check(matriculas.primaryKeys().size() == 2, "parser: clave compuesta");
        CrudGenerator.Generated mat = CrudGenerator.generate(matriculas, "com.ejemplo", "Matricula");
        check(mat.dao().contains("buscarPorId(Integer id, Integer estudianteId)"),
                "dao: firma con clave compuesta");
        check(mat.dao().contains("WHERE `id` = ? AND `estudiante_id` = ?"),
                "dao: where compuesto");

        String noKey = """
                CREATE TABLE logs (
                  mensaje TEXT,
                  creado_en TIMESTAMP
                );
                """;
        SqlTable logs = SqlParser.parse(noKey);
        check(!logs.hasPrimaryKey(), "parser: tabla sin PK");
        CrudGenerator.Generated log = CrudGenerator.generate(logs, "com.ejemplo", "Log");
        check(!log.dao().contains("actualizar(Log"), "dao sin PK: no actualizar");
        check(!log.dao().contains("eliminar("), "dao sin PK: no eliminar");
        check(!log.dao().contains("buscarPorId"), "dao sin PK: no buscarPorId");
        check(log.notes().contains("PRIMARY KEY"), "nota: avisa de la falta de PK");

        String comments = """
                /* comentario */
                CREATE TABLE simple (
                  id INT PRIMARY KEY, -- clave
                  nombre VARCHAR(20)
                );
                """;
        SqlTable simple = SqlParser.parse(comments);
        check(simple.columns.size() == 2, "parser: ignora comentarios");

        System.out.println(failures == 0 ? "VERIFICACION OK" : "FALLOS: " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static boolean compiles(CrudGenerator.Generated generated) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.out.println("       (sin javac en el runtime)");
            return false;
        }
        Path root = Files.createTempDirectory("crud-verify");
        Path classes = root.resolve("classes");
        Path model = root.resolve("src/com/ejemplo/sistema/model/Usuario.java");
        Path dao = root.resolve("src/com/ejemplo/sistema/dao/UsuarioDAO.java");
        Path service = root.resolve("src/com/ejemplo/sistema/service/UsuarioService.java");
        Path connection = root.resolve("src/com/ejemplo/sistema/conexion/Conexion.java");
        Files.createDirectories(model.getParent());
        Files.createDirectories(dao.getParent());
        Files.createDirectories(service.getParent());
        Files.createDirectories(connection.getParent());
        Files.writeString(model, generated.entity(), StandardCharsets.UTF_8);
        Files.writeString(dao, generated.dao(), StandardCharsets.UTF_8);
        Files.writeString(service, generated.service(), StandardCharsets.UTF_8);
        Files.writeString(connection, CrudGenerator.connectionCode(
                "com.ejemplo.sistema.conexion", "Conexion",
                "jdbc:mysql://localhost:3306/demo", "root", ""), StandardCharsets.UTF_8);
        Files.createDirectories(classes);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjects(
                    model.toFile(), dao.toFile(), service.toFile(), connection.toFile());
            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(classes.toString());
            boolean ok = compiler.getTask(null, files, diagnostics, options, null, units).call();
            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    System.out.println("       " + diagnostic.getKind() + ": "
                            + diagnostic.getMessage(null));
                }
            }
            return ok;
        } finally {
            try (var stream = Files.walk(root)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private static SqlTable.Column column(SqlTable table, String name) {
        for (SqlTable.Column column : table.columns) {
            if (column.sqlName.equals(name)) {
                return column;
            }
        }
        return null;
    }

    private static String type(SqlTable table, String name) {
        SqlTable.Column column = column(table, name);
        return column == null ? "" : column.javaType;
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  OK   " : "  FALLO ") + what);
        if (!ok) {
            failures++;
        }
    }
}

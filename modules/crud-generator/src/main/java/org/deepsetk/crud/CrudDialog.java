package org.deepsetk.crud;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.windows.WindowManager;

/**
 * Dialogo: pega un CREATE TABLE de MySQL y genera Entity, DAO JDBC, Service y
 * el SQL de las operaciones.
 */
final class CrudDialog extends JDialog {

    private static final Pattern PACKAGE = Pattern.compile(
            "[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*");
    private static final Pattern CLASS = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final String SAMPLE = """
            CREATE TABLE usuarios (
              id INT AUTO_INCREMENT PRIMARY KEY,
              nombre VARCHAR(100) NOT NULL,
              correo VARCHAR(150),
              activo TINYINT(1) NOT NULL DEFAULT 1,
              creado_en DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """;
    private static final Color MUTED = new Color(0x8A929C);
    private static final Color OK = new Color(0x6F9F6F);
    private static final Color ERROR = new Color(0xC97A7A);

    private static CrudDialog instance;

    private final JTextArea sql = new JTextArea(8, 80);
    private final JTextField basePackage = new JTextField("com.ejemplo.sistema", 30);
    private final JTextField entityName = new JTextField(20);
    private final JTextField outputFolder = new JTextField(44);
    private final JCheckBox genEntity = new JCheckBox("Entity", true);
    private final JCheckBox genDao = new JCheckBox("DAO", true);
    private final JCheckBox genService = new JCheckBox("Service", true);
    private final JCheckBox genConnection = new JCheckBox("Conexión", true);
    private final JCheckBox genView = new JCheckBox("Vista (JTable)", true);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextArea entityPreview = previewArea(true);
    private final JTextArea daoPreview = previewArea(true);
    private final JTextArea servicePreview = previewArea(true);
    private final JTextArea viewPreview = previewArea(true);
    private final JTextArea sqlPreview = previewArea(false);
    private final JTextArea connectionPreview = previewArea(true);
    private final JTextField connectionPackage = new JTextField(30);
    private final JTextField connectionClass = new JTextField("Conexion", 20);
    private final JTextField dbUrl = new JTextField(
            "jdbc:mysql://localhost:3306/mi_base?useSSL=false&serverTimezone=UTC", 50);
    private final JTextField dbUser = new JTextField("root", 18);
    private final JPasswordField dbPassword = new JPasswordField(18);
    private final JLabel status = new JLabel(" ");
    private final JButton analyze = new JButton("Analizar");
    private final JButton generate = new JButton("Generar archivos");
    private final JButton copy = new JButton("Copiar pestaña");
    private final JButton openFolder = new JButton("Abrir carpeta");
    private final JButton close = new JButton("Cerrar");

    private CrudGenerator.Generated generated;
    private String lastDerivedEntity = "";
    private String lastEntity = "";
    private String lastDao = "";
    private String lastService = "";
    private String lastView = "";
    private String lastConnection = "";

    static void open() {
        if (instance == null) {
            instance = new CrudDialog(WindowManager.getDefault().getMainWindow());
        }
        instance.setVisible(true);
        instance.toFront();
        instance.requestFocus();
    }

    private CrudDialog(java.awt.Frame owner) {
        super(owner, "Generador de CRUD (Java + MySQL)", false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
            }
        });
        buildUi();
        sql.setText(SAMPLE);
        outputFolder.setText(defaultOutputFolder());
        connectionPackage.setText(basePackage.getText().trim() + ".conexion");
        refreshConnectionPreview();
        analyzeAndPreview();
    }

    private void buildUi() {
        sql.setFont(new Font("Consolas", Font.PLAIN, 12));
        sql.setTabSize(2);
        JScrollPane sqlScroll = new JScrollPane(sql);
        sqlScroll.setPreferredSize(new Dimension(900, 155));

        JPanel sqlPanel = new JPanel(new BorderLayout(6, 4));
        sqlPanel.setBorder(BorderFactory.createTitledBorder("1. Pega el CREATE TABLE de MySQL"));
        sqlPanel.add(sqlScroll, BorderLayout.CENTER);
        JPanel sqlButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sqlButtons.add(analyze);
        JButton connect = new JButton("Conectar a MySQL…");
        connect.addActionListener(e -> {
            String ddl = DbConnectDialog.open(getOwner());
            if (ddl != null) {
                sql.setText(ddl);
                analyzeAndPreview();
            }
        });
        sqlButtons.add(connect);
        JButton sample = new JButton("Cargar ejemplo");
        sample.addActionListener(e -> {
            sql.setText(SAMPLE);
            analyzeAndPreview();
        });
        sqlButtons.add(sample);
        sqlPanel.add(sqlButtons, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("2. Datos del código"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;

        gbc.gridx = 0;
        form.add(new JLabel("Paquete base:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(basePackage, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Clase entidad:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(entityName, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Carpeta de salida:"), gbc);
        gbc.gridx = 1;
        JPanel folderRow = new JPanel(new BorderLayout(4, 0));
        folderRow.add(outputFolder, BorderLayout.CENTER);
        JButton browse = new JButton("Examinar…");
        browse.addActionListener(e -> browseFolder());
        folderRow.add(browse, BorderLayout.EAST);
        form.add(folderRow, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        options.add(new JLabel("Generar:"));
        options.add(genEntity);
        options.add(genDao);
        options.add(genService);
        options.add(genConnection);
        options.add(genView);
        form.add(options, gbc);

        gbc.gridy = 4;
        JLabel editableHint = new JLabel(
                "Edita el código o cambia paquete/clase y pulsa Analizar; Generar guarda lo que ves.");
        editableHint.setForeground(MUTED);
        form.add(editableHint, gbc);

        JPanel connectionForm = new JPanel(new GridBagLayout());
        connectionForm.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        GridBagConstraints conn = new GridBagConstraints();
        conn.insets = new Insets(3, 4, 3, 4);
        conn.anchor = GridBagConstraints.WEST;
        conn.gridy = 0;
        conn.gridx = 0;
        connectionForm.add(new JLabel("URL JDBC:"), conn);
        conn.gridx = 1;
        conn.weightx = 1;
        conn.fill = GridBagConstraints.HORIZONTAL;
        connectionForm.add(dbUrl, conn);

        conn.gridy = 1;
        conn.gridx = 0;
        conn.weightx = 0;
        conn.fill = GridBagConstraints.NONE;
        connectionForm.add(new JLabel("Usuario:"), conn);
        conn.gridx = 1;
        conn.weightx = 1;
        conn.fill = GridBagConstraints.HORIZONTAL;
        connectionForm.add(dbUser, conn);

        conn.gridy = 2;
        conn.gridx = 0;
        conn.weightx = 0;
        conn.fill = GridBagConstraints.NONE;
        connectionForm.add(new JLabel("Clave:"), conn);
        conn.gridx = 1;
        conn.weightx = 1;
        conn.fill = GridBagConstraints.HORIZONTAL;
        connectionForm.add(dbPassword, conn);

        conn.gridy = 3;
        conn.gridx = 0;
        conn.weightx = 0;
        conn.fill = GridBagConstraints.NONE;
        connectionForm.add(new JLabel("Paquete conexión:"), conn);
        conn.gridx = 1;
        conn.weightx = 1;
        conn.fill = GridBagConstraints.HORIZONTAL;
        connectionForm.add(connectionPackage, conn);

        conn.gridy = 4;
        conn.gridx = 0;
        conn.weightx = 0;
        conn.fill = GridBagConstraints.NONE;
        connectionForm.add(new JLabel("Clase conexión:"), conn);
        conn.gridx = 1;
        conn.weightx = 1;
        conn.fill = GridBagConstraints.HORIZONTAL;
        connectionForm.add(connectionClass, conn);

        conn.gridy = 5;
        conn.gridx = 0;
        conn.gridwidth = 2;
        JLabel security = new JLabel(
                "La clave queda en el código generado; para producción usa variables de entorno.");
        security.setForeground(MUTED);
        connectionForm.add(security, conn);

        JPanel connectionPanel = new JPanel(new BorderLayout(0, 4));
        connectionPanel.add(connectionForm, BorderLayout.NORTH);
        connectionPanel.add(new JScrollPane(connectionPreview), BorderLayout.CENTER);

        tabs.addTab("Entity", new JScrollPane(entityPreview));
        tabs.addTab("DAO", new JScrollPane(daoPreview));
        tabs.addTab("Service", new JScrollPane(servicePreview));
        tabs.addTab("Vista", new JScrollPane(viewPreview));
        tabs.addTab("Conexión", connectionPanel);
        tabs.addTab("SQL", new JScrollPane(sqlPreview));

        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        status.setForeground(MUTED);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(generate);
        buttons.add(copy);
        buttons.add(openFolder);
        buttons.add(close);

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(sqlPanel, BorderLayout.NORTH);
        north.add(form, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        analyze.addActionListener(e -> analyzeAndPreview());
        generate.addActionListener(e -> generateFiles());
        copy.addActionListener(e -> copyActiveTab());
        openFolder.addActionListener(e -> openOutputFolder());
        close.addActionListener(e -> dispose());
        generate.setEnabled(false);

        sql.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markStale();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markStale();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markStale();
            }
        });

        SimpleListener connectionListener = new SimpleListener(this::refreshConnectionPreview);
        dbUrl.getDocument().addDocumentListener(connectionListener);
        dbUser.getDocument().addDocumentListener(connectionListener);
        dbPassword.getDocument().addDocumentListener(connectionListener);
        connectionPackage.getDocument().addDocumentListener(connectionListener);
        connectionClass.getDocument().addDocumentListener(connectionListener);

        setPreferredSize(new Dimension(1040, 760));
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void markStale() {
        if (generated != null) {
            status.setText("Hay cambios sin analizar. Pulsa Analizar.");
            status.setForeground(MUTED);
            generate.setEnabled(false);
        }
    }

    private void analyzeAndPreview() {
        if (codeEdited() && JOptionPane.showConfirmDialog(this,
                "Has editado código en las pestañas. Al analizar se reemplazará por el código generado.\n¿Continuar?",
                "Perder cambios manuales", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        refreshConnectionPreview();
        try {
            SqlTable table = SqlParser.parse(sql.getText());
            String derived = SqlParser.toClassName(table.name);
            String current = entityName.getText().trim();
            if (current.isEmpty() || current.equals(lastDerivedEntity)) {
                entityName.setText(derived);
                lastDerivedEntity = derived;
            }
            String entity = entityName.getText().trim();
            String pkg = basePackage.getText().trim();
            if (pkg.isEmpty()) {
                pkg = "com.ejemplo.sistema";
                basePackage.setText(pkg);
            }
            if (!PACKAGE.matcher(pkg).matches()) {
                throw new IllegalArgumentException("El paquete no es válido: " + pkg);
            }
            if (!CLASS.matcher(entity).matches()) {
                throw new IllegalArgumentException("El nombre de la clase no es válido: " + entity);
            }
            generated = CrudGenerator.generate(table, pkg, entity);
            entityPreview.setText(generated.entity());
            daoPreview.setText(generated.dao());
            servicePreview.setText(generated.service());
            sqlPreview.setText(generated.sql());
            lastEntity = generated.entity();
            lastDao = generated.dao();
            lastService = generated.service();
            String view = CrudGenerator.viewCode(pkg, entity, table);
            viewPreview.setText(view);
            lastView = view;
            entityPreview.setCaretPosition(0);
            daoPreview.setCaretPosition(0);
            servicePreview.setCaretPosition(0);
            viewPreview.setCaretPosition(0);
            sqlPreview.setCaretPosition(0);

            StringBuilder summary = new StringBuilder("Tabla ").append(table.name)
                    .append(" · ").append(table.columns.size()).append(" columnas");
            List<SqlTable.Column> keys = table.primaryKeys();
            if (!keys.isEmpty()) {
                summary.append(" · PK: ");
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) {
                        summary.append(", ");
                    }
                    summary.append(keys.get(i).sqlName);
                }
            }
            if (!generated.notes().isBlank()) {
                summary.append(" · ").append(generated.notes().replace('\n', ' '));
            }
            status.setText(summary.toString());
            status.setForeground(OK);
            generate.setEnabled(true);
        } catch (RuntimeException ex) {
            generated = null;
            generate.setEnabled(false);
            status.setText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            status.setForeground(ERROR);
        }
    }

    private boolean codeEdited() {
        return (!lastEntity.isEmpty() && !entityPreview.getText().equals(lastEntity))
                || (!lastDao.isEmpty() && !daoPreview.getText().equals(lastDao))
                || (!lastService.isEmpty() && !servicePreview.getText().equals(lastService))
                || (!lastView.isEmpty() && !viewPreview.getText().equals(lastView))
                || (!lastConnection.isEmpty() && !connectionPreview.getText().equals(lastConnection));
    }

    private void refreshConnectionPreview() {
        String pkg = connectionPackage.getText().trim();
        if (pkg.isEmpty()) {
            pkg = basePackage.getText().trim() + ".conexion";
        }
        String cls = connectionClass.getText().trim();
        if (cls.isEmpty()) {
            cls = "Conexion";
        }
        String code = CrudGenerator.connectionCode(pkg, cls, dbUrl.getText(),
                dbUser.getText(), new String(dbPassword.getPassword()));
        connectionPreview.setText(code);
        connectionPreview.setCaretPosition(0);
        lastConnection = code;
    }

    private void generateFiles() {
        boolean needsCode = genEntity.isSelected() || genDao.isSelected()
                || genService.isSelected() || genView.isSelected();
        if (needsCode && (generated == null || !generate.isEnabled())) {
            analyzeAndPreview();
            if (generated == null) {
                return;
            }
        }
        if (!needsCode && !genConnection.isSelected()) {
            status.setText("Marca al menos Entity, DAO, Service o Conexión");
            status.setForeground(ERROR);
            return;
        }
        String out = outputFolder.getText().trim();
        if (out.isEmpty()) {
            status.setText("Elige una carpeta de salida");
            status.setForeground(ERROR);
            return;
        }
        Path root;
        try {
            root = Path.of(out);
        } catch (RuntimeException ex) {
            status.setText("Ruta de salida no válida: " + out);
            status.setForeground(ERROR);
            return;
        }
        try {
            if (!Files.exists(root)) {
                int create = JOptionPane.showConfirmDialog(this,
                        "La carpeta no existe. ¿Crearla?\n" + root,
                        "Crear carpeta", JOptionPane.YES_NO_OPTION);
                if (create != JOptionPane.YES_OPTION) {
                    return;
                }
                Files.createDirectories(root);
            }
            if (!Files.isDirectory(root)) {
                status.setText("La salida no es una carpeta: " + root);
                status.setForeground(ERROR);
                return;
            }
        } catch (Exception ex) {
            status.setText("No se pudo preparar la carpeta: " + ex.getMessage());
            status.setForeground(ERROR);
            return;
        }

        String entity = entityName.getText().trim();
        String pkg = basePackage.getText().trim();
        String connPkg = connectionPackage.getText().trim();
        if (connPkg.isEmpty()) {
            connPkg = pkg + ".conexion";
        }
        String connClass = connectionClass.getText().trim();
        if (connClass.isEmpty()) {
            connClass = "Conexion";
        }

        List<Path> files = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        try {
            if (genEntity.isSelected()) {
                addCodeFile(files, contents, root, entityPreview.getText(), pkg, entity);
            }
            if (genDao.isSelected()) {
                addCodeFile(files, contents, root, daoPreview.getText(), pkg, entity + "DAO");
            }
            if (genService.isSelected()) {
                addCodeFile(files, contents, root, servicePreview.getText(), pkg, entity + "Service");
            }
            if (genView.isSelected()) {
                addCodeFile(files, contents, root, viewPreview.getText(), pkg + ".view", entity + "View");
            }
            if (genConnection.isSelected()) {
                if (connectionPreview.getText().isBlank()) {
                    refreshConnectionPreview();
                }
                addCodeFile(files, contents, root, connectionPreview.getText(), connPkg, connClass);
            }
        } catch (IllegalArgumentException ex) {
            status.setText(ex.getMessage());
            status.setForeground(ERROR);
            return;
        }
        if (files.isEmpty()) {
            status.setText("No hay archivos que generar");
            status.setForeground(ERROR);
            return;
        }
        List<Path> existing = new ArrayList<>();
        for (Path file : files) {
            if (Files.exists(file)) {
                existing.add(file);
            }
        }
        if (!existing.isEmpty()) {
            StringBuilder message = new StringBuilder("Ya existen estos archivos:\n");
            int count = Math.min(existing.size(), 6);
            for (int i = 0; i < count; i++) {
                message.append(existing.get(i)).append('\n');
            }
            if (existing.size() > count) {
                message.append("... (").append(existing.size() - count).append(" más)\n");
            }
            message.append("\n¿Sobrescribirlos?");
            int answer = JOptionPane.showConfirmDialog(this, message.toString(),
                    "Sobrescribir archivos", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            for (int i = 0; i < files.size(); i++) {
                Path file = files.get(i);
                Files.createDirectories(file.getParent());
                Files.writeString(file, contents.get(i), StandardCharsets.UTF_8);
            }
            status.setText("Generados " + files.size() + " archivo(s) en " + root);
            status.setForeground(OK);
        } catch (Exception ex) {
            status.setText("No se pudieron escribir los archivos: " + ex.getMessage());
            status.setForeground(ERROR);
        }
    }

    private static void addCodeFile(List<Path> files, List<String> contents, Path root,
            String code, String fallbackPackage, String fallbackClass) {
        String text = code == null ? "" : code;
        if (text.isBlank()) {
            throw new IllegalArgumentException("Una pestaña de código está vacía.");
        }
        String pkg = packageOf(text, fallbackPackage);
        String cls = classOf(text, fallbackClass);
        if (!PACKAGE.matcher(pkg).matches()) {
            throw new IllegalArgumentException("Paquete no válido en el código: " + pkg);
        }
        if (!CLASS.matcher(cls).matches()) {
            throw new IllegalArgumentException("Clase no válida en el código: " + cls);
        }
        Path file = root.resolve(pkg.replace('.', File.separatorChar)).resolve(cls + ".java");
        if (!files.contains(file)) {
            files.add(file);
            contents.add(text);
        }
    }

    private static String packageOf(String code, String fallback) {
        Matcher matcher = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(code);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String classOf(String code, String fallback) {
        Matcher matcher = Pattern.compile("(?m)public\\s+(?:final\\s+)?class\\s+(\\w+)")
                .matcher(code);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private void copyActiveTab() {
        java.awt.Component selected = tabs.getSelectedComponent();
        JTextComponent text = null;
        if (tabs.getSelectedIndex() == tabs.indexOfTab("Conexión")) {
            text = connectionPreview;
        } else if (selected instanceof JScrollPane scroll
                && scroll.getViewport().getView() instanceof JTextComponent component) {
            text = component;
        }
        if (text == null) {
            return;
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text.getText()), null);
        status.setText("Pestaña copiada al portapapeles");
        status.setForeground(OK);
    }

    private void openOutputFolder() {
        String out = outputFolder.getText().trim();
        if (out.isEmpty()) {
            return;
        }
        try {
            Desktop.getDesktop().open(new File(out));
        } catch (Exception ex) {
            status.setText("No se pudo abrir la carpeta: " + ex.getMessage());
            status.setForeground(ERROR);
        }
    }

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser(outputFolder.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Carpeta de salida (src o src/main/java)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputFolder.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static String defaultOutputFolder() {
        JTextComponent pane = EditorRegistry.lastFocusedComponent();
        if (pane != null) {
            Document doc = pane.getDocument();
            DataObject data = (DataObject) doc.getProperty(Document.StreamDescriptionProperty);
            FileObject file = data == null ? null : data.getPrimaryFile();
            for (FileObject dir = file == null ? null : file.getParent();
                    dir != null; dir = dir.getParent()) {
                if ("java".equals(dir.getName()) && dir.getParent() != null
                        && "main".equals(dir.getParent().getName())
                        && dir.getParent().getParent() != null
                        && "src".equals(dir.getParent().getParent().getName())) {
                    return dir.getPath();
                }
                if ("src".equals(dir.getName())) {
                    return dir.getPath();
                }
            }
        }
        return System.getProperty("user.home", ".");
    }

    private static JTextArea previewArea(boolean editable) {
        JTextArea area = new JTextArea();
        area.setEditable(editable);
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        area.setTabSize(4);
        Color background = UIManager.getColor("TextArea.background");
        Color foreground = UIManager.getColor("TextArea.foreground");
        if (background != null) {
            area.setBackground(background);
        }
        if (foreground != null) {
            area.setForeground(foreground);
        }
        return area;
    }

    private static final class SimpleListener implements DocumentListener {

        private final Runnable action;

        SimpleListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            action.run();
        }
    }
}

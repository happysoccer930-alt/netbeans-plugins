package org.deepsetk.crud;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;

/** Editor SQL con conexion a MySQL, ejecucion y autocompletado contextual. */
final class SqlEditorDialog extends JDialog {

    private static final String[] KEYWORDS = {
        "SELECT", "FROM", "WHERE", "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM",
        "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "ON", "GROUP BY", "ORDER BY",
        "HAVING", "LIMIT", "OFFSET", "CREATE TABLE", "ALTER TABLE", "DROP TABLE", "PRIMARY KEY",
        "FOREIGN KEY", "REFERENCES", "AUTO_INCREMENT", "NOT NULL", "DEFAULT", "UNIQUE",
        "INDEX", "AND", "OR", "IN", "LIKE", "BETWEEN", "IS NULL", "DISTINCT", "AS", "ASC", "DESC",
        "SHOW TABLES", "DESCRIBE", "TRUNCATE", "EXPLAIN"
    };

    private static final String[] FUNCTIONS = {
        "COUNT", "SUM", "AVG", "MIN", "MAX", "IFNULL", "COALESCE", "CONCAT",
        "NOW", "CURDATE", "CURTIME", "LENGTH", "UPPER", "LOWER", "ROUND",
        "DATE_FORMAT", "YEAR", "MONTH", "DAY"
    };

    private static final Set<String> TABLE_CLAUSES = Set.of(
            "FROM", "JOIN", "INTO", "UPDATE", "TABLE");
    private static final Set<String> COLUMN_CLAUSES = Set.of(
            "SELECT", "WHERE", "AND", "OR", "ON", "SET", "BY", "HAVING", "VALUES", "ORDER", "GROUP");

    private final JTextField host = new JTextField("localhost", 12);
    private final JTextField port = new JTextField("3306", 5);
    private final JTextField user = new JTextField("root", 12);
    private final JPasswordField password = new JPasswordField(12);
    private final JTextField database = new JTextField(16);
    private final JButton connect = new JButton("Conectar");
    private final JButton execute = new JButton("Ejecutar (Ctrl+Enter)");
    private final JTextArea sql = new JTextArea(10, 80);
    private final JTable results = new JTable();
    private final JLabel status = new JLabel(" ");

    private final Object schemaLock = new Object();
    private final List<String> tables = new ArrayList<>();
    private final Set<String> allColumns = new LinkedHashSet<>();
    private final Map<String, List<String>> tableColumns = new LinkedHashMap<>();

    private final DefaultListModel<String> completionModel = new DefaultListModel<>();
    private final JList<String> completionList = new JList<>(completionModel);
    private final JPopupMenu completionPopup = new JPopupMenu();
    private final Timer completionTimer = new Timer(140, e -> showCompletion(false));
    private int completionStart = -1;
    private int completionEnd = -1;
    private boolean updatingCompletion;

    private Connection connection;

    static void open(Window owner) {
        new SqlEditorDialog(owner).setVisible(true);
    }

    private SqlEditorDialog(Window owner) {
        super(owner, "Editor SQL (MySQL)", ModalityType.MODELESS);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                closeConnection();
            }
        });
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;
        gbc.gridx = 0;
        form.add(new JLabel("Host:"), gbc);
        gbc.gridx = 1;
        form.add(host, gbc);
        gbc.gridx = 2;
        form.add(new JLabel("Puerto:"), gbc);
        gbc.gridx = 3;
        form.add(port, gbc);
        gbc.gridx = 4;
        form.add(new JLabel("Usuario:"), gbc);
        gbc.gridx = 5;
        form.add(user, gbc);
        gbc.gridx = 6;
        form.add(new JLabel("Clave:"), gbc);
        gbc.gridx = 7;
        form.add(password, gbc);
        gbc.gridx = 8;
        form.add(new JLabel("Base:"), gbc);
        gbc.gridx = 9;
        form.add(database, gbc);
        gbc.gridx = 10;
        form.add(connect, gbc);

        sql.setFont(new Font("Consolas", Font.PLAIN, 13));
        sql.setTabSize(4);
        installCompletion();

        results.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane resultsScroll = new JScrollPane(results);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(sql), resultsScroll);
        split.setResizeWeight(0.45);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(form, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(execute);
        JButton clear = new JButton("Limpiar");
        clear.addActionListener(e -> sql.setText(""));
        buttons.add(clear);
        JButton close = new JButton("Cerrar");
        close.addActionListener(e -> dispose());
        buttons.add(close);

        JPanel south = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        south.add(status, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(1000, 620));

        connect.addActionListener(e -> connect());
        execute.addActionListener(e -> runSql());

        sql.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "run-sql");
        sql.getActionMap().put("run-sql", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runSql();
            }
        });
    }

    private void installCompletion() {
        completionList.setFocusable(false);
        completionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        completionList.setVisibleRowCount(8);
        completionPopup.setFocusable(false);
        completionPopup.setPopupSize(new Dimension(320, 200));
        completionPopup.add(new JScrollPane(completionList));
        completionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    acceptCompletion();
                }
            }
        });
        completionTimer.setRepeats(false);

        sql.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }

            private void schedule() {
                if (!updatingCompletion) {
                    completionTimer.restart();
                }
            }
        });

        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_DOWN_MASK), "sql-complete",
                e -> showCompletion(true));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "sql-up", e -> {
            if (completionPopup.isVisible()) {
                move(-1);
            } else {
                defaultAction(DefaultEditorKit.upAction, e);
            }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "sql-down", e -> {
            if (completionPopup.isVisible()) {
                move(1);
            } else {
                defaultAction(DefaultEditorKit.downAction, e);
            }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sql-enter", e -> {
            if (completionPopup.isVisible()) {
                acceptCompletion();
            } else {
                defaultAction(DefaultEditorKit.insertBreakAction, e);
            }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "sql-tab", e -> {
            if (completionPopup.isVisible()) {
                acceptCompletion();
            } else {
                sql.replaceSelection("    ");
            }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "sql-escape", e -> {
            if (completionPopup.isVisible()) {
                hideCompletion();
            }
        });
    }

    private void bind(KeyStroke key, String name, java.util.function.Consumer<ActionEvent> action) {
        sql.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        sql.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.accept(event);
            }
        });
    }

    private void defaultAction(String name, ActionEvent event) {
        Action action = sql.getActionMap().get(name);
        if (action != null) {
            action.actionPerformed(new ActionEvent(sql, ActionEvent.ACTION_PERFORMED, name));
        }
    }

    private void scheduleCompletion() {
        if (!updatingCompletion) {
            completionTimer.restart();
        }
    }

    private void showCompletion(boolean forced) {
        int caret = sql.getCaretPosition();
        String text = sql.getText();
        int start = caret;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        String full = text.substring(start, caret);
        int dot = full.lastIndexOf('.');
        String prefix = full;
        int replacementStart = start;
        List<String> candidates;
        if (dot >= 0) {
            String table = full.substring(0, dot);
            prefix = full.substring(dot + 1);
            replacementStart = start + dot + 1;
            candidates = columnsOf(table, prefix);
        } else {
            if (!forced && (prefix.length() < 2)) {
                hideCompletion();
                return;
            }
            if (prefix.isEmpty() && !forced) {
                hideCompletion();
                return;
            }
            candidates = candidates(prefix, text.substring(0, start));
        }
        if (candidates.isEmpty()) {
            hideCompletion();
            return;
        }
        completionStart = replacementStart;
        completionEnd = caret;
        completionModel.clear();
        for (String candidate : candidates) {
            completionModel.addElement(candidate);
        }
        completionList.setSelectedIndex(0);
        try {
            Rectangle bounds = sql.modelToView2D(caret).getBounds();
            completionPopup.show(sql, bounds.x, bounds.y + bounds.height);
            sql.requestFocusInWindow();
        } catch (BadLocationException ignored) {
            hideCompletion();
        }
    }

    private void move(int delta) {
        int size = completionModel.getSize();
        if (size == 0) {
            return;
        }
        int index = (completionList.getSelectedIndex() + delta + size) % size;
        completionList.setSelectedIndex(index);
        completionList.ensureIndexIsVisible(index);
    }

    private void acceptCompletion() {
        String value = completionList.getSelectedValue();
        if (value == null || completionStart < 0) {
            hideCompletion();
            return;
        }
        try {
            updatingCompletion = true;
            sql.getDocument().remove(completionStart, completionEnd - completionStart);
            sql.getDocument().insertString(completionStart, value, null);
            sql.setCaretPosition(completionStart + value.length());
        } catch (BadLocationException ignored) {
        } finally {
            updatingCompletion = false;
        }
        hideCompletion();
        sql.requestFocusInWindow();
    }

    private void hideCompletion() {
        completionPopup.setVisible(false);
        completionStart = -1;
        completionEnd = -1;
    }

    private List<String> candidates(String prefix, String before) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        String clause = clause(before);
        boolean wantTables = clause == null || TABLE_CLAUSES.contains(clause);
        boolean wantColumns = clause == null || COLUMN_CLAUSES.contains(clause);

        Set<String> found = new LinkedHashSet<>();
        if (wantTables) {
            synchronized (schemaLock) {
                for (String table : tables) {
                    if (starts(table, lower)) {
                        found.add(table);
                    }
                }
            }
        }
        if (wantColumns) {
            synchronized (schemaLock) {
                for (String column : allColumns) {
                    if (starts(column, lower)) {
                        found.add(column);
                    }
                }
            }
            for (String function : FUNCTIONS) {
                if (starts(function, lower)) {
                    found.add(function);
                }
            }
        }
        for (String keyword : KEYWORDS) {
            if (starts(keyword, lower)) {
                found.add(keyword);
            }
        }
        List<String> list = new ArrayList<>(found);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list.size() > 30 ? list.subList(0, 30) : list;
    }

    private List<String> columnsOf(String table, String prefix) {
        List<String> columns = null;
        synchronized (schemaLock) {
            columns = tableColumns.get(table);
            if (columns == null) {
                for (Map.Entry<String, List<String>> entry : tableColumns.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(table)) {
                        columns = entry.getValue();
                        break;
                    }
                }
            }
        }
        if (columns == null) {
            return List.of();
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String column : columns) {
            if (starts(column, lower)) {
                found.add(column);
            }
        }
        found.sort(String.CASE_INSENSITIVE_ORDER);
        return found;
    }

    private static boolean starts(String value, String lowerPrefix) {
        return value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix);
    }

    private static String clause(String before) {
        String tail = before.length() > 220 ? before.substring(before.length() - 220) : before;
        Matcher matcher = Pattern.compile("[A-Za-z_]+").matcher(tail.toUpperCase(Locale.ROOT));
        String clause = null;
        while (matcher.find()) {
            String word = matcher.group();
            if (TABLE_CLAUSES.contains(word) || COLUMN_CLAUSES.contains(word)) {
                clause = word;
            }
        }
        return clause;
    }

    private DbConfig config() {
        return new DbConfig(host.getText(), port.getText(), user.getText(),
                new String(password.getPassword()), database.getText());
    }

    private void connect() {
        if (database.getText().isBlank()) {
            status.setText("Escribe el nombre de la base de datos.");
            return;
        }
        connect.setEnabled(false);
        status.setText("Conectando...");
        DbConfig cfg = config();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                closeConnection();
                connection = cfg.connect();
                List<String> tableList = new ArrayList<>();
                Set<String> columnFlat = new LinkedHashSet<>();
                Map<String, List<String>> byTable = new LinkedHashMap<>();
                int names = 0;
                try (Statement statement = connection.createStatement();
                        ResultSet rs = statement.executeQuery("SHOW TABLES")) {
                    while (rs.next()) {
                        String table = rs.getString(1);
                        tableList.add(table);
                        names++;
                        List<String> columns = new ArrayList<>();
                        try (Statement columnStatement = connection.createStatement();
                                ResultSet crs = columnStatement.executeQuery(
                                        "SHOW COLUMNS FROM `" + table.replace("`", "``") + "`")) {
                            while (crs.next()) {
                                String column = crs.getString(1);
                                columns.add(column);
                                columnFlat.add(column);
                                names++;
                            }
                        }
                        byTable.put(table, columns);
                    }
                }
                synchronized (schemaLock) {
                    tables.clear();
                    tables.addAll(tableList);
                    allColumns.clear();
                    allColumns.addAll(columnFlat);
                    tableColumns.clear();
                    tableColumns.putAll(byTable);
                }
                return names + " nombres de esquema cargados.";
            }

            @Override
            protected void done() {
                connect.setEnabled(true);
                try {
                    status.setText(get());
                } catch (Exception ex) {
                    connection = null;
                    status.setText("Error: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private void runSql() {
        hideCompletion();
        if (connection == null) {
            status.setText("Conectate primero.");
            return;
        }
        String text = sql.getSelectedText();
        if (text == null || text.isBlank()) {
            text = sql.getText();
        }
        if (text.isBlank()) {
            status.setText("Escribe una consulta.");
            return;
        }
        String query = text;
        execute.setEnabled(false);
        status.setText("Ejecutando...");
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                List<String> statements = DbBackup.splitStatements(query);
                String lastMessage = "Sin resultados.";
                for (int i = 0; i < statements.size(); i++) {
                    String statementText = statements.get(i);
                    try (Statement statement = connection.createStatement()) {
                        boolean hasResult = statement.execute(statementText);
                        if (hasResult) {
                            try (ResultSet rs = statement.getResultSet()) {
                                return new Object[]{toModel(rs), null};
                            }
                        }
                        lastMessage = "Sentencia " + (i + 1) + ": "
                                + statement.getUpdateCount() + " fila(s) afectada(s).";
                    }
                }
                return new Object[]{null, lastMessage};
            }

            @Override
            protected void done() {
                execute.setEnabled(true);
                try {
                    Object[] result = get();
                    if (result[0] instanceof DefaultTableModel model) {
                        results.setModel(model);
                        status.setText(model.getRowCount() + " fila(s).");
                    } else {
                        results.setModel(new DefaultTableModel());
                        status.setText(String.valueOf(result[1]));
                    }
                } catch (Exception ex) {
                    status.setText("Error: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private static DefaultTableModel toModel(ResultSet rs) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        String[] names = new String[columns];
        for (int i = 0; i < columns; i++) {
            names[i] = meta.getColumnLabel(i + 1);
        }
        DefaultTableModel model = new DefaultTableModel(names, 0);
        while (rs.next()) {
            Object[] row = new Object[columns];
            for (int i = 0; i < columns; i++) {
                row[i] = rs.getObject(i + 1);
            }
            model.addRow(row);
        }
        return model;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '`';
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
            connection = null;
        }
    }

    private static String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}

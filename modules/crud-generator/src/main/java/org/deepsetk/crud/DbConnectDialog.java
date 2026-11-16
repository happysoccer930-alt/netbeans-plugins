package org.deepsetk.crud;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Conecta a MySQL, lista las tablas reales y devuelve el CREATE TABLE de la
 * tabla elegida para reutilizarlo en el generador de CRUD.
 */
final class DbConnectDialog extends JDialog {

    private final JTextField host = new JTextField("localhost", 14);
    private final JTextField port = new JTextField("3306", 6);
    private final JTextField user = new JTextField("root", 14);
    private final JPasswordField password = new JPasswordField(14);
    private final JTextField database = new JTextField(18);
    private final DefaultListModel<String> tableModel = new DefaultListModel<>();
    private final JList<String> tables = new JList<>(tableModel);
    private final JLabel status = new JLabel(" ");
    private final JButton load = new JButton("Probar y ver tablas");

    private String createSql;

    static String open(Window owner) {
        DbConnectDialog dialog = new DbConnectDialog(owner);
        dialog.setVisible(true);
        return dialog.createSql;
    }

    private DbConnectDialog(Window owner) {
        super(owner, "Conectar a MySQL", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Conexion"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;

        gbc.gridx = 0;
        form.add(new JLabel("Host:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(host, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Puerto:"), gbc);
        gbc.gridx = 3;
        form.add(port, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        form.add(new JLabel("Usuario:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(user, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Clave:"), gbc);
        gbc.gridx = 3;
        form.add(password, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        form.add(new JLabel("Base de datos:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(database, gbc);
        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        form.add(load, gbc);
        gbc.gridwidth = 1;

        JPanel tablePanel = new JPanel(new BorderLayout(4, 4));
        tablePanel.setBorder(BorderFactory.createTitledBorder("Tablas"));
        tablePanel.add(new JScrollPane(tables), BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        JButton ok = new JButton("Usar tabla");
        JButton cancel = new JButton("Cancelar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(ok);
        buttons.add(cancel);

        load.addActionListener(e -> loadTables());
        ok.addActionListener(e -> chooseTable());
        cancel.addActionListener(e -> dispose());
        tables.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    chooseTable();
                }
            }
        });

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.add(form, BorderLayout.NORTH);
        center.add(tablePanel, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(600, 430));
    }

    private DbConfig config() {
        return new DbConfig(host.getText(), port.getText(), user.getText(),
                new String(password.getPassword()), database.getText());
    }

    private void loadTables() {
        if (database.getText().isBlank()) {
            status.setText("Escribe el nombre de la base de datos.");
            return;
        }
        load.setEnabled(false);
        status.setText("Conectando...");
        DbConfig cfg = config();
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                List<String> names = new ArrayList<>();
                try (Connection connection = cfg.connect();
                        Statement statement = connection.createStatement();
                        ResultSet rs = statement.executeQuery("SHOW TABLES")) {
                    while (rs.next()) {
                        names.add(rs.getString(1));
                    }
                }
                return names;
            }

            @Override
            protected void done() {
                load.setEnabled(true);
                try {
                    List<String> names = get();
                    tableModel.clear();
                    for (String name : names) {
                        tableModel.addElement(name);
                    }
                    status.setText(names.size() + " tabla(s) en " + cfg.database);
                } catch (Exception ex) {
                    tableModel.clear();
                    status.setText("Error: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private void chooseTable() {
        String table = tables.getSelectedValue();
        if (table == null) {
            status.setText("Elige una tabla de la lista.");
            return;
        }
        status.setText("Leyendo CREATE TABLE de " + table + "...");
        DbConfig cfg = config();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try (Connection connection = cfg.connect();
                        Statement statement = connection.createStatement();
                        ResultSet rs = statement.executeQuery(
                                "SHOW CREATE TABLE `" + table.replace("`", "``") + "`")) {
                    return rs.next() ? rs.getString(2) : null;
                }
            }

            @Override
            protected void done() {
                try {
                    createSql = get();
                    if (createSql == null || createSql.isBlank()) {
                        status.setText("No se pudo leer la tabla " + table);
                        return;
                    }
                    dispose();
                } catch (Exception ex) {
                    status.setText("Error: " + rootMessage(ex));
                }
            }
        }.execute();
    }

    private static String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}

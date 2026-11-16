package org.deepsetk.crud;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Exporta e importa una base MySQL a/desde un archivo .sql. */
final class DbBackupDialog extends JDialog {

    private static final Color MUTED = new Color(0x8A929C);

    private final JTextField host = new JTextField("localhost", 14);
    private final JTextField port = new JTextField("3306", 6);
    private final JTextField user = new JTextField("root", 14);
    private final JPasswordField password = new JPasswordField(14);
    private final JTextField database = new JTextField(18);
    private final JTextField file = new JTextField(38);
    private final JCheckBox useTools = new JCheckBox(
            "Usar mysqldump/mysql del sistema (si estan en el PATH)");
    private final JLabel status = new JLabel(" ");
    private final JButton export = new JButton("Exportar .sql");
    private final JButton importButton = new JButton("Importar .sql");

    static void open(Window owner) {
        new DbBackupDialog(owner).setVisible(true);
    }

    private DbBackupDialog(Window owner) {
        super(owner, "Importar / Exportar base MySQL", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        boolean tools = DbBackup.hasDumpTool() && DbBackup.hasMysqlTool();
        useTools.setSelected(tools);
        useTools.setEnabled(tools);
        if (!tools) {
            useTools.setText("mysqldump/mysql no encontrados: se usara JDBC");
        }
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
        form.add(useTools, gbc);
        gbc.gridwidth = 1;

        JPanel filePanel = new JPanel(new BorderLayout(4, 0));
        filePanel.setBorder(BorderFactory.createTitledBorder("Archivo .sql"));
        filePanel.add(file, BorderLayout.CENTER);
        JButton browse = new JButton("Examinar…");
        browse.addActionListener(e -> browse());
        filePanel.add(browse, BorderLayout.EAST);

        status.setForeground(MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        JButton close = new JButton("Cerrar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(export);
        buttons.add(importButton);
        buttons.add(close);
        close.addActionListener(e -> dispose());
        export.addActionListener(e -> run(true));
        importButton.addActionListener(e -> run(false));

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(form, BorderLayout.NORTH);
        center.add(filePanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);

        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(640, 300));
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(file.getText());
        chooser.setFileFilter(new FileNameExtensionFilter("Volcado SQL (*.sql)", "sql"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            String path = selected.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".sql")) {
                path += ".sql";
            }
            file.setText(path);
        }
    }

    private void run(boolean exporting) {
        String path = file.getText().trim();
        if (path.isEmpty()) {
            status.setText("Elige un archivo .sql.");
            return;
        }
        if (database.getText().isBlank()) {
            status.setText("Escribe el nombre de la base de datos.");
            return;
        }
        Path target = Path.of(path);
        DbConfig cfg = new DbConfig(host.getText(), port.getText(), user.getText(),
                new String(password.getPassword()), database.getText());
        boolean tools = useTools.isSelected();
        export.setEnabled(false);
        importButton.setEnabled(false);
        status.setText(exporting ? "Exportando..." : "Importando...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                if (exporting) {
                    if (tools) {
                        DbBackup.exportDump(cfg, target);
                    } else {
                        DbBackup.exportJdbc(cfg, target);
                    }
                    return "Exportado: " + target;
                }
                if (tools) {
                    DbBackup.importDump(cfg, target);
                } else {
                    int count = DbBackup.importJdbc(cfg, target);
                    return "Importadas " + count + " sentencia(s) desde " + target;
                }
                return "Importado: " + target;
            }

            @Override
            protected void done() {
                export.setEnabled(true);
                importButton.setEnabled(true);
                try {
                    status.setText(get());
                } catch (Exception ex) {
                    status.setText("Error: " + rootMessage(ex));
                    JOptionPane.showMessageDialog(DbBackupDialog.this,
                            rootMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
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

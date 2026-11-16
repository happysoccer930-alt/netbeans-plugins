package org.deepsetk.pluginmanager;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import org.netbeans.api.autoupdate.OperationContainer;
import org.netbeans.api.autoupdate.OperationException;
import org.netbeans.api.autoupdate.OperationSupport;
import org.netbeans.api.autoupdate.UpdateElement;
import org.netbeans.api.autoupdate.UpdateManager;
import org.netbeans.api.autoupdate.UpdateUnit;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.LifecycleManager;
import org.openide.modules.Dependency;
import org.openide.modules.ModuleInfo;
import org.openide.util.Lookup;
import org.openide.windows.WindowManager;

/**
 * Panel para activar y desactivar modulos instalados. Usa la API publica de
 * autoupdate, la misma que el gestor de plugins de NetBeans.
 */
final class PluginManagerDialog extends JDialog {

    private static PluginManagerDialog instance;

    private final PluginTableModel model = new PluginTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<PluginTableModel> sorter = new TableRowSorter<>(model);
    private final DefaultListModel<BlockItem> blockModel = new DefaultListModel<>();
    private final JList<BlockItem> blockList = new JList<>(blockModel);
    private final JTextField search = new JTextField(24);
    private final JComboBox<String> category = new JComboBox<>();
    private final JComboBox<String> state = new JComboBox<>(new String[]{"Todos", "Activados", "Desactivados"});
    private final JCheckBox protect = new JCheckBox("Proteger dependencias", true);
    private final JButton apply = new JButton("Aplicar cambios");
    private final JButton discard = new JButton("Descartar");
    private final JButton reload = new JButton("Recargar");
    private final JButton close = new JButton("Cerrar");
    private final JLabel status = new JLabel(" ");
    private final JTextArea detail = new JTextArea(4, 60);

    private boolean loading;
    private boolean applying;
    private String selectedBlock = "Todos";

    static void open() {
        if (instance == null) {
            Frame owner = WindowManager.getDefault().getMainWindow();
            instance = new PluginManagerDialog(owner);
        }
        instance.setVisible(true);
        instance.toFront();
        instance.requestFocus();
    }

    private PluginManagerDialog(Frame owner) {
        super(owner, "Gestor de plugins", false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
            }
        });
        buildUi();
        reloadModules();
    }

    private void buildUi() {
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filters.add(new JLabel("Buscar:"));
        filters.add(search);
        filters.add(new JLabel("Categoría:"));
        category.setPreferredSize(new Dimension(170, category.getPreferredSize().height));
        filters.add(category);
        filters.add(new JLabel("Estado:"));
        filters.add(state);
        filters.add(protect);
        protect.setToolTipText("No permite desactivar un módulo del que dependa otro módulo activo");

        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(56);
        table.getColumnModel().getColumn(0).setMinWidth(56);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(260);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        category.addActionListener(e -> applyFilter());
        state.addActionListener(e -> applyFilter());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail();
            }
        });

        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setBackground(new Color(0x1B1E22));
        detail.setForeground(new Color(0xD6DAE0));
        detail.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Detalle"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), detailScroll);
        split.setResizeWeight(1.0);
        split.setBorder(BorderFactory.createEmptyBorder());

        blockList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blockList.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        blockList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            BlockItem item = blockList.getSelectedValue();
            selectedBlock = item == null ? "Todos" : item.name();
            table.clearSelection();
            detail.setText("");
            applyFilter();
        });
        JPanel blocks = new JPanel(new BorderLayout());
        blocks.setBorder(BorderFactory.createTitledBorder("Bloques"));
        blocks.add(new JScrollPane(blockList), BorderLayout.CENTER);
        blocks.setPreferredSize(new Dimension(190, 520));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, blocks, split);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());

        apply.setEnabled(false);
        discard.setEnabled(false);
        apply.addActionListener(e -> applyChanges());
        discard.addActionListener(e -> {
            model.discardChanges();
            updateStatus();
        });
        reload.addActionListener(e -> {
            if (model.pendingChanges() > 0 && JOptionPane.showConfirmDialog(this,
                    "Hay cambios sin aplicar. ¿Recargar y perderlos?",
                    "Recargar", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                    != JOptionPane.YES_OPTION) {
                return;
            }
            reloadModules();
        });
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(apply);
        buttons.add(discard);
        buttons.add(reload);
        buttons.add(close);

        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        JLabel hint = new JLabel("Desactivar módulos puede quitar funciones. Los cambios se aplican al reiniciar NetBeans.");
        hint.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        hint.setForeground(new Color(0x9AA3AD));

        JPanel south = new JPanel(new BorderLayout());
        JPanel southTop = new JPanel(new BorderLayout());
        southTop.add(status, BorderLayout.WEST);
        southTop.add(hint, BorderLayout.EAST);
        south.add(southTop, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        add(filters, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(1020, 660));
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void reloadModules() {
        if (loading || applying) {
            return;
        }
        loading = true;
        apply.setEnabled(false);
        discard.setEnabled(false);
        status.setText("Cargando módulos…");
        new Thread(() -> {
            List<Row> rows = new ArrayList<>();
            String error = null;
            try {
                List<UpdateUnit> units = UpdateManager.getDefault()
                        .getUpdateUnits(UpdateManager.TYPE.MODULE);
                for (UpdateUnit unit : units) {
                    UpdateElement installed = unit.getInstalled();
                    if (installed == null) {
                        continue;
                    }
                    rows.add(new Row(unit, installed, installed.isEnabled()));
                }
                rows.sort(Comparator.comparing(row -> row.display.toLowerCase(Locale.ROOT)));
            } catch (Exception ex) {
                error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            String failure = error;
            SwingUtilities.invokeLater(() -> {
                loading = false;
                if (failure != null) {
                    status.setText("No se pudo cargar la lista: " + failure);
                    return;
                }
                model.setRows(rows);
                rebuildBlocks();
                rebuildCategories();
                updateStatus();
                detail.setText("");
            });
        }, "deepsetk-plugin-manager-load").start();
    }

    private void rebuildBlocks() {
        String previous = selectedBlock;
        Map<String, Integer> counts = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        counts.put("Todos", model.getRowCount());
        for (int i = 0; i < model.getRowCount(); i++) {
            counts.merge(model.row(i).block, 1, Integer::sum);
        }
        blockModel.clear();
        BlockItem restore = null;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            BlockItem item = new BlockItem(entry.getKey(), entry.getValue());
            blockModel.addElement(item);
            if (item.name().equals(previous)) {
                restore = item;
            }
        }
        if (restore != null) {
            blockList.setSelectedValue(restore, true);
        } else if (!blockModel.isEmpty()) {
            blockList.setSelectedIndex(0);
        }
    }

    private void rebuildCategories() {        String selected = (String) category.getSelectedItem();
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < model.getRowCount(); i++) {
            categories.add(model.row(i).category);
        }
        category.removeAllItems();
        category.addItem("Todas");
        for (String value : categories) {
            category.addItem(value);
        }
        if (selected != null && categories.contains(selected)) {
            category.setSelectedItem(selected);
        } else {
            category.setSelectedItem("Todas");
        }
    }

    private void applyFilter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        String wantedCategory = (String) category.getSelectedItem();
        String wantedState = (String) state.getSelectedItem();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends PluginTableModel, ? extends Integer> entry) {
                Row row = model.row(entry.getIdentifier());
                if (!"Todos".equals(selectedBlock) && !selectedBlock.equals(row.block)) {
                    return false;
                }
                if (!query.isEmpty()) {
                    String haystack = (row.display + " " + row.code + " " + row.category + " "
                            + row.description).toLowerCase(Locale.ROOT);
                    if (!haystack.contains(query)) {
                        return false;
                    }
                }
                if (wantedCategory != null && !"Todas".equals(wantedCategory)
                        && !wantedCategory.equals(row.category)) {
                    return false;
                }
                if ("Activados".equals(wantedState) && !row.original) {
                    return false;
                }
                if ("Desactivados".equals(wantedState) && row.original) {
                    return false;
                }
                return true;
            }
        });
        updateStatus();
    }

    private void showDetail() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            detail.setText("");
            return;
        }
        Row row = model.row(table.convertRowIndexToModel(viewRow));
        detail.setText(row.display + "\n" + row.code + "  ·  " + row.version
                + "  ·  " + row.category + "\n\n" + row.description);
        detail.setCaretPosition(0);
    }

    private void updateStatus() {
        int total = "Todos".equals(selectedBlock)
                ? model.getRowCount() : model.blockCount(selectedBlock);
        int changes = model.pendingChanges();
        String prefix = "Todos".equals(selectedBlock) ? "" : selectedBlock + " · ";
        String text = prefix + total + " módulos · " + changes + " cambio(s) pendiente(s)";
        if (loading) {
            text = "Cargando módulos…";
        }
        status.setText(text);
        apply.setEnabled(!loading && !applying && changes > 0);
        discard.setEnabled(!loading && !applying && changes > 0);
    }

    private void applyChanges() {
        if (loading || applying) {
            return;
        }
        List<Row> changed = model.changedRows();
        if (changed.isEmpty()) {
            status.setText("No hay cambios que aplicar");
            return;
        }
        long disableCount = changed.stream().filter(row -> !row.pending).count();
        long enableCount = changed.size() - disableCount;

        if (protect.isSelected() && disableCount > 0) {
            Set<String> broken = findBrokenDependents(changed);
            if (!broken.isEmpty()) {
                showLongMessage("Estos módulos activos dependen de algo que quieres desactivar:\n\n"
                        + String.join("\n", broken)
                        + "\n\nDesactiva la protección de dependencias si de verdad quieres continuar.",
                        "Dependencias en riesgo", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        int answer = JOptionPane.showConfirmDialog(this,
                "Se activarán " + enableCount + " y se desactivarán " + disableCount + " módulos.\n"
                + "NetBeans debe reiniciarse para aplicar los cambios.\n\n¿Continuar?",
                "Aplicar cambios", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        boolean protectDependencies = protect.isSelected();
        applying = true;
        setBusy(true);
        status.setText("Aplicando cambios…");

        new Thread(() -> {
            List<String> rejected = new ArrayList<>();
            Set<String> broken = new TreeSet<>();
            OperationSupport support = null;
            OperationSupport.Restarter restarter = null;
            String error = null;
            ProgressHandle progress = null;
            try {
                OperationContainer<OperationSupport> disable = OperationContainer.createForDisable();
                OperationContainer<OperationSupport> enable = OperationContainer.createForEnable();
                for (Row row : changed) {
                    OperationContainer<OperationSupport> container = row.pending ? enable : disable;
                    if (!container.canBeAdded(row.unit, row.element)) {
                        rejected.add(row.display);
                        continue;
                    }
                    OperationContainer.OperationInfo<OperationSupport> info
                            = container.add(row.unit, row.element);
                    if (info != null) {
                        broken.addAll(info.getBrokenDependencies());
                    }
                }
                if (!rejected.isEmpty()) {
                    error = "No se pueden cambiar estos módulos:\n" + String.join("\n", rejected);
                } else if (protectDependencies && !broken.isEmpty()) {
                    error = "Estos cambios romperían dependencias:\n" + String.join("\n", broken);
                } else {
                    progress = ProgressHandle.createHandle("Aplicando cambios en plugins");
                    progress.start();
                    if (!disable.listAll().isEmpty()) {
                        support = disable.getSupport();
                        restarter = support.doOperation(progress);
                    }
                    if (!enable.listAll().isEmpty()) {
                        support = enable.getSupport();
                        restarter = support.doOperation(progress);
                    }
                }
            } catch (OperationException ex) {
                error = ex.getLocalizedMessage();
            } catch (Exception ex) {
                error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            } finally {
                if (progress != null) {
                    try {
                        progress.finish();
                    } catch (RuntimeException ignored) {
                    }
                }
            }

            String failure = error;
            OperationSupport finalSupport = support;
            OperationSupport.Restarter finalRestarter = restarter;
            SwingUtilities.invokeLater(() -> {
                applying = false;
                setBusy(false);
                if (failure != null) {
                    status.setText("Error al aplicar los cambios");
                    showLongMessage(failure, "No se pudieron aplicar los cambios",
                            JOptionPane.ERROR_MESSAGE);
                    updateStatus();
                    return;
                }
                if (finalSupport == null || finalRestarter == null) {
                    status.setText("Cambios aplicados");
                    reloadModules();
                    return;
                }
                int restart = JOptionPane.showConfirmDialog(this,
                        "Los cambios están listos.\n¿Reiniciar NetBeans ahora?",
                        "Reiniciar NetBeans", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (restart == JOptionPane.YES_OPTION) {
                    restartNow(finalSupport, finalRestarter);
                } else {
                    try {
                        finalSupport.doRestartLater(finalRestarter);
                        LifecycleManager.getDefault().markForRestart();
                        status.setText("Reinicia NetBeans para aplicar los cambios");
                    } catch (Exception ex) {
                        status.setText("Reinicia NetBeans para aplicar los cambios");
                    }
                }
            });
        }, "deepsetk-plugin-manager-apply").start();
    }

    private void restartNow(OperationSupport support, OperationSupport.Restarter restarter) {
        status.setText("Reiniciando NetBeans…");
        new Thread(() -> {
            ProgressHandle progress = ProgressHandle.createHandle("Reiniciando NetBeans");
            progress.start();
            try {
                support.doRestart(restarter, progress);
            } catch (Exception ex) {
                try {
                    progress.finish();
                } catch (RuntimeException ignored) {
                }
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    status.setText("No se pudo reiniciar");
                    showLongMessage(message, "No se pudo reiniciar NetBeans",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "deepsetk-plugin-manager-restart").start();
    }

    private void setBusy(boolean busy) {
        search.setEnabled(!busy);
        category.setEnabled(!busy);
        state.setEnabled(!busy);
        protect.setEnabled(!busy);
        table.setEnabled(!busy);
        reload.setEnabled(!busy);
        close.setEnabled(!busy);
        apply.setEnabled(!busy && model.pendingChanges() > 0);
        discard.setEnabled(!busy && model.pendingChanges() > 0);
    }

    private Set<String> findBrokenDependents(List<Row> changed) {
        Set<String> disabled = new HashSet<>();
        for (Row row : changed) {
            if (!row.pending) {
                disabled.add(base(row.code));
            }
        }
        if (disabled.isEmpty()) {
            return Set.of();
        }
        Set<String> providedTokens = new HashSet<>();
        List<ModuleInfo> modules = new ArrayList<>();
        for (ModuleInfo module : Lookup.getDefault().lookupAll(ModuleInfo.class)) {
            modules.add(module);
            if (disabled.contains(module.getCodeNameBase()) && module.getProvides() != null) {
                providedTokens.addAll(Arrays.asList(module.getProvides()));
            }
        }
        Set<String> broken = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ModuleInfo module : modules) {
            if (!module.isEnabled() || disabled.contains(module.getCodeNameBase())) {
                continue;
            }
            for (Dependency dependency : module.getDependencies()) {
                if (dependency.getType() == Dependency.TYPE_MODULE
                        && disabled.contains(base(dependency.getName()))) {
                    broken.add(displayName(module));
                } else if (dependency.getType() == Dependency.TYPE_NEEDS
                        && providedTokens.contains(dependency.getName())) {
                    broken.add(displayName(module));
                }
            }
        }
        return broken;
    }

    private static String displayName(ModuleInfo module) {
        String name = module.getDisplayName();
        return name == null || name.isBlank() ? module.getCodeNameBase() : name;
    }

    private static String base(String codeName) {
        int slash = codeName.indexOf('/');
        return slash < 0 ? codeName : codeName.substring(0, slash);
    }

    private static String blockOf(String codeName) {
        String base = base(codeName).toLowerCase(Locale.ROOT);
        if (base.startsWith("org.deepsetk.softdark")) {
            return "Tema Soft Dark";
        }
        if (base.startsWith("org.deepsetk.aierrors")) {
            return "Gemini";
        }
        if (base.startsWith("org.deepsetk.music")) {
            return "Música";
        }
        if (base.startsWith("org.deepsetk.pluginmanager")) {
            return "Gestor de plugins";
        }
        if (base.startsWith("org.deepsetk.crud")) {
            return "Generador CRUD";
        }
        if (base.startsWith("org.deepsetk.cyberpunk")) {
            return "Cyberpunk";
        }
        if (base.startsWith("org.deepsetk.")) {
            return "DeepSetk";
        }
        if (base.contains("jrebel") || base.startsWith("com.zeroturnaround")
                || base.startsWith("org.zeroturnaround")) {
            return "JRebel";
        }
        if (base.startsWith("org.netbeans.") || base.startsWith("org.openide.")
                || base.startsWith("org.apache.") || base.startsWith("com.sun.")
                || base.startsWith("org.eclipse.")) {
            return "NetBeans";
        }
        return "Otros";
    }

    private static String plain(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private void showLongMessage(String message, String title, int type) {
        JTextArea area = new JTextArea(message, 12, 70);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), title, type);
    }

    private record BlockItem(String name, int count) {

        @Override
        public String toString() {
            return name + " (" + count + ")";
        }
    }

    private static final class Row {

        final UpdateUnit unit;
        final UpdateElement element;
        final boolean original;
        boolean pending;
        final String display;
        final String category;
        final String code;
        final String version;
        final String description;
        final String block;

        Row(UpdateUnit unit, UpdateElement element, boolean enabled) {
            this.unit = unit;
            this.element = element;
            this.original = enabled;
            this.pending = enabled;
            this.display = plain(element.getDisplayName()).isEmpty()
                    ? unit.getCodeName() : plain(element.getDisplayName());
            String cat = plain(element.getCategory());
            this.category = cat.isEmpty() ? "Sin categoría" : cat;
            this.code = unit.getCodeName();
            this.block = blockOf(this.code);
            this.version = element.getSpecificationVersion() == null
                    ? "" : element.getSpecificationVersion();
            this.description = plain(element.getDescription());
        }
    }

    private final class PluginTableModel extends AbstractTableModel {

        private final String[] columns = {"Activo", "Módulo", "Categoría", "Código", "Versión"};
        private final List<Row> rows = new ArrayList<>();

        void setRows(List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }

        Row row(int index) {
            return rows.get(index);
        }

        List<Row> changedRows() {
            List<Row> changed = new ArrayList<>();
            for (Row row : rows) {
                if (row.pending != row.original) {
                    changed.add(row);
                }
            }
            return changed;
        }

        int pendingChanges() {
            return changedRows().size();
        }

        int blockCount(String block) {
            int count = 0;
            for (Row row : rows) {
                if (block.equals(row.block)) {
                    count++;
                }
            }
            return count;
        }

        void discardChanges() {
            for (Row row : rows) {
                row.pending = row.original;
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Row item = rows.get(row);
            return switch (column) {
                case 0 -> item.pending;
                case 1 -> item.display;
                case 2 -> item.category;
                case 3 -> item.code;
                case 4 -> item.version;
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && value instanceof Boolean enabled) {
                rows.get(row).pending = enabled;
                fireTableCellUpdated(row, column);
                updateStatus();
                showDetail();
            }
        }
    }
}

package org.deepsetk.music;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Ventana con la lista de reproduccion, los botones para agregar musica y el
 * volumen. Los controles basicos tambien estan en la barra de herramientas.
 */
public final class PlaylistTopComponent extends TopComponent {

    private static PlaylistTopComponent instance;

    private final Player player = Player.get();
    private final DefaultListModel<Track> model = new DefaultListModel<>();
    private final JList<Track> list = new JList<>(model);
    private final JLabel status = new JLabel();
    private final JSlider volume = new JSlider(0, 100);
    private final JButton remove = new JButton("Quitar");
    private boolean refreshing;

    public static synchronized PlaylistTopComponent create() {
        if (instance == null) {
            instance = new PlaylistTopComponent();
        }
        return instance;
    }

    public static PlaylistTopComponent findInstance() {
        PlaylistTopComponent found = instance;
        if (found == null) {
            for (TopComponent component : WindowManager.getDefault().getRegistry().getOpened()) {
                if (component instanceof PlaylistTopComponent candidate) {
                    found = candidate;
                    break;
                }
            }
        }
        return found == null ? create() : found;
    }

    /** Constructor publico sin argumentos: lo necesita el sistema de ventanas. */
    public PlaylistTopComponent() {
        super();
        instance = this;
        setName("MusicPlaylist");
        setDisplayName("Música");
        setToolTipText("Reproductor de música local");
        setIcon(Icons.listImage());
        buildUi();
        player.addListener(source -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    private void buildUi() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        status.setText("Detenido");
        add(status, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setToolTipText("Doble clic para reproducir");
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    int row = list.locationToIndex(event.getPoint());
                    if (row >= 0) {
                        var bounds = list.getCellBounds(row, row);
                        if (bounds != null && bounds.contains(event.getPoint())) {
                            player.play(row);
                        }
                    }
                }
            }
        });
        list.addListSelectionListener(event -> remove.setEnabled(list.getSelectedIndices().length > 0));
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addFolder = new JButton("Carpeta…", Icons.folder());
        addFolder.setToolTipText("Agrega los MP3, FLAC, OGG y WAV de una carpeta y sus subcarpetas");
        addFolder.addActionListener(event -> addFolder());

        JButton addFiles = new JButton("Archivos…");
        addFiles.setToolTipText("Agrega archivos de audio sueltos");
        addFiles.addActionListener(event -> addFiles());

        remove.setEnabled(false);
        remove.addActionListener(event -> removeSelected());

        JButton clear = new JButton("Vaciar");
        clear.addActionListener(event -> clearAll());

        buttons.add(addFolder);
        buttons.add(addFiles);
        buttons.add(remove);
        buttons.add(clear);

        JPanel volumeRow = new JPanel(new BorderLayout(6, 0));
        volume.setValue(player.volume());
        volume.setToolTipText("Volumen");
        volume.addChangeListener(event -> {
            if (!refreshing) {
                player.setVolume(volume.getValue());
            }
        });
        volumeRow.add(new JLabel("Volumen"), BorderLayout.WEST);
        volumeRow.add(volume, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(buttons, BorderLayout.NORTH);
        south.add(volumeRow, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
    }

    private void addFolder() {
        JFileChooser chooser = new JFileChooser(MusicSettings.loadLastFolder());
        chooser.setDialogTitle("Carpeta con música");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dir = chooser.getSelectedFile();
        MusicSettings.saveLastFolder(dir);
        status.setText("Buscando audio en " + dir.getName() + "…");
        Cursor previous = getCursor();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        Thread scan = new Thread(() -> {
            List<Track> found = List.of();
            String problem = null;
            try {
                found = scanFolder(dir.toPath());
            } catch (IOException ex) {
                problem = ex.getMessage();
            }
            List<Track> tracks = found;
            String error = problem;
            SwingUtilities.invokeLater(() -> {
                setCursor(previous);
                if (error != null) {
                    status.setText("No se pudo leer la carpeta: " + error);
                    return;
                }
                int added = player.addTracks(tracks);
                if (tracks.isEmpty()) {
                    status.setText("Esa carpeta no tiene archivos MP3, FLAC, OGG ni WAV");
                } else if (added == 0) {
                    status.setText("Esas canciones ya estaban en la lista");
                } else {
                    status.setText(added + " canción(es) agregada(s)");
                }
                refresh();
            });
        }, "DeepSetk-Music-Scan");
        scan.setDaemon(true);
        scan.start();
    }

    private void addFiles() {
        JFileChooser chooser = new JFileChooser(MusicSettings.loadLastFolder());
        chooser.setDialogTitle("Archivos de audio");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Audio (MP3, FLAC, OGG, WAV)", "mp3", "flac", "ogg", "oga", "wav"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] files = chooser.getSelectedFiles();
        List<Track> picked = new ArrayList<>();
        for (File file : files) {
            if (AudioCodec.isSupported(file.toPath())) {
                picked.add(new Track(file.toPath()));
            }
        }
        int added = player.addTracks(picked);
        if (files.length > 0 && files[0].getParentFile() != null) {
            MusicSettings.saveLastFolder(files[0].getParentFile());
        }
        status.setText(added == 0
                ? "Esos archivos ya estaban en la lista"
                : added + " canción(es) agregada(s)");
        refresh();
    }

    private void removeSelected() {
        int[] rows = list.getSelectedIndices();
        if (rows.length > 0) {
            player.removeTracks(rows);
            refresh();
        }
    }

    private void clearAll() {
        if (model.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "¿Vaciar la lista de reproducción?\nLos archivos no se borran del disco.",
                "Vaciar lista", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            player.clear();
            refresh();
        }
    }

    private static List<Track> scanFolder(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(AudioCodec::isSupported)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .map(Track::new)
                    .collect(Collectors.toList());
        }
    }

    private void refresh() {
        List<Track> data = player.tracks();
        if (!sameAsModel(data)) {
            model.clear();
            for (Track track : data) {
                model.addElement(track);
            }
        }

        int current = player.index();
        if (current >= 0 && current < model.size()) {
            list.setSelectedIndex(current);
            list.ensureIndexIsVisible(current);
        } else {
            list.clearSelection();
        }

        Player.State state = player.state();
        Track track = player.currentTrack();
        String text;
        if (state == Player.State.PLAYING) {
            text = track == null ? "Reproduciendo" : "Reproduciendo: " + track.title();
        } else if (state == Player.State.PAUSED) {
            text = track == null ? "En pausa" : "En pausa: " + track.title();
        } else if (data.isEmpty()) {
            text = "Lista vacía: agrega una carpeta de música";
        } else if (track != null) {
            text = "Detenido — " + track.title();
        } else {
            text = "Detenido";
        }
        status.setText(text);

        refreshing = true;
        try {
            volume.setValue(player.volume());
        } finally {
            refreshing = false;
        }
        remove.setEnabled(list.getSelectedIndices().length > 0);
    }

    private boolean sameAsModel(List<Track> data) {
        if (model.size() != data.size()) {
            return false;
        }
        for (int i = 0; i < data.size(); i++) {
            if (!model.get(i).equals(data.get(i))) {
                return false;
            }
        }
        return true;
    }
}

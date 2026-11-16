package org.deepsetk.music;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * Persistencia de la lista, la pista actual, el volumen y la ultima carpeta.
 * Usa las preferencias del IDE (una cadena con una ruta por linea).
 */
final class MusicSettings {

    private static final Logger LOG = Logger.getLogger(MusicSettings.class.getName());
    private static final String KEY_PLAYLIST = "playlist";
    private static final String KEY_INDEX = "index";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_FOLDER = "lastFolder";

    private MusicSettings() {
    }

    private static Preferences prefs() {
        return NbPreferences.forModule(Player.class);
    }

    static List<Track> loadPlaylist() {
        String raw = prefs().get(KEY_PLAYLIST, "");
        List<Track> tracks = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String text = line.strip();
            if (text.isEmpty()) {
                continue;
            }
            try {
                Path path = Path.of(text);
                if (Files.isRegularFile(path) && AudioCodec.isSupported(path)) {
                    tracks.add(new Track(path));
                }
            } catch (RuntimeException badPath) {
                LOG.log(Level.FINE, "Ruta invalida en la lista: {0}", text);
            }
        }
        return tracks;
    }

    static void savePlaylist(List<Track> tracks) {
        StringBuilder sb = new StringBuilder();
        for (Track track : tracks) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(track.file());
        }
        prefs().put(KEY_PLAYLIST, sb.toString());
        flush();
    }

    static int loadIndex() {
        return prefs().getInt(KEY_INDEX, -1);
    }

    static void saveIndex(int index) {
        prefs().putInt(KEY_INDEX, index);
        flush();
    }

    static int loadVolume() {
        int value = prefs().getInt(KEY_VOLUME, 70);
        return Math.max(0, Math.min(100, value));
    }

    static void saveVolume(int volume) {
        prefs().putInt(KEY_VOLUME, volume);
        flush();
    }

    static File loadLastFolder() {
        String text = prefs().get(KEY_FOLDER, "");
        if (!text.isEmpty()) {
            File dir = new File(text);
            if (dir.isDirectory()) {
                return dir;
            }
        }
        return new File(System.getProperty("user.home", "."));
    }

    static void saveLastFolder(File dir) {
        if (dir != null) {
            prefs().put(KEY_FOLDER, dir.getAbsolutePath());
            flush();
        }
    }

    private static void flush() {
        try {
            prefs().flush();
        } catch (BackingStoreException ex) {
            LOG.log(Level.FINE, "No se pudieron guardar las preferencias de musica", ex);
        }
    }
}

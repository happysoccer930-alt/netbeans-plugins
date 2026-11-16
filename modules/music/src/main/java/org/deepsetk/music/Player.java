package org.deepsetk.music;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javax.swing.SwingUtilities;
import org.openide.awt.StatusDisplayer;

/**
 * Reproductor unico del IDE. La lista, el indice y el volumen se guardan en
 * preferencias, de modo que sobreviven al reinicio.
 */
public final class Player {

    public enum State {
        STOPPED, PLAYING, PAUSED
    }

    @FunctionalInterface
    public interface Listener {

        void playerChanged(Player player);
    }

    private static final Logger LOG = Logger.getLogger(Player.class.getName());
    private static Player instance;

    private final List<Track> tracks = new ArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private Playback current;
    private State state = State.STOPPED;
    private int index = -1;
    private volatile int volume = 70;

    public static synchronized Player get() {
        if (instance == null) {
            instance = new Player();
        }
        return instance;
    }

    static synchronized void shutdown() {
        if (instance != null) {
            instance.stop();
            instance.listeners.clear();
        }
    }

    private Player() {
        volume = MusicSettings.loadVolume();
        tracks.addAll(MusicSettings.loadPlaylist());
        int saved = MusicSettings.loadIndex();
        if (!tracks.isEmpty() && saved >= 0) {
            index = Math.min(saved, tracks.size() - 1);
        }
    }

    // ------------------------------------------------------------------ lista

    public synchronized List<Track> tracks() {
        return List.copyOf(tracks);
    }

    public synchronized int index() {
        return index;
    }

    public synchronized Track currentTrack() {
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    public int addTracks(Collection<Track> found) {
        int added = 0;
        synchronized (this) {
            Set<String> keys = new HashSet<>();
            for (Track track : tracks) {
                keys.add(key(track));
            }
            for (Track track : found) {
                if (keys.add(key(track))) {
                    tracks.add(track);
                    added++;
                }
            }
            if (added > 0) {
                MusicSettings.savePlaylist(tracks);
            }
        }
        if (added > 0) {
            fire();
        }
        return added;
    }

    public void removeTracks(int[] rows) {
        List<Integer> ordered = new ArrayList<>();
        for (int row : rows) {
            if (row >= 0 && !ordered.contains(row)) {
                ordered.add(row);
            }
        }
        if (ordered.isEmpty()) {
            return;
        }
        int[] sorted = ordered.stream().mapToInt(Integer::intValue).sorted().toArray();
        synchronized (this) {
            boolean currentRemoved = false;
            for (int pos = sorted.length - 1; pos >= 0; pos--) {
                int row = sorted[pos];
                if (row >= tracks.size()) {
                    continue;
                }
                if (row == index) {
                    currentRemoved = true;
                }
                tracks.remove(row);
                if (row < index) {
                    index--;
                }
            }
            if (currentRemoved) {
                halt();
                state = State.STOPPED;
            }
            if (index >= tracks.size()) {
                index = tracks.size() - 1;
            }
            if (tracks.isEmpty()) {
                index = -1;
            }
            MusicSettings.savePlaylist(tracks);
            MusicSettings.saveIndex(index);
        }
        fire();
    }

    public void clear() {
        synchronized (this) {
            halt();
            tracks.clear();
            index = -1;
            state = State.STOPPED;
            MusicSettings.savePlaylist(tracks);
            MusicSettings.saveIndex(index);
        }
        fire();
    }

    private static String key(Track track) {
        return track.file().toString().toLowerCase(Locale.ROOT);
    }

    // -------------------------------------------------------------- controles

    public synchronized State state() {
        return state;
    }

    public void toggle() {
        switch (state()) {
            case PLAYING ->
                pause();
            case PAUSED ->
                resume();
            default -> {
                int position = index();
                play(position < 0 ? 0 : position);
            }
        }
    }

    public void play(int position) {
        Playback playback;
        synchronized (this) {
            if (tracks.isEmpty()) {
                return;
            }
            int target = Math.max(0, Math.min(position, tracks.size() - 1));
            halt();
            index = target;
            state = State.PLAYING;
            playback = new Playback(tracks.get(target).file());
            current = playback;
            MusicSettings.saveIndex(index);
        }
        fire();
        Thread thread = new Thread(() -> run(playback), "DeepSetk-Music-Player");
        thread.setDaemon(true);
        thread.start();
    }

    public void pause() {
        Playback playback;
        synchronized (this) {
            playback = current;
            if (playback == null || state != State.PLAYING) {
                return;
            }
            playback.paused = true;
            state = State.PAUSED;
        }
        SourceDataLine line = playback.line;
        if (line != null) {
            try {
                line.stop();
                line.flush();
            } catch (RuntimeException ignored) {
            }
        }
        fire();
    }

    public void resume() {
        Playback playback;
        synchronized (this) {
            playback = current;
            if (playback == null || state != State.PAUSED) {
                return;
            }
            playback.paused = false;
            state = State.PLAYING;
        }
        SourceDataLine line = playback.line;
        if (line != null) {
            try {
                line.start();
            } catch (RuntimeException ignored) {
            }
        }
        synchronized (playback.waitLock) {
            playback.waitLock.notifyAll();
        }
        fire();
    }

    public void stop() {
        synchronized (this) {
            halt();
            state = State.STOPPED;
        }
        fire();
    }

    public void next() {
        int target;
        synchronized (this) {
            if (tracks.isEmpty()) {
                return;
            }
            target = index < 0 ? 0 : (index + 1) % tracks.size();
        }
        play(target);
    }

    public void previous() {
        int target;
        synchronized (this) {
            if (tracks.isEmpty()) {
                return;
            }
            target = index <= 0 ? tracks.size() - 1 : index - 1;
        }
        play(target);
    }

    public void setVolume(int value) {
        int level = Math.max(0, Math.min(100, value));
        Playback playback;
        synchronized (this) {
            if (volume == level) {
                return;
            }
            volume = level;
            playback = current;
        }
        MusicSettings.saveVolume(level);
        SourceDataLine line = playback == null ? null : playback.line;
        if (line != null) {
            applyGain(line, level);
        }
    }

    public synchronized int volume() {
        return volume;
    }

    // ------------------------------------------------------------- escucha UI

    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void fire() {
        if (listeners.isEmpty()) {
            return;
        }
        Runnable task = () -> {
            for (Listener listener : listeners) {
                try {
                    listener.playerChanged(this);
                } catch (RuntimeException ex) {
                    LOG.log(Level.FINE, "Fallo un listener del reproductor", ex);
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    // ------------------------------------------------------------- reproduccion

    private void run(Playback playback) {
        AudioInputStream stream = null;
        SourceDataLine line = null;
        boolean natural = false;
        try {
            stream = AudioCodec.openPcm(playback.file);
            AudioFormat format = stream.getFormat();
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
            playback.line = line;
            line.open(format, 1 << 16);
            applyGain(line, volume);
            if (!playback.paused && !playback.aborted) {
                line.start();
            }

            byte[] buffer = new byte[1 << 14];
            while (!playback.aborted) {
                synchronized (playback.waitLock) {
                    while (playback.paused && !playback.aborted) {
                        playback.waitLock.wait();
                    }
                }
                if (playback.aborted) {
                    break;
                }
                int read = stream.read(buffer, 0, buffer.length);
                if (read < 0) {
                    natural = !playback.aborted;
                    break;
                }
                if (read == 0) {
                    // VorbisSPI decodifica en un hilo aparte: al principio puede
                    // devolver 0 mientras llena su buffer.
                    Thread.sleep(5);
                    continue;
                }
                line.write(buffer, 0, read);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            if (!playback.aborted) {
                report(playback, ex);
            }
        } finally {
            if (line != null) {
                try {
                    if (playback.aborted) {
                        line.stop();
                        line.flush();
                    } else {
                        line.drain();
                        line.stop();
                    }
                } catch (RuntimeException ignored) {
                }
                line.close();
            }
            closeQuietly(stream);
            synchronized (this) {
                if (current == playback) {
                    current = null;
                }
            }
        }
        if (natural && !playback.aborted) {
            finished();
        }
    }

    private void finished() {
        int next;
        synchronized (this) {
            if (current != null || state != State.PLAYING) {
                return;
            }
            next = index + 1;
            if (next >= tracks.size()) {
                state = State.STOPPED;
                fire();
                return;
            }
        }
        SwingUtilities.invokeLater(() -> {
            synchronized (this) {
                if (state != State.PLAYING || current != null) {
                    return;
                }
            }
            play(next);
        });
    }

    private void report(Playback playback, Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        LOG.log(Level.WARNING, "No se pudo reproducir " + playback.file, ex);
        synchronized (this) {
            state = State.STOPPED;
        }
        String name = playback.file.getFileName() == null
                ? playback.file.toString()
                : playback.file.getFileName().toString();
        String text = "Música: no se pudo reproducir " + name + " (" + message + ")";
        SwingUtilities.invokeLater(() -> {
            try {
                StatusDisplayer.getDefault().setStatusText(text);
            } catch (RuntimeException ignored) {
            }
        });
        fire();
    }

    private void halt() {
        Playback playback = current;
        if (playback == null) {
            return;
        }
        playback.aborted = true;
        playback.paused = false;
        SourceDataLine line = playback.line;
        if (line != null) {
            try {
                line.stop();
                line.flush();
            } catch (RuntimeException ignored) {
            }
        }
        synchronized (playback.waitLock) {
            playback.waitLock.notifyAll();
        }
    }

    private static void applyGain(SourceDataLine line, int volume) {
        try {
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            if (gain == null) {
                return;
            }
            double decibels = volume <= 0 ? gain.getMinimum() : 20.0 * Math.log10(volume / 100.0);
            decibels = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels));
            gain.setValue((float) decibels);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void closeQuietly(AudioInputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class Playback {

        final Path file;
        final Object waitLock = new Object();
        volatile boolean aborted;
        volatile boolean paused;
        volatile SourceDataLine line;

        Playback(Path file) {
            this.file = file;
        }
    }
}

package org.deepsetk.music;

import java.nio.file.Path;

/**
 * Una cancion de la lista: ruta absoluta + titulo legible derivado del nombre.
 */
public final class Track {

    private final Path file;
    private final String title;

    public Track(Path file) {
        this.file = file.toAbsolutePath().normalize();
        this.title = titleOf(this.file);
    }

    public Path file() {
        return file;
    }

    public String title() {
        return title;
    }

    static String titleOf(Path path) {
        Path name = path.getFileName();
        String text = name == null ? path.toString() : name.toString();
        int dot = text.lastIndexOf('.');
        if (dot > 0) {
            text = text.substring(0, dot);
        }
        text = text.replace('_', ' ').trim();
        return text.isEmpty() ? path.toString() : text;
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Track other && other.file.equals(file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }
}

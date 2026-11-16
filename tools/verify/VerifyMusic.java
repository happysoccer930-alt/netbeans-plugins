package org.deepsetk.music;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import org.openide.filesystems.XMLFileSystem;

/**
 * Verificacion offline del modulo music: layer, titulos, iconos y decodificacion
 * real de WAV, MP3, FLAC (16 y 24 bits) y OGG generados con ffmpeg.
 */
public final class VerifyMusic {

    private static int failures;

    public static void main(String[] args) throws Exception {
        String resDir = args[0];
        Path audioDir = Path.of(args[1]);

        XMLFileSystem fs = new XMLFileSystem(new File(resDir, "layer.xml").toURI().toURL());
        check(fs.findResource("Actions/Music/org-deepsetk-music-play.instance") != null,
                "accion reproducir/pausar registrada");
        check(fs.findResource("Actions/Music/org-deepsetk-music-stop.instance") != null,
                "accion detener registrada");
        check(fs.findResource("Actions/Music/org-deepsetk-music-next.instance") != null,
                "accion siguiente registrada");
        check(fs.findResource("Actions/Music/org-deepsetk-music-prev.instance") != null,
                "accion anterior registrada");
        check(fs.findResource("Toolbars/File/org-deepsetk-music-play.shadow") != null,
                "control en la barra de herramientas");
        check(fs.findResource("Menu/Window/org-deepsetk-music-show.shadow") != null,
                "menu Ventana -> Lista de musica");
        check(fs.findResource("Menu/Tools/org-deepsetk-music-show.shadow") != null,
                "menu Herramientas -> Lista de musica");
        check(fs.findResource("Windows2/Components/MusicPlaylist.settings") != null,
                "ventana de lista registrada");
        check(fs.findResource("Windows2/Modes/explorer/MusicPlaylist.wstcref") != null,
                "ventana ubicada en el modo explorer");
        check(contentOf(fs, "Windows2/Components/MusicPlaylist.settings")
                .contains("class=\"org.deepsetk.music.PlaylistTopComponent\""),
                "settings apunta a la clase de la ventana");
        check(contentOf(fs, "Windows2/Modes/explorer/MusicPlaylist.wstcref")
                .contains("<tc-id id=\"MusicPlaylist\"/>"),
                "wstcref usa el id MusicPlaylist");

        check("Mi cancion".equals(new Track(Path.of("C:/tmp/Mi_cancion.mp3")).title()),
                "titulo: sin extension y con espacios");
        check("01 - Tema".equals(new Track(Path.of("C:/tmp/01 - Tema.mp3")).title()),
                "titulo: conserva espacios y guiones");
        check(AudioCodec.isSupported(Path.of("x.MP3")), "extension MP3 en mayusculas");
        check(AudioCodec.isSupported(Path.of("x.oga")), "extension OGA");
        check(!AudioCodec.isSupported(Path.of("x.txt")), "extension no soportada rechazada");

        check(Icons.play() != null && Icons.pause() != null && Icons.next() != null,
                "iconos de acciones creados");
        check(Icons.listImage().getWidth() == 16 && Icons.listImage().getHeight() == 16,
                "icono de ventana 16x16");

        List<String> samples = List.of("test.wav", "test.mp3", "test.flac", "test24.flac", "test.ogg");
        for (String name : samples) {
            Path file = audioDir.resolve(name);
            if (!Files.isRegularFile(file)) {
                check(false, "falta el archivo de prueba " + name);
                continue;
            }
            long start = System.nanoTime();
            try (AudioInputStream in = AudioCodec.openPcm(file)) {
                AudioFormat format = in.getFormat();
                check(AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()), name + ": PCM firmado");
                check(Math.round(format.getSampleRate()) == 44100, name + ": 44.1 kHz");
                check(format.getChannels() == 2, name + ": estereo");

                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                int idle = 0;
                while (total < 44100 && (read = in.read(buffer, 0, buffer.length)) >= 0) {
                    if (read == 0) {
                        // VorbisSPI decodifica de forma asincrona.
                        if (++idle > 200) {
                            break;
                        }
                        Thread.sleep(5);
                        continue;
                    }
                    idle = 0;
                    total += read;
                }
                check(total > 0, name + ": " + total + " bytes de audio");
                long millis = (System.nanoTime() - start) / 1_000_000L;
                System.out.println("       (" + millis + " ms, " + format + ")");
            }
        }

        System.out.println(failures == 0 ? "VERIFICACION OK" : "FALLOS: " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static String contentOf(XMLFileSystem fs, String path) throws Exception {
        var file = fs.findResource(path);
        if (file == null) {
            return "";
        }
        try (java.io.InputStream in = file.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  OK   " : "  FALLO ") + what);
        if (!ok) {
            failures++;
        }
    }
}

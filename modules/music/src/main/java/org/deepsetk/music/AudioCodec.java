package org.deepsetk.music;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.FormatConversionProvider;
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import javazoom.spi.mpeg.sampled.file.MpegEncoding;
import javazoom.spi.vorbis.sampled.convert.VorbisFormatConversionProvider;
import javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader;
import javazoom.spi.vorbis.sampled.file.VorbisEncoding;
import org.jflac.sound.spi.FlacAudioFileReader;
import org.jflac.sound.spi.FlacEncoding;
import org.jflac.sound.spi.FlacFormatConversionProvider;

/**
 * Abre los formatos soportados y los convierte a PCM listo para una linea de
 * audio. Se usan los decodificadores directamente (sin depender del registro
 * SPI global de {@link AudioSystem}) porque el modulo vive en su propio
 * classloader.
 *
 * Los formatos M4A/AAC/MP4 se decodifican con ffmpeg a PCM en memoria, sin
 * convertir ni duplicar archivos en disco.
 */
final class AudioCodec {

    static final Set<String> EXTENSIONS = Set.of(
            "mp3", "flac", "ogg", "oga", "wav", "m4a", "aac", "mp4", "m4b");

    private static volatile String ffmpegPath;
    private static volatile boolean ffmpegChecked;

    private AudioCodec() {
    }

    static boolean isSupported(Path path) {
        String ext = extension(path);
        return ext != null && EXTENSIONS.contains(ext);
    }

    private static String extension(Path path) {
        Path name = path.getFileName();
        if (name == null) {
            return null;
        }
        String text = name.toString().toLowerCase(Locale.ROOT);
        int dot = text.lastIndexOf('.');
        return dot < 0 ? null : text.substring(dot + 1);
    }

    /** Devuelve el audio ya decodificado (o el WAV original, que ya es PCM). */
    static AudioInputStream openPcm(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            throw new IOException("No existe el archivo: " + path);
        }
        String ext = extension(path);
        if (ext == null) {
            throw new UnsupportedAudioFileException("Sin extension: " + path.getFileName());
        }
        return switch (ext) {
            case "mp3" -> pcm(new MpegAudioFileReader().getAudioInputStream(path.toFile()));
            case "flac" -> pcm(new FlacAudioFileReader().getAudioInputStream(path.toFile()));
            case "ogg", "oga" -> pcm(new VorbisAudioFileReader().getAudioInputStream(path.toFile()));
            case "wav" -> pcm(AudioSystem.getAudioInputStream(path.toFile()));
            case "m4a", "aac", "mp4", "m4b" -> ffmpegPcm(path);
            default -> throw new UnsupportedAudioFileException("Formato no soportado: " + ext);
        };
    }

    /** Decodifica con ffmpeg a PCM 16-bit 44.1 kHz estereo en memoria. */
    private static AudioInputStream ffmpegPcm(Path path) throws Exception {
        String ffmpeg = ffmpeg();
        if (ffmpeg == null) {
            throw new UnsupportedAudioFileException(
                    "Para M4A/AAC se necesita ffmpeg. Instala ffmpeg o agregalo al PATH.");
        }
        ProcessBuilder builder = new ProcessBuilder(
                ffmpeg, "-nostdin", "-v", "error",
                "-i", path.toAbsolutePath().toString(),
                "-f", "s16le", "-acodec", "pcm_s16le",
                "-ar", "44100", "-ac", "2", "-");
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        byte[] pcm;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            process.getInputStream().transferTo(out);
            pcm = out.toByteArray();
        }
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg tardo demasiado con " + path.getFileName());
        }
        if (process.exitValue() != 0 || pcm.length == 0) {
            throw new UnsupportedAudioFileException(
                    "ffmpeg no pudo decodificar " + path.getFileName());
        }
        AudioFormat format = new AudioFormat(44100f, 16, 2, true, false);
        return new AudioInputStream(new ByteArrayInputStream(pcm), format,
                pcm.length / format.getFrameSize());
    }

    private static String ffmpeg() {
        if (!ffmpegChecked) {
            synchronized (AudioCodec.class) {
                if (!ffmpegChecked) {
                    ffmpegPath = findFfmpeg();
                    ffmpegChecked = true;
                }
            }
        }
        return ffmpegPath;
    }

    private static String findFfmpeg() {
        if (runOk("ffmpeg")) {
            return "ffmpeg";
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            Path packages = Path.of(local, "Microsoft", "WinGet", "Packages");
            if (Files.isDirectory(packages)) {
                try (Stream<Path> stream = Files.walk(packages, 7)) {
                    Optional<Path> found = stream
                            .filter(p -> p.getFileName() != null
                                    && p.getFileName().toString().equalsIgnoreCase("ffmpeg.exe"))
                            .findFirst();
                    if (found.isPresent()) {
                        return found.get().toString();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        String[] common = {
            "C:\\ffmpeg\\bin\\ffmpeg.exe",
            "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
            "C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe"
        };
        for (String candidate : common) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean runOk(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "-version")
                    .redirectErrorStream(true).start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static AudioInputStream pcm(AudioInputStream encoded) throws Exception {
        AudioFormat src = encoded.getFormat();
        AudioFormat.Encoding encoding = src.getEncoding();

        if (encoding.equals(AudioFormat.Encoding.PCM_SIGNED) && !src.isBigEndian()) {
            return encoded;
        }

        FormatConversionProvider converter = converterFor(encoding);
        if (converter != null) {
            try {
                return converter.getAudioInputStream(pcmFormat(src, 16), encoded);
            } catch (IllegalArgumentException wrongDepth) {
                // FLAC de 24 bits: pedir el mismo tamano de muestra que el origen.
                int bits = src.getSampleSizeInBits() > 16 ? src.getSampleSizeInBits() : 16;
                return converter.getAudioInputStream(pcmFormat(src, bits), encoded);
            }
        }

        if (encoding.equals(AudioFormat.Encoding.PCM_UNSIGNED)
                || encoding.equals(AudioFormat.Encoding.PCM_FLOAT)
                || encoding.equals(AudioFormat.Encoding.PCM_SIGNED)
                || encoding.equals(AudioFormat.Encoding.ULAW)
                || encoding.equals(AudioFormat.Encoding.ALAW)) {
            return AudioSystem.getAudioInputStream(pcmFormat(src, 16), encoded);
        }

        throw new UnsupportedAudioFileException("Codificacion de audio no soportada: " + src);
    }

    private static FormatConversionProvider converterFor(AudioFormat.Encoding encoding) {
        if (encoding instanceof MpegEncoding) {
            return new MpegFormatConversionProvider();
        }
        if (encoding instanceof VorbisEncoding) {
            return new VorbisFormatConversionProvider();
        }
        if (encoding instanceof FlacEncoding) {
            return new FlacFormatConversionProvider();
        }
        return null;
    }

    private static AudioFormat pcmFormat(AudioFormat src, int bits) {
        int channels = Math.max(1, src.getChannels());
        float rate = src.getSampleRate();
        int frameSize = channels * Math.max(1, bits / 8);
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, bits, channels, frameSize, rate, false);
    }
}

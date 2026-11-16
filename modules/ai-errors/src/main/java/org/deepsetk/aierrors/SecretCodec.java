package org.deepsetk.aierrors;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Guarda la API key ofuscada con AES/GCM. La clave se deriva del usuario de
 * Windows y de su carpeta personal, de modo que el fichero de preferencias no
 * contiene la key en texto plano.
 *
 * Aviso honesto: esto evita que la key quede legible en un backup o en un
 * fichero sincronizado, pero no es seguridad fuerte: cualquier proceso que se
 * ejecute como tu usuario puede descifrarla.
 */
final class SecretCodec {

    private static final byte[] SALT = "DeepSetk-Gemini-v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile SecretKey cachedKey;

    private SecretCodec() {
    }

    static String encrypt(String plain) {
        if (plain == null) {
            return "";
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception ex) {
            return "";
        }
    }

    static String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length <= 12) {
                return "";
            }
            ByteBuffer buf = ByteBuffer.wrap(all);
            byte[] iv = new byte[12];
            buf.get(iv);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static SecretKey key() throws Exception {
        SecretKey local = cachedKey;
        if (local != null) {
            return local;
        }
        synchronized (SecretCodec.class) {
            local = cachedKey;
            if (local == null) {
                String user = System.getProperty("user.name", "user");
                String home = System.getProperty("user.home", "home");
                PBEKeySpec spec = new PBEKeySpec(
                        (user + '\u0000' + home).toCharArray(), SALT, 20_000, 128);
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                byte[] raw = factory.generateSecret(spec).getEncoded();
                spec.clearPassword();
                local = new SecretKeySpec(raw, "AES");
                cachedKey = local;
            }
        }
        return local;
    }
}

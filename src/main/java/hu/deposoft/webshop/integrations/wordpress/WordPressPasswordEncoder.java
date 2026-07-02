package hu.deposoft.webshop.integrations.wordpress;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies WordPress phpass "portable" password hashes ({@code $P$} / {@code $H$}).
 * Used only to authenticate migrated WooCommerce customers; on the first
 * successful login Spring Security upgrades the stored hash to bcrypt
 * (UserDetailsPasswordService), so this path is transitional.
 *
 * <p>Algorithm (Openwall phpass): {@code h = md5(salt + password)}, then
 * {@code count} times {@code h = md5(h + password)}, where {@code count = 1 <<
 * itoa64.indexOf(hash[3])} and {@code salt = hash[4..12]}; the 16 raw bytes are
 * re-encoded with phpass base64 and compared to the stored tail.
 */
public class WordPressPasswordEncoder implements PasswordEncoder {

    private static final String ITOA64 =
            "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @Override
    public String encode(CharSequence rawPassword) {
        throw new UnsupportedOperationException(
                "WordPress hashes are read-only; new passwords are encoded with bcrypt");
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.length() != 34) {
            return false;
        }
        String setting = encodedPassword.substring(0, 12);
        if (!setting.startsWith("$P$") && !setting.startsWith("$H$")) {
            return false;
        }
        int countLog2 = ITOA64.indexOf(setting.charAt(3));
        if (countLog2 < 7 || countLog2 > 30) {
            return false;
        }
        String salt = setting.substring(4, 12);
        byte[] password = rawPassword.toString().getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] hash = md5.digest(concat(salt.getBytes(StandardCharsets.UTF_8), password));
            long count = 1L << countLog2;
            for (long i = 0; i < count; i++) {
                md5.reset();
                hash = md5.digest(concat(hash, password));
            }
            String computed = setting + encode64(hash, 16);
            return constantTimeEquals(computed, encodedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** phpass base64 encoding (its own alphabet/bit order, not standard Base64). */
    private static String encode64(byte[] input, int count) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        do {
            int value = input[i++] & 0xff;
            out.append(ITOA64.charAt(value & 0x3f));
            if (i < count) {
                value |= (input[i] & 0xff) << 8;
            }
            out.append(ITOA64.charAt((value >> 6) & 0x3f));
            if (i++ >= count) {
                break;
            }
            if (i < count) {
                value |= (input[i] & 0xff) << 16;
            }
            out.append(ITOA64.charAt((value >> 12) & 0x3f));
            if (i++ >= count) {
                break;
            }
            out.append(ITOA64.charAt((value >> 18) & 0x3f));
        } while (i < count);
        return out.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}

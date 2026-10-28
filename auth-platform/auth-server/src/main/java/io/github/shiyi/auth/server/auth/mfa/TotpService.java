package io.github.shiyi.auth.server.auth.mfa;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/**
 * TOTP (RFC 6238) second factor: HMAC-SHA1, 30s step, 6 digits, ±1 step drift.
 * Compatible with Google Authenticator / Microsoft Authenticator / WeCom
 * authenticator.
 *
 * <p>Secrets are Base32 (RFC 4648, no padding) — the format authenticator apps
 * accept in otpauth:// URIs.</p>
 */
@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int DRIFT_STEPS = 1;

    private final SecureRandom random = new SecureRandom();

    /** New 160-bit secret, Base32-encoded (32 chars, no padding). */
    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** otpauth:// URI for QR-code enrollment. */
    public String otpauthUri(String issuer, String accountName, String secret) {
        String label = urlEncode(issuer + ":" + accountName);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** Verifies a 6-digit code, tolerating one step of clock drift each way. */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.length() != DIGITS) {
            return false;
        }
        long counter = System.currentTimeMillis() / 1000 / STEP_SECONDS;
        for (int offset = -DRIFT_STEPS; offset <= DRIFT_STEPS; offset++) {
            if (generate(secret, counter + offset).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String generate(String secret, long counter) {
        try {
            byte[] key = base32Decode(secret);
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    // ---------- Base32 (RFC 4648, no padding) ----------

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String encoded) {
        String s = encoded.toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "");
        int buffer = 0, bitsLeft = 0;
        byte[] out = new byte[s.length() * 5 / 8];
        int idx = 0;
        for (char c : s.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[idx++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}

package io.github.sachachtrl.matchppl.repository;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TextCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TextCipher(byte[] keyBytes) {
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("The key must be " + KEY_LENGTH_BYTES + " bytes long");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public static TextCipher fromBase64(String encodedKey) {
        return new TextCipher(Base64.getDecoder().decode(encodedKey.strip()));
    }

    public String encrypt(String plainText, String context) {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        this.random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded, String context) throws GeneralSecurityException {
        byte[] data = Base64.getDecoder().decode(encoded);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, this.key, new GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH_BYTES));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        byte[] decrypted = cipher.doFinal(data, IV_LENGTH_BYTES, data.length - IV_LENGTH_BYTES);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}

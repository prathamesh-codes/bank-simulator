package com.billdesk.pg.payments.simulator.bank.cab;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CabEncryptionUtil {

    private static final String AES_TRANSFORMATION =
            "AES/CBC/PKCS5Padding";

    private static final int AES_KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 16;

    private CabEncryptionUtil() {
    }

    public static String encrypt(
            String plaintext,
            String encryptionKey,
            String ivValue) {

        validateInputs(
                plaintext,
                encryptionKey,
                ivValue
        );

        try {
            byte[] key =
                    encryptionKey.getBytes(
                            StandardCharsets.UTF_8
                    );

            byte[] iv =
                    ivValue.getBytes(
                            StandardCharsets.UTF_8
                    );

            Cipher cipher =
                    Cipher.getInstance(
                            AES_TRANSFORMATION
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv)
            );

            byte[] ciphertext =
                    cipher.doFinal(
                            plaintext.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(ciphertext);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "CAB encryption failed",
                    e
            );
        }
    }

    public static String decrypt(
            String encryptedPayload,
            String encryptionKey,
            String ivValue) {

        if (encryptedPayload == null ||
                encryptedPayload.isBlank()) {

            throw new IllegalArgumentException(
                    "Encrypted payload cannot be blank"
            );
        }

        validateKeyAndIv(
                encryptionKey,
                ivValue
        );

        try {
            byte[] key =
                    encryptionKey.getBytes(
                            StandardCharsets.UTF_8
                    );

            byte[] iv =
                    ivValue.getBytes(
                            StandardCharsets.UTF_8
                    );

            byte[] ciphertext =
                    Base64.getUrlDecoder()
                            .decode(
                                    encryptedPayload
                            );

            Cipher cipher =
                    Cipher.getInstance(
                            AES_TRANSFORMATION
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv)
            );

            byte[] plaintext =
                    cipher.doFinal(ciphertext);

            return new String(
                    plaintext,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "CAB decryption failed",
                    e
            );
        }
    }

    private static void validateInputs(
            String plaintext,
            String encryptionKey,
            String ivValue) {

        if (plaintext == null) {
            throw new IllegalArgumentException(
                    "Plaintext cannot be null"
            );
        }

        validateKeyAndIv(
                encryptionKey,
                ivValue
        );
    }

    private static void validateKeyAndIv(
            String encryptionKey,
            String ivValue) {

        if (encryptionKey == null ||
                encryptionKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Encryption key is not configured"
            );
        }

        if (ivValue == null ||
                ivValue.isBlank()) {

            throw new IllegalArgumentException(
                    "IV is not configured"
            );
        }

        byte[] key =
                encryptionKey.getBytes(
                        StandardCharsets.UTF_8
                );

        byte[] iv =
                ivValue.getBytes(
                        StandardCharsets.UTF_8
                );

        if (key.length != AES_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "CAB AES-256 key must be exactly 32 bytes; configured value is "
                            + key.length
                            + " bytes"
            );
        }

        if (iv.length != IV_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "CAB AES CBC IV must be exactly 16 bytes; configured value is "
                            + iv.length
                            + " bytes"
            );
        }
    }
}
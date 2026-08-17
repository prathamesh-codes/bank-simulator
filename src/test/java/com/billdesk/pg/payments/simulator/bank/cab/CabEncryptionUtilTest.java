package com.billdesk.pg.payments.simulator.bank.cab;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class CabEncryptionUtilTest {

    @Value("${simulator.cab.encryption-key:}")
    private String encryptionKey;

    @Value("${simulator.cab.iv:}")
    private String iv;

    @Test
    void shouldLoadCabCryptoPropertiesFromLocalProperties() {

        assertAll(
                () -> assertNotNull(encryptionKey),
                () -> assertFalse(encryptionKey.isBlank()),
                () -> assertNotNull(iv),
                () -> assertFalse(iv.isBlank())
        );
    }

    @Test
    void shouldEncryptSuccessfully() {

        String plaintext = "TEST_TRANSACTION_DATA";

        String encrypted = CabEncryptionUtil.encrypt(
                plaintext,
                encryptionKey,
                iv
        );

        assertNotNull(encrypted);
        assertFalse(encrypted.isBlank());
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void shouldDecryptSuccessfully() {

        String plaintext = "TEST_TRANSACTION_DATA";

        String encrypted = CabEncryptionUtil.encrypt(
                plaintext,
                encryptionKey,
                iv
        );

        String decrypted = CabEncryptionUtil.decrypt(
                encrypted,
                encryptionKey,
                iv
        );

        assertEquals(plaintext, decrypted);
    }

    @Test
    void shouldCompleteEncryptionDecryptionRoundTrip() {

        String plaintext =
                "CLIENT001MERCHANT001INR1000.00REF123";

        String encrypted = CabEncryptionUtil.encrypt(
                plaintext,
                encryptionKey,
                iv
        );

        String decrypted = CabEncryptionUtil.decrypt(
                encrypted,
                encryptionKey,
                iv
        );

        assertAll(
                () -> assertNotNull(encrypted),
                () -> assertFalse(encrypted.isBlank()),
                () -> assertNotEquals(plaintext, encrypted),
                () -> assertEquals(plaintext, decrypted)
        );
    }

    @Test
    void shouldProduceSameCiphertextForSameInputAndFixedIv() {

        String plaintext = "TEST_DATA";

        String encrypted1 = CabEncryptionUtil.encrypt(
                plaintext,
                encryptionKey,
                iv
        );

        String encrypted2 = CabEncryptionUtil.encrypt(
                plaintext,
                encryptionKey,
                iv
        );

        assertEquals(encrypted1, encrypted2);
    }

    @Test
    void shouldThrowExceptionWhenEncryptionKeyIsNull() {

        assertThrows(
                RuntimeException.class,
                () -> CabEncryptionUtil.encrypt(
                        "TEST_DATA",
                        null,
                        iv
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenIvIsNull() {

        assertThrows(
                RuntimeException.class,
                () -> CabEncryptionUtil.encrypt(
                        "TEST_DATA",
                        encryptionKey,
                        null
                )
        );
    }

    @Test
    void shouldFailForInvalidIvLength() {

        assertThrows(
                RuntimeException.class,
                () -> CabEncryptionUtil.encrypt(
                        "TEST_DATA",
                        encryptionKey,
                        "SHORT_IV"
                )
        );
    }

    @Test
    void shouldFailForInvalidBase64Ciphertext() {

        assertThrows(
                RuntimeException.class,
                () -> CabEncryptionUtil.decrypt(
                        "THIS@@IS@@NOT@@BASE64",
                        encryptionKey,
                        iv
                )
        );
    }
}
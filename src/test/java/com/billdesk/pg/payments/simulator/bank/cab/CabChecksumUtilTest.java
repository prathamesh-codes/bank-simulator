package com.billdesk.pg.payments.simulator.bank.cab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class CabChecksumUtilTest {

//    @Value("${simulator.cab.checksum-key:}")
//    private String checksumKey;
//
//    @Test
//    void shouldLoadChecksumKeyFromLocalProperties() {
//        assertNotNull(checksumKey);
//        assertFalse(checksumKey.isBlank());
//    }
//
//    @Test
//    void shouldGenerateChecksumSuccessfully() {
//
//        String checksum = CabChecksumUtil.computeChecksum(
//                "TEST_TRANSACTION_DATA",
//                checksumKey
//        );
//
//        assertNotNull(checksum);
//        assertFalse(checksum.isBlank());
//    }
//
//    @Test
//    void shouldGenerateSameChecksumForSameInputAndKey() {
//
//        String input = "TEST_TRANSACTION_DATA";
//
//        String checksum1 =
//                CabChecksumUtil.computeChecksum(input, checksumKey);
//
//        String checksum2 =
//                CabChecksumUtil.computeChecksum(input, checksumKey);
//
//        assertEquals(checksum1, checksum2);
//    }
//
//    @Test
//    void shouldGenerateDifferentChecksumForDifferentInput() {
//
//        String checksum1 =
//                CabChecksumUtil.computeChecksum(
//                        "TRANSACTION_1",
//                        checksumKey
//                );
//
//        String checksum2 =
//                CabChecksumUtil.computeChecksum(
//                        "TRANSACTION_2",
//                        checksumKey
//                );
//
//        assertNotEquals(checksum1, checksum2);
//    }
//
//    @Test
//    void shouldThrowExceptionWhenChecksumKeyIsNull() {
//
//        assertThrows(
//                IllegalArgumentException.class,
//                () -> CabChecksumUtil.computeChecksum(
//                        "TEST_DATA",
//                        null
//                )
//        );
//    }
//
//    @Test
//    void shouldThrowExceptionWhenChecksumKeyIsBlank() {
//
//        assertThrows(
//                IllegalArgumentException.class,
//                () -> CabChecksumUtil.computeChecksum(
//                        "TEST_DATA",
//                        ""
//                )
//        );
//    }
}
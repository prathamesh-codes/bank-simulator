package com.billdesk.pg.payments.simulator.bank.cab;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CabPayloadGeneratorTest {

    private static final String CHECKSUM_KEY =
            "12345678901234567890123456789012";

    private static final String ENCRYPTION_KEY =
            "12345678901234567890123456789012";

    private static final String IV =
            "1234567890ABCDEF";


    /**
     * STEP 1
     *
     * Generates:
     * 1. checksum input
     * 2. checkval
     * 3. plaintext payload
     * 4. encrypted data
     * 5. ready-to-use curl
     */
    @Test
    void generateStep1EncryptedPayload() {

        String pid = "00000000023";

        String biller_name = "MERCNAME";
        
        String payment_ref_no = "CABTEST005";
        
        String currency = "INR";
        
        String account_no = "12345678";
        
        Map<String, String> fields =
                new LinkedHashMap<>();

        fields.put(
                "mode",
                "P"
        );

        fields.put(
                "payee_id",
                pid
        );

        fields.put(
                "biller_name",
                biller_name
        );

        fields.put(
                "payment_ref_no",
                payment_ref_no
        );

        fields.put(
                "amount",
                "100.00"
        );

        fields.put(
                "currency",
                currency
        );

        fields.put(
                "return_url",
                "http://localhost:9999/unused"
        );

        fields.put(
                "account_no",
                account_no
        );
        
        System.out.println("[88] fields: " + fields);


        /*
         * Generate Step 1 checksum.
         */
        String checksumInput =
                CabChecksumUtil
                        .buildInitChecksumString(
                                fields,
                                CHECKSUM_KEY
                        );

        String checkval =
                CabChecksumUtil
                        .computeChecksum(
                                checksumInput
                        );

        fields.put(
                "checkval",
                checkval
        );


        /*
         * Convert fields into CAB's
         * key=value|key=value format.
         */
        String plaintext =
                buildPipePayload(fields);


        /*
         * Encrypt using the exact same
         * implementation used by CabService.
         */
        String encryptedData =
                CabEncryptionUtil.encrypt(
                        plaintext,
                        ENCRYPTION_KEY,
                        IV
                );


        assertNotNull(encryptedData);


        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "CAB STEP 1 PAYLOAD"
        );
        System.out.println(
                "========================================"
        );

        System.out.println(
                "PID:"
        );

        System.out.println(
                pid
        );

        System.out.println();

        System.out.println(
                "Checksum Input:"
        );

        System.out.println(
                checksumInput
        );

        System.out.println();

        System.out.println(
                "Checkval:"
        );

        System.out.println(
                checkval
        );

        System.out.println();

        System.out.println(
                "Plaintext:"
        );

        System.out.println(
                plaintext
        );

        System.out.println();

        System.out.println(
                "Encrypted data:"
        );

        System.out.println(
                encryptedData
        );

        System.out.println();

        System.out.println(
                "CURL:"
        );

        System.out.println(
                buildStep1Curl(
                        pid,
                        encryptedData
                )
        );

        System.out.println(
                "========================================"
        );
    }


    /**
     * STEP 3
     *
     * Generates the final Bank -> BillDesk
     * confirmation payload.
     *
     * Change SUCCESS to false to generate
     * a failure response.
     */
    @Test
    void generateStep3EncryptedPayload() {

        boolean success = true;

        String paymentRefNo =
                "CABTEST005";

        String bankRefNo =
                "SIM924331983568";

//        String failureReason =
//                "";
        
        String pid = "00000000023";
        
        String biller_name = "MERCNAME";
        
        String currency = "INR";


        Map<String, String> fields =
                new LinkedHashMap<>();


        fields.put("mode", "online");
        
//        fields.put(
//                "status",
//                success
//                        ? "Y"
//                        : "N"
//        );

        fields.put(
                "payment_ref_no",
                paymentRefNo
        );
        
        fields.put("payee_id", pid);

        fields.put(
                "biller_name",
                biller_name
        );

        fields.put(
                "bank_ref_no",
                bankRefNo
        );

        fields.put(
                "amount",
                "100.00"
        );

        fields.put(
                "currency",
                currency
        );

//        fields.put(
//                "error_msg",
//                success
//                        ? "Transaction successful"
//                        : failureReason
//        );
        
        System.out.println("[271] Fields: " + fields);


        /*
         * Generate Step 3 checksum.
         */
        String checksumInput =
                CabChecksumUtil
                        .buildTestApi3ChecksumString(
                                fields,
                                CHECKSUM_KEY
                        );


        String checkval =
                CabChecksumUtil
                        .computeChecksum(
                                checksumInput
                        );


        fields.put(
                "checkval",
                checkval
        );


        String plaintext =
                buildPipePayload(
                        fields
                );


        String encryptedData =
                CabEncryptionUtil.encrypt(
                        plaintext,
                        ENCRYPTION_KEY,
                        IV
                );


        assertNotNull(
                encryptedData
        );


        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "CAB STEP 3 PAYLOAD"
        );

        System.out.println(
                "========================================"
        );


        System.out.println(
                "Result:"
        );

        System.out.println(
                success
                        ? "SUCCESS"
                        : "FAILURE"
        );


        System.out.println();

        System.out.println(
                "Checksum Input:"
        );

        System.out.println(
                checksumInput
        );


        System.out.println();

        System.out.println(
                "Checkval:"
        );

        System.out.println(
                checkval
        );


        System.out.println();

        System.out.println(
                "Plaintext:"
        );

        System.out.println(
                plaintext
        );


        System.out.println();

        System.out.println(
                "Encrypted data:"
        );

        System.out.println(
                encryptedData
        );


        System.out.println();

        System.out.println(
                "POST BODY:"
        );

        System.out.println(
                "data=" + encryptedData
        );


        System.out.println(
                "========================================"
        );
    }


    private String buildPipePayload(
            Map<String, String> fields) {

        StringBuilder builder =
                new StringBuilder();


        for (Map.Entry<String, String> entry :
                fields.entrySet()) {

            if (builder.length() > 0) {

                builder.append(
                        "|"
                );
            }

            builder.append(
                    entry.getKey()
            );

            builder.append(
                    "="
            );

            builder.append(
                    safe(
                            entry.getValue()
                    )
            );
        }


        return builder.toString();
    }


    private String buildStep1Curl(
            String pid,
            String encryptedData) {

        return "curl.exe -i -X POST "
                + "\"http://localhost:7000/simulator/netbanking/CAB\" "
                + "--data-urlencode \"PID="
                + pid
                + "\" "
                + "--data-urlencode \"data="
                + encryptedData
                + "\"";
    }


    private String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
}
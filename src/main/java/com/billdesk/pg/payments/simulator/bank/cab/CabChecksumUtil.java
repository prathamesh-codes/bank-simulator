package com.billdesk.pg.payments.simulator.bank.cab;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore.Entry;
import java.security.MessageDigest;
import java.util.Map;

public final class CabChecksumUtil {

    private CabChecksumUtil() {
    }

    public static String computeChecksum(String input) {

        if (input == null) {
            throw new IllegalArgumentException(
                    "Checksum input cannot be null"
            );
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            input.getBytes(StandardCharsets.UTF_8)
                    );

            return toHex(hash);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to compute CAB checksum",
                    e
            );
        }
    }

    private static String toHex(byte[] bytes) {

        StringBuilder result =
                new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            result.append(
                    String.format("%02x", b)
            );
        }

        return result.toString();
    }
    
    static String buildInitChecksumString(
            Map<String, String> fields,
            String checksumKey) {

        return safe(fields.get("mode"))
                + "|" + safe(fields.get("payee_id"))
                + "|" + safe(fields.get("biller_name"))
                + "|" + safe(fields.get("payment_ref_no"))
                + "|" + safe(fields.get("amount"))
                + "|" + safe(fields.get("currency"))
                + "|" + safe(fields.get("return_url"))
                + "|" + safe(fields.get("account_no"))
                + "|" + checksumKey;
    }

    static String buildCallbackChecksumString(
            Map<String, String> fields,
            String checksumKey) {

        return safe(fields.get("status"))
                + "|" + safe(fields.get("payment_ref_no"))
                + "|" + safe(fields.get("biller_name"))
                + "|" + safe(fields.get("bank_ref_no"))
                + "|" + safe(fields.get("amount"))
                + "|" + safe(fields.get("account_no"))
                + "|" + safe(fields.get("error_msg"))
                + "|" + checksumKey;
    }

    static String buildVerificationRequestChecksumString(
            Map<String, String> fields,
            String checksumKey) {

        return safe(fields.get("mode"))
                + "|" + safe(fields.get("payment_ref_no"))
                + "|" + safe(fields.get("payee_id"))
                + "|" + safe(fields.get("biller_name"))
                + "|" + safe(fields.get("bank_ref_no"))
                + "|" + safe(fields.get("amount"))
                + "|" + safe(fields.get("currency"))
                + "|" + checksumKey;
    }

    static String buildVerificationResponseChecksumString(
            Map<String, String> fields,
            String checksumKey) {

        return safe(fields.get("status"))
                + "|" + safe(fields.get("payment_ref_no"))
                + "|" + safe(fields.get("bank_ref_no"))
                + "|" + safe(fields.get("amount"))
                + "|" + safe(fields.get("error_msg"))
                + "|" + checksumKey;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
    
    static String buildTestApi3ChecksumString(
    		Map<String, String> fields,
    		String checksumKey) {
    	String finalChecksumString = "";
    	
    	for(Map.Entry<String, String> entry : fields.entrySet()) {
    		finalChecksumString += safe(entry.getValue()) + "|";
    	}
    	
    	finalChecksumString += safe(checksumKey);
    	
    	return safe(finalChecksumString);
    }
}
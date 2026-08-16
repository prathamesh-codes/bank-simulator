package com.billdesk.pg.payments.simulator.bank.hre;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.zip.CRC32;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Faithful port of the real HRE checksum machinery — {@code BankUtils.doDigest} and
 * {@code HREHelper.calculateChecksum} in
 * banks/src/main/java/com/billdesk/pg/payments/bank/{helpers/BankUtils,hre/HREHelper}.java —
 * plus the two checksum-string constructions from {@code HREService} (init leg: :111-154,
 * response leg: :284-301). Kept a direct line-for-line port (not "cleaned up") so it stays
 * verifiably identical to what real HRE actually validates against.
 */
final class HreChecksumUtil {

  private HreChecksumUtil() {

  }

  /**
   * {@code testKey} plays the role of the real, HSM-protected {@code MEBankConfig.bankSecurityPwd}
   * once decrypted — i.e. pass the plaintext key material directly (design doc §3.1/§7, open
   * question #8). The real key format embeds its algorithm selector after the last {@code |} in
   * this string (see {@link #doDigest}), e.g. {@code "somematerial|SHA256-realsecret"}.
   */
  static String calculateChecksum(String checksumString, String testKey) {

    return doDigest(checksumString + testKey);
  }

  /** Direct port of {@code BankUtils.doDigest} — algorithm is selected by a prefix after the last '|'. */
  static String doDigest(String strMsg) {

    int checksuminx = strMsg.lastIndexOf("|");
    String checksumkey = strMsg.substring(checksuminx + 1);
    if (checksumkey.startsWith("SHA256-")) {
      checksumkey = checksumkey.substring(7);
      String newMsg = strMsg.substring(0, checksuminx) + "|" + checksumkey;
      return checkSumSHA256(newMsg);
    }
    if (checksumkey.startsWith("HMAC-")) {
      checksumkey = checksumkey.substring(5);
      String newMsg = strMsg.substring(0, checksuminx);
      return hmacSHA256(newMsg, checksumkey);
    }
    if (checksumkey.startsWith("DS-")) {
      return "NA";
    }
    CRC32 crcEncode = new CRC32();
    crcEncode.update(strMsg.getBytes(StandardCharsets.UTF_8));
    return String.valueOf(crcEncode.getValue());
  }

  private static String checkSumSHA256(String plaintext) {

    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(plaintext.getBytes(StandardCharsets.UTF_8));
      return toHex(md.digest());
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static String hmacSHA256(String message, String secret) {

    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return toHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HmacSHA256 not available", e);
    }
  }

  private static String toHex(byte[] raw) {

    char[] arr = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    StringBuilder sb = new StringBuilder(raw.length * 2);
    for (byte b : raw) {
      sb.append(arr[(b & 0xF0) >> 4]).append(arr[b & 0x0F]);
    }
    return sb.toString();
  }

  private static String nz(String v) {

    return v == null ? "" : v;
  }

  /** Mirrors HREService.generateRequest's checksumString construction, :111-154. */
  static String buildInitChecksumString(Map<String, String> fields) {

    String checksumString = nz(fields.get("ClientCode"))
                            + nz(fields.get("MerchantCode"))
                            + nz(fields.get("TxnCurrency"))
                            + nz(fields.get("TxnAmount"))
                            + nz(fields.get("TxnScAmount"))
                            + nz(fields.get("MerchantRefNo"))
                            + "NN"
                            + nz(fields.get("Date"));
    if (fields.containsKey("DisplayDetails")) {
      checksumString = checksumString
                       + nz(fields.get("Ref1"))
                       + nz(fields.get("Ref2"))
                       + nz(fields.get("Ref3"))
                       + nz(fields.get("Ref4"))
                       + nz(fields.get("Ref5"))
                       + nz(fields.get("Ref6"))
                       + "N";
    }
    if (fields.get("ClientAccNum") != null && !"NA".equals(fields.get("ClientAccNum"))) {
      checksumString = checksumString + fields.get("ClientAccNum");
    }
    checksumString = checksumString + nz(fields.get("DynamicUrl"));
    return checksumString;
  }

  /** Mirrors HREService.processPayment's checksumString construction, :284-301. */
  static String buildResponseChecksumString(Map<String, String> fields) {

    return nz(fields.get("ClientCode"))
          + nz(fields.get("MerchantCode"))
          + nz(fields.get("TxnCurrency"))
          + nz(fields.get("TxnAmount"))
          + nz(fields.get("TxnScAmount"))
          + nz(fields.get("MerchRefNo"))
          + "NN"
          + nz(fields.get("Date"))
          + nz(fields.get("Ref1"))
          + nz(fields.get("Ref2"))
          + nz(fields.get("Ref3"))
          + nz(fields.get("Ref4"))
          + nz(fields.get("Ref5"))
          + nz(fields.get("Ref6"))
          + nz(fields.get("Date1"))
          + nz(fields.get("Date2"))
          + nz(fields.get("BankRefNo"))
          + nz(fields.get("Message"));
  }
}

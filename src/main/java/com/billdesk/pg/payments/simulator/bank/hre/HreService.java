package com.billdesk.pg.payments.simulator.bank.hre;

import com.billdesk.pg.payments.simulator.core.NetbankingBankSimulator;
import com.billdesk.pg.payments.simulator.dto.CallbackDelivery;
import com.billdesk.pg.payments.simulator.dto.ParsedInit;
import com.billdesk.pg.payments.simulator.dto.SimulatedCase;
import com.billdesk.pg.payments.simulator.dto.ValidationResult;
import com.billdesk.pg.payments.simulator.dto.VerificationWireResponse;
import com.billdesk.pg.payments.simulator.model.SimulatorRecord;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The HRE (HDFC) bank-specific service — the single place all HRE wire-format decisions live
 * (required fields, checksum scheme, S2S delivery shape). Resolved by bank id via
 * {@link com.billdesk.pg.payments.simulator.factory.SimulatorFactory} for every leg
 * (init/submit/verify); nothing outside this class needs to know HRE's field shapes. Field shapes
 * and validation rules are a direct port of
 * banks/src/main/java/com/billdesk/pg/payments/bank/hre/HREService.java (see
 * banks/docs/netbanking/HRE.md for the reverse-engineered write-up this was built from, and
 * banks/docs/netbanking/simulator-jar-design.md §7 for the spec this implements).
 */
@Component
public class HreService implements NetbankingBankSimulator {

  private static final Logger logger = LogManager.getLogger(HreService.class);
  private static final DateTimeFormatter DATE_FORMAT =
                                                       DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
  private static final List<String> REQUIRED_INIT_FIELDS =
                                                          List.of("TxnCurrency",
                                                                 "ClientCode",
                                                                 "MerchantCode",
                                                                 "TxnAmount",
                                                                 "TxnScAmount",
                                                                 "MerchantRefNo",
                                                                 "SuccessStaticFlag",
                                                                 "FailureStaticFlag",
                                                                 "Date",
                                                                 "DynamicUrl",
                                                                 "CheckSum");
  private static final List<String> REQUIRED_VERIFY_FIELDS =
                                                            List.of("MerchantCode",
                                                                   "Date",
                                                                   "MerchantRefNo",
                                                                   "ClientCode",
                                                                   "SuccessStaticFlag",
                                                                   "FailureStaticFlag",
                                                                   "TxnAmount",
                                                                   "FlgVerify",
                                                                   "TransactionId");

  /** Curated from the real bank-message -> PGI error code table, HREService.java:849-919. */
  public static final List<String> KNOWN_FAILURE_REASONS =
                                                          List.of("Hey! We regret to inform you that you do not have a sufficient balance in your account to complete this transaction.",
                                                                 "You have entered incorrect One-Time Password [OTP]. Please try again.",
                                                                 "OTP [One Time Password] entered by you for HDFC Bank transaction has expired. Please re-initiate the transaction.",
                                                                 "Sorry, this is a duplicate transaction. A transaction with the same details has already been processed.",
                                                                 "You have exceeded your third party funds transfer limit for the day.You cannot transfer any more funds.",
                                                                 "Your account has not been activated for netbanking. Please contact the bank.",
                                                                 "Funds transfer terminated by user.");

  @Value("${simulator.hre.checksum-key:}")
  private String checksumKey;

  @Override
  public String bankId() {

    return "HRE";
  }

  @Override
  public ValidationResult validateInit(Map<String, String> raw) {

    for (String field : REQUIRED_INIT_FIELDS) {
      String value = raw.get(field);
      if (value == null || value.isBlank()) {
        return ValidationResult.fail(field, "required field missing or blank");
      }
    }
    if ( !raw.get("ClientCode").equals(raw.get("MerchantRefNo"))) {
      return ValidationResult.fail("ClientCode/MerchantRefNo",
                                   "ClientCode must equal MerchantRefNo (HRE sends the same PG transaction id under both names)");
    }
    try {
      double amount = Double.parseDouble(raw.get("TxnAmount"));
      if (amount < 0) {
        return ValidationResult.fail("TxnAmount", "must be non-negative");
      }
    } catch (NumberFormatException e) {
      return ValidationResult.fail("TxnAmount", "not a valid decimal amount");
    }
    try {
      DATE_FORMAT.parse(raw.get("Date"));
    } catch (Exception e) {
      return ValidationResult.fail("Date", "does not match dd/MM/yyyy HH:mm:ss");
    }
    try {
      java.net.URI.create(raw.get("DynamicUrl")).toURL();
    } catch (Exception e) {
      return ValidationResult.fail("DynamicUrl", "not a well-formed URL");
    }
    if (checksumKey != null && !checksumKey.isBlank()) {
      String expected = HreChecksumUtil.calculateChecksum(HreChecksumUtil.buildInitChecksumString(raw),
                                                          checksumKey);
      if ( !expected.equalsIgnoreCase(raw.get("CheckSum"))) {
        logger.warn("HRE init CheckSum mismatch for txn={} expected={} received={}",
                    raw.get("ClientCode"),
                    expected,
                    raw.get("CheckSum"));
        return ValidationResult.fail("CheckSum", "recomputed checksum does not match");
      }
    } else {
      logger.info("HRE init CheckSum presence-only check (no simulator.hre.checksum-key configured) for txn={}",
                 raw.get("ClientCode"));
    }
    return ValidationResult.ok();
  }

  @Override
  public ParsedInit parseInit(Map<String, String> raw) {

    return new ParsedInit(raw.get("ClientCode"),
                          raw.get("MerchantCode"),
                          raw.get("TxnAmount"),
                          raw.get("TxnCurrency"),
                          raw.get("DynamicUrl"));
  }

  @Override
  public CallbackDelivery buildCallbackResponse(SimulatorRecord record, SimulatedCase chosenCase) {

    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("ClientCode", record.getTxnId());
    fields.put("MerchantCode", record.getMerchantCode());
    fields.put("TxnCurrency", record.getTxnCurrency());
    fields.put("TxnAmount", record.getTxnAmount());
    fields.put("TxnScAmount", "0.00");
    fields.put("MerchRefNo", record.getTxnId());
    fields.put("Date", LocalDateTime.now().format(DATE_FORMAT));
    fields.put("BankRefNo", record.getBankRef());
    // Deliberately always present, even when empty: HREService.processPayment treats a MISSING
    // Message field as a non-empty reason ("".equals(null) is false) and would wrongly fail an
    // otherwise-successful transaction — see HreSimulator class javadoc / design doc §7.
    fields.put("Message", chosenCase.isSuccess() ? "" : effectiveFailureReason(chosenCase));
    String checksum = checksumKey != null && !checksumKey.isBlank()
      ? HreChecksumUtil.calculateChecksum(HreChecksumUtil.buildResponseChecksumString(fields),
                                          checksumKey)
      : placeholderChecksum(record.getTxnId());
    fields.put("CheckSum", checksum);
    return new CallbackDelivery(record.getReturnUrl(), fields);
  }

  @Override
  public ValidationResult validateVerification(Map<String, String> raw, SimulatorRecord record) {

    for (String field : REQUIRED_VERIFY_FIELDS) {
      String value = raw.get(field);
      if (value == null || value.isBlank()) {
        return ValidationResult.fail(field, "required field missing or blank on verification call");
      }
    }
    if ( !"Y".equals(raw.get("FlgVerify"))) {
      return ValidationResult.fail("FlgVerify", "expected literal 'Y'");
    }
    if ( !"XTXTV01".equals(raw.get("TransactionId"))) {
      return ValidationResult.fail("TransactionId", "expected literal verification marker 'XTXTV01'");
    }
    if ( !"N".equals(raw.get("SuccessStaticFlag")) || !"N".equals(raw.get("FailureStaticFlag"))) {
      return ValidationResult.fail("SuccessStaticFlag/FailureStaticFlag", "expected literal 'N'/'N'");
    }
    if ( !raw.get("MerchantCode").equals(record.getMerchantCode())) {
      return ValidationResult.fail("MerchantCode", "does not match value captured at init");
    }
    if ( !raw.get("MerchantRefNo").equals(record.getTxnId())
        || !raw.get("ClientCode").equals(record.getTxnId())) {
      return ValidationResult.fail("MerchantRefNo/ClientCode", "does not match transaction id captured at init");
    }
    if ( !raw.get("TxnAmount").equals(record.getTxnAmount())) {
      return ValidationResult.fail("TxnAmount", "does not match amount captured at init");
    }
    return ValidationResult.ok();
  }

  @Override
  public VerificationWireResponse buildVerificationResponse(SimulatorRecord record,
                                                             SimulatedCase chosenCase) {

    String body = "flgSuccess=" + (chosenCase.isSuccess() ? "S" : "F")
                 + "&flgVerify=Y"
                 + "&BankRefNo=" + record.getBankRef()
                 + "&TxnAmount=" + record.getTxnAmount()
                 + "&ClientCode=" + record.getTxnId()
                 + "&MerchantRefNo=" + record.getTxnId();
    return new VerificationWireResponse("text/plain", body);
  }

  @Override
  public VerificationWireResponse buildMismatchVerificationResponse(SimulatorRecord record,
                                                                     ValidationResult failure) {

    logger.warn("HRE verification-call mismatch for txn={} field={} reason={}",
               record.getTxnId(),
               failure.getField(),
               failure.getMessage());
    return new VerificationWireResponse("text/plain",
                                       "",
                                       failure.getField() + ": " + failure.getMessage());
  }

  @Override
  public String extractVerificationTxnId(Map<String, String> rawParams) {

    return rawParams.get("MerchantRefNo");
  }

  @Override
  public List<String> knownFailureReasons() {

    return KNOWN_FAILURE_REASONS;
  }

  private String effectiveFailureReason(SimulatedCase chosenCase) {

    String reason = chosenCase.getFailureReason();
    return (reason == null || reason.isBlank()) ? KNOWN_FAILURE_REASONS.get(0) : reason;
  }

  private String placeholderChecksum(String txnId) {

    return HreChecksumUtil.doDigest(txnId + "|" + System.nanoTime());
  }
}

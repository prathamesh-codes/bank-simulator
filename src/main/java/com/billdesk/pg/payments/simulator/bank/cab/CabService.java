package com.billdesk.pg.payments.simulator.bank.cab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.billdesk.pg.payments.simulator.core.NetbankingBankSimulator;
import com.billdesk.pg.payments.simulator.dto.CallbackDelivery;
import com.billdesk.pg.payments.simulator.dto.ParsedInit;
import com.billdesk.pg.payments.simulator.dto.SimulatedCase;
import com.billdesk.pg.payments.simulator.dto.ValidationResult;
import com.billdesk.pg.payments.simulator.dto.VerificationWireResponse;
import com.billdesk.pg.payments.simulator.model.SimulatorRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CabService implements NetbankingBankSimulator{
	
	private final ObjectMapper objectMapper;

	public CabService(
	        ObjectMapper objectMapper) {

	    this.objectMapper =
	            objectMapper;
	}
	
	private Map<String, String> getBankMetadata(
	        SimulatorRecord record) {

	    if (record.getBankMetadata() == null ||
	            record.getBankMetadata().isBlank()) {

	        throw new IllegalStateException(
	                "CAB bank metadata missing for txn="
	                        + record.getTxnId()
	        );
	    }

	    try {

	        return objectMapper.readValue(
	                record.getBankMetadata(),
	                new TypeReference<Map<String, String>>() {}
	        );

	    } catch (Exception e) {

	        throw new IllegalStateException(
	                "Could not read CAB bank metadata",
	                e
	        );
	    }
	}

	private static final Logger logger = LogManager.getLogger(CabService.class);
	
	private static final List<String> REQUIRED_INIT_FIELDS =
	        List.of(
	                "mode",
	                "payee_id",
	                "biller_name",
	                "payment_ref_no",
	                "amount",
	                "currency",
	                "return_url",
	                "checkval"
	        );

	private static final List<String> REQUIRED_VERIFY_FIELDS =
	        List.of(
	                "mode",
	                "payment_ref_no",
	                "payee_id",
	                "biller_name",
	                "bank_ref_no",
	                "amount",
	                "currency",
	                "checkval"
	        );

	private static final List<String> KNOWN_FAILURE_REASONS =
	        List.of(
	                "Insufficient funds",
	                "Checker Rejected",
	                "Transaction cancelled",
	                "Transaction timeout",
	                "Payment failed"
	        );

	@Value("${simulator.cab.confirmation-url:}")
	String confirmationUrl;
	
	@Value("${simulator.cab.checksum-key:}")
	String checksumKey;

	@Value("${simulator.cab.encryption-key}")
	String encryptionKey;

	@Value("${simulator.cab.iv}")
	String iv;

	@Override
	public String bankId() {
		// TODO Auto-generated method stub
		return "CAB";
	}

	@Override
	public ValidationResult validateInit(
	        Map<String, String> raw) {

	    /*
	     * 1. Required fields.
	     */
	    for (String field : REQUIRED_INIT_FIELDS) {

	        String value =
	                raw.get(field);

	        if (value == null ||
	                value.isBlank()) {

	            return ValidationResult.fail(
	                    field,
	                    "required field missing or blank"
	            );
	        }
	    }

	    /*
	     * 2. CAB payment-init mode must be P.
	     */
	    if (!"P".equalsIgnoreCase(
	            raw.get("mode"))) {

	        return ValidationResult.fail(
	                "mode",
	                "expected literal 'P'"
	        );
	    }

	    /*
	     * 3. PID outside encrypted data must match
	     * payee_id inside encrypted data.
	     */
	    String pid =
	            raw.get("_PID");

	    if (pid == null ||
	            pid.isBlank()) {

	        return ValidationResult.fail(
	                "PID",
	                "PID query/form parameter is required"
	        );
	    }

	    if (!pid.equals(
	            raw.get("payee_id"))) {

	        return ValidationResult.fail(
	                "PID/payee_id",
	                "PID does not match payee_id inside encrypted payload"
	        );
	    }

	    /*
	     * 4. Validate amount.
	     */
	    try {

	        double amount =
	                Double.parseDouble(
	                        raw.get("amount")
	                );

	        if (amount <= 0) {
	            return ValidationResult.fail(
	                    "amount",
	                    "must be greater than zero"
	            );
	        }

	    } catch (NumberFormatException e) {

	        return ValidationResult.fail(
	                "amount",
	                "not a valid decimal amount"
	        );
	    }

	    /*
	     * 5. Validate return URL.
	     */
	    try {

	        java.net.URI.create(
	                raw.get("return_url")
	        ).toURL();

	    } catch (Exception e) {

	        return ValidationResult.fail(
	                "return_url",
	                "not a well-formed URL"
	        );
	    }

	    /*
	     * 6. Recompute CAB checksum.
	     */
	    if (checksumKey != null &&
	            !checksumKey.isBlank()) {

	        String checksumInput =
	                CabChecksumUtil
	                        .buildInitChecksumString(
	                                raw,
	                                checksumKey
	                        );

	        String expected =
	                CabChecksumUtil
	                        .computeChecksum(
	                                checksumInput
	                        );

	        String received =
	                raw.get("checkval");

	        if (!expected.equalsIgnoreCase(
	                received)) {

	            logger.warn(
	                    "CAB init checksum mismatch txn={} expected={} received={}",
	                    raw.get("payment_ref_no"),
	                    expected,
	                    received
	            );

	            return ValidationResult.fail(
	                    "checkval",
	                    "recomputed checksum does not match"
	            );
	        }
	    }
	    
	    if (!"online".equalsIgnoreCase(raw.get("mode"))) {
	        return ValidationResult.fail(
	                "mode",
	                "expected literal 'online'"
	        );
	    }

	    return ValidationResult.ok();
	}

	@Override
	public ParsedInit parseInit(
	        Map<String, String> raw) {

	    return new ParsedInit(
	            raw.get("payment_ref_no"),
	            raw.get("payee_id"),
	            raw.get("amount"),
	            raw.get("currency"),
	            raw.get("return_url")
	    );
	}

	@Override
	public CallbackDelivery buildCallbackResponse(
	        SimulatorRecord record,
	        SimulatedCase chosenCase) {

	    Map<String, String> init =
	            getBankMetadata(record);

	    Map<String, String> fields =
	            new LinkedHashMap<>();

	    fields.put(
	            "status",
	            chosenCase.isSuccess()
	                    ? "Y"
	                    : "N"
	    );

	    fields.put(
	            "payment_ref_no",
	            record.getTxnId()
	    );

	    fields.put(
	            "biller_name",
	            init.get("biller_name")
	    );

	    fields.put(
	            "bank_ref_no",
	            record.getBankRef()
	    );

	    fields.put(
	            "amount",
	            record.getTxnAmount()
	    );

	    fields.put(
	            "account_no",
	            safe(init.get("account_no"))
	    );

	    fields.put(
	            "error_msg",
	            chosenCase.isSuccess()
	                    ? "Transaction successful"
	                    : effectiveFailureReason(
	                            chosenCase
	                    )
	    );

	    String checksumInput =
	            CabChecksumUtil
	                    .buildCallbackChecksumString(
	                            fields,
	                            checksumKey
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

	    String plainResponse =
	            buildPipePayload(fields);

	    String encrypted =
	            CabEncryptionUtil.encrypt(
	                    plainResponse,
	                    encryptionKey,
	                    iv
	            );

	    Map<String, String> callbackFields =
	            new LinkedHashMap<>();

	    callbackFields.put(
	            "data",
	            encrypted
	    );

	    return new CallbackDelivery(
	            confirmationUrl,
	            Map.of(),
	            Map.of(
	                    "data",
	                    encrypted
	            ),
	            HttpMethod.POST
	    );
	}

	@Override
	public ValidationResult validateVerification(
	        Map<String, String> raw,
	        SimulatorRecord record) {

	    for (String field :
	            REQUIRED_VERIFY_FIELDS) {

	        String value =
	                raw.get(field);

	        if (value == null ||
	                value.isBlank()) {

	            return ValidationResult.fail(
	                    field,
	                    "required field missing or blank on verification call"
	            );
	        }
	    }

	    Map<String, String> init =
	            getBankMetadata(record);

	    /*
	     * Check transaction ID.
	     */
	    if (!record.getTxnId().equals(
	            raw.get("payment_ref_no"))) {

	        return ValidationResult.fail(
	                "payment_ref_no",
	                "does not match transaction captured at init"
	        );
	    }

	    /*
	     * Check payee ID.
	     */
	    if (!init.get("payee_id").equals(
	            raw.get("payee_id"))) {

	        return ValidationResult.fail(
	                "payee_id",
	                "does not match value captured at init"
	        );
	    }

	    /*
	     * Check biller.
	     */
	    if (!init.get("biller_name").equals(
	            raw.get("biller_name"))) {

	        return ValidationResult.fail(
	                "biller_name",
	                "does not match value captured at init"
	        );
	    }

	    /*
	     * Check bank reference.
	     */
	    if (!record.getBankRef().equals(
	            raw.get("bank_ref_no"))) {

	        return ValidationResult.fail(
	                "bank_ref_no",
	                "does not match generated bank reference"
	        );
	    }

	    /*
	     * Check amount.
	     */
	    if (!record.getTxnAmount().equals(
	            raw.get("amount"))) {

	        return ValidationResult.fail(
	                "amount",
	                "does not match value captured at init"
	        );
	    }

	    /*
	     * Check currency.
	     */
	    if (!record.getTxnCurrency().equalsIgnoreCase(
	            raw.get("currency"))) {

	        return ValidationResult.fail(
	                "currency",
	                "does not match value captured at init"
	        );
	    }

	    /*
	     * Validate verification checksum.
	     */
	    String checksumInput =
	            CabChecksumUtil
	                    .buildVerificationRequestChecksumString(
	                            raw,
	                            checksumKey
	                    );

	    String expected =
	            CabChecksumUtil
	                    .computeChecksum(
	                            checksumInput
	                    );

	    if (!expected.equalsIgnoreCase(
	            raw.get("checkval"))) {

	        logger.warn(
	                "CAB verification checksum mismatch txn={} expected={} received={}",
	                record.getTxnId(),
	                expected,
	                raw.get("checkval")
	        );

	        return ValidationResult.fail(
	                "checkval",
	                "recomputed verification checksum does not match"
	        );
	    }

	    return ValidationResult.ok();
	}

	@Override
	public VerificationWireResponse buildVerificationResponse(
	        SimulatorRecord record,
	        SimulatedCase chosenCase) {

	    Map<String, String> fields =
	            new LinkedHashMap<>();

	    fields.put(
	            "status",
	            chosenCase.isSuccess()
	                    ? "Y"
	                    : "N"
	    );

	    fields.put(
	            "payment_ref_no",
	            record.getTxnId()
	    );

	    fields.put(
	            "bank_ref_no",
	            record.getBankRef()
	    );

	    fields.put(
	            "amount",
	            record.getTxnAmount()
	    );

	    fields.put(
	            "error_msg",
	            chosenCase.isSuccess()
	                    ? "TransactionSuccessful"
	                    : effectiveFailureReason(
	                            chosenCase
	                    )
	    );

	    String checksumInput =
	            CabChecksumUtil
	                    .buildVerificationResponseChecksumString(
	                            fields,
	                            checksumKey
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

	    String plainPayload =
	            buildPipePayload(fields);

	    String encryptedData =
	            CabEncryptionUtil.encrypt(
	                    plainPayload,
	                    encryptionKey,
	                    iv
	            );

	    String body =
	            "data=" + encryptedData;

	    return new VerificationWireResponse(
	            "application/x-www-form-urlencoded",
	            body
	    );
	}

	@Override
	public VerificationWireResponse
	        buildMismatchVerificationResponse(
	                SimulatorRecord record,
	                ValidationResult failure) {

	    logger.warn(
	            "CAB verification mismatch txn={} field={} reason={}",
	            record.getTxnId(),
	            failure.getField(),
	            failure.getMessage()
	    );

	    Map<String, String> fields =
	            new LinkedHashMap<>();

	    fields.put(
	            "status",
	            "N"
	    );

	    fields.put(
	            "payment_ref_no",
	            record.getTxnId()
	    );

	    fields.put(
	            "bank_ref_no",
	            safe(record.getBankRef())
	    );

	    fields.put(
	            "amount",
	            record.getTxnAmount()
	    );

	    fields.put(
	            "error_msg",
	            "Verification mismatch"
	    );

	    String checksumInput =
	            CabChecksumUtil
	                    .buildVerificationResponseChecksumString(
	                            fields,
	                            checksumKey
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

	    String plainPayload =
	            buildPipePayload(fields);

	    String encryptedData =
	            CabEncryptionUtil.encrypt(
	                    plainPayload,
	                    encryptionKey,
	                    iv
	            );

	    return new VerificationWireResponse(
	            "application/x-www-form-urlencoded",
	            "data=" + encryptedData,
	            failure.getField()
	                    + ": "
	                    + failure.getMessage()
	    );
	}

	@Override
	public String extractVerificationTxnId(
	        Map<String, String> rawParams) {

	    return rawParams.get(
	            "payment_ref_no"
	    );
	}
	
//	custom helpers
	
	@Override
	public Map<String, String> preprocessInit(
	        Map<String, String> rawFields) {

	    String encryptedData =
	            rawFields.get("data");

	    if (encryptedData == null ||
	            encryptedData.isBlank()) {

	        throw new IllegalArgumentException(
	                "CAB init request is missing data"
	        );
	    }

	    String decrypted =
	            CabEncryptionUtil.decrypt(
	                    encryptedData,
	                    encryptionKey,
	                    iv
	            );

	    logger.info(
	            "CAB init payload decrypted successfully"
	    );

	    Map<String, String> decoded =
	            parsePipeSeparatedPayload(decrypted);

	    /*
	     * PID is outside the encrypted payload.
	     * Preserve it so validateInit() can verify
	     * PID == payee_id.
	     */
	    decoded.put(
	            "_PID",
	            rawFields.get("PID")
	    );

	    return decoded;
	}
	
	private Map<String, String> parsePipeSeparatedPayload(
	        String payload) {

	    Map<String, String> fields =
	            new LinkedHashMap<>();

	    if (payload == null ||
	            payload.isBlank()) {
	        return fields;
	    }

	    String[] parts =
	            payload.split("\\|");

	    for (String part : parts) {

	        int separator =
	                part.indexOf('=');

	        if (separator <= 0) {
	            continue;
	        }

	        String key =
	                part.substring(0, separator).trim();

	        String value =
	                part.substring(separator + 1).trim();

	        fields.put(key, value);
	    }

	    return fields;
	}
	
	private String buildPipePayload(
	        Map<String, String> fields) {

	    StringBuilder builder =
	            new StringBuilder();

	    for (Map.Entry<String, String> entry :
	            fields.entrySet()) {

	        if (builder.length() > 0) {
	            builder.append("|");
	        }

	        builder.append(
	                entry.getKey()
	        );

	        builder.append("=");

	        builder.append(
	                safe(entry.getValue())
	        );
	    }

	    return builder.toString();
	}
	
	private String safe(String value) {
	    return value == null ? "" : value;
	}

		
	@Override
	public Map<String, String> preprocessVerification(Map<String, String> rawParams) {

	    String encryptedData =
	            rawParams.get("data");

	    if (encryptedData == null ||
	            encryptedData.isBlank()) {

	        throw new IllegalArgumentException(
	                "CAB verification request missing data"
	        );
	    }

	    String decrypted =
	            CabEncryptionUtil.decrypt(
	                    encryptedData,
	                    encryptionKey,
	                    iv
	            );

	    return parsePipeSeparatedPayload(
	            decrypted
	    );
	}
	
	@Override
	public List<String> knownFailureReasons() {

	    return KNOWN_FAILURE_REASONS;
	}
	
	private String effectiveFailureReason(
	        SimulatedCase chosenCase) {

	    String reason =
	            chosenCase.getFailureReason();

	    return reason == null ||
	            reason.isBlank()
	            ? "Payment failed"
	            : reason;
	}

}

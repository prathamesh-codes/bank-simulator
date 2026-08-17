package com.billdesk.pg.payments.simulator.core;

import com.billdesk.pg.payments.simulator.dto.CallbackDelivery;
import com.billdesk.pg.payments.simulator.dto.ParsedInit;
import com.billdesk.pg.payments.simulator.dto.SimulatedCase;
import com.billdesk.pg.payments.simulator.dto.ValidationResult;
import com.billdesk.pg.payments.simulator.dto.VerificationWireResponse;
import com.billdesk.pg.payments.simulator.model.SimulatorRecord;
import java.util.List;
import java.util.Map;

/**
 * One implementation per bank, mirroring the shape the real {@code banks} service uses
 * ({@code BankingService}/{@code BankDetailsBankingService}/{@code CheckerBankingService}) —
 * see banks/docs/netbanking/simulator-jar-design.md §2. Everything bank-specific (wire format,
 * required fields, checksum scheme) lives inside the implementation; the controllers, the DB
 * table, and the case semantics stay entirely generic.
 */
public interface NetbankingBankSimulator {

  /** The bank_master.BANK_ID this plugin plays, e.g. "HRE". */
  String bankId();

  /**
   * Runs once, before {@link #validateInit} and {@link #parseInit}, on whatever PG's
   * TXN_INIT_URL call actually contained — a bank whose fields arrive encrypted or otherwise
   * encoded (e.g. a single opaque {@code data} query param) overrides this to decrypt/decode
   * once into the flat field map the rest of this interface works with, instead of repeating
   * that decoding inside both {@link #validateInit} and {@link #parseInit}. Default is a no-op
   * for banks (like HRE) whose fields already arrive as plain, individually-named form fields.
   */
  default Map<String, String> preprocessInit(Map<String, String> rawFields) {

    return rawFields;
  }

  /**
   * Validate PG's TXN_INIT_URL POST BEFORE anything is persisted or shown to a tester — required
   * fields present, cross-field sanity, and (where a test signing key is configured) a real
   * checksum recomputation. Design doc §3.1. A failure here means no simulator page is ever
   * rendered and no {@link SimulatorRecord} row is created. Receives the output of
   * {@link #preprocessInit}, not the raw wire payload.
   */
  ValidationResult validateInit(Map<String, String> rawFormFields);

  /**
   * Parse this bank's (already {@link #preprocessInit}-ed) init fields into a bank-agnostic
   * record. Only called after {@link #validateInit} passes.
   */
  ParsedInit parseInit(Map<String, String> rawFormFields);

  /**
   * Build this bank's outbound "browser is being sent back to you" payload for the tester's
   * chosen case — wire format is bank-specific.
   */
  CallbackDelivery buildCallbackResponse(SimulatorRecord record, SimulatedCase chosenCase);

  /**
   * Validate PG's double-verification call (bank_master.QRY_INIT_URL) BEFORE answering — required
   * fields present, literal marker values correct, and every echoed field still matches what was
   * captured on the init leg. Design doc §3.1.
   */
  ValidationResult validateVerification(Map<String, String> rawParams, SimulatorRecord record);

  /**
   * Build this bank's response to the double-verification call. Only called after
   * {@link #validateVerification} passes; a failed validation instead goes through
   * {@link #buildMismatchVerificationResponse}.
   */
  VerificationWireResponse buildVerificationResponse(SimulatorRecord record,
                                                      SimulatedCase chosenCase);

  /**
   * What to answer the verification call with when {@link #validateVerification} fails — a
   * genuine mismatch shape (not a fabricated match), so PG's real degrade-to-PENDING path gets
   * exercised. Design doc §3.1.
   */
  VerificationWireResponse buildMismatchVerificationResponse(SimulatorRecord record,
                                                              ValidationResult failure);

  /**
   * Pull the transaction id back out of a verification-call request, using whatever field name
   * that bank echoes it under (e.g. {@code MerchantRefNo} for HRE) — lets the controller look up
   * the {@link SimulatorRecord} before any bank-specific validation runs.
   */
  String extractVerificationTxnId(Map<String, String> rawParams);

  /** Optional, bank-specific list of realistic failure messages to offer on the simulator page. */
  default List<String> knownFailureReasons() {

    return List.of();
  }
  
  default Map<String, String> preprocessVerification(
	        Map<String, String> rawParams) {

	    return rawParams;
	}
}

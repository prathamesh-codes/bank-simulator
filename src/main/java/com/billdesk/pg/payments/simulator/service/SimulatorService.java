package com.billdesk.pg.payments.simulator.service;

import java.net.URI;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.billdesk.pg.payments.simulator.core.NetbankingBankSimulator;
import com.billdesk.pg.payments.simulator.dto.CallbackDelivery;
import com.billdesk.pg.payments.simulator.dto.InitOutcome;
import com.billdesk.pg.payments.simulator.dto.ParsedInit;
import com.billdesk.pg.payments.simulator.dto.SimulatedCase;
import com.billdesk.pg.payments.simulator.dto.SubmitOutcome;
import com.billdesk.pg.payments.simulator.dto.ValidationResult;
import com.billdesk.pg.payments.simulator.dto.VerificationWireResponse;
import com.billdesk.pg.payments.simulator.enums.DeliveryMode;
import com.billdesk.pg.payments.simulator.enums.ResultOutcome;
import com.billdesk.pg.payments.simulator.enums.SimulatorStatus;
import com.billdesk.pg.payments.simulator.factory.SimulatorFactory;
import com.billdesk.pg.payments.simulator.model.SimulatorRecord;
import com.billdesk.pg.payments.simulator.repository.SimulatorRecordStore;
import com.billdesk.pg.payments.simulator.util.UrlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The single, bank-agnostic orchestration layer behind every endpoint in
 * {@code controller.SimulatorController} — record persistence, delivery mechanics (redirect URL
 * building, the S2S calls to PG, delay/drop handling), and verification-call handling all live
 * here, not in the controller. Every method works purely off the {@link NetbankingBankSimulator}
 * contract, so adding a new bank never means touching this class — only adding a new
 * {@code @Component} under {@code bank/<id>/}. See design doc §2/§8.
 */
@Service
public class SimulatorService {

  private static final Logger logger = LogManager.getLogger(SimulatorService.class);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SimulatorFactory factory;
  private final SimulatorRecordStore store;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final ScheduledExecutorService scheduler;

  @Value("${simulator.delay.default-seconds:5}")
  private int defaultDelaySeconds;
  @Value("${simulator.delay.max-seconds:120}")
  private int maxDelaySeconds;
  @Value("${simulator.duplicate.second-call-delay-ms:1500}")
  private long duplicateCallDelayMs;

  public SimulatorService(SimulatorFactory factory,
                          SimulatorRecordStore store,
                          ObjectMapper objectMapper,
                          RestTemplate restTemplate,
                          ScheduledExecutorService scheduler) {

    this.factory = factory;
    this.store = store;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
    this.scheduler = scheduler;
  }

  /** Default seconds for the DELAY delivery mode — exposed so the page can pre-fill the field. */
  public int getDefaultDelaySeconds() {

    return defaultDelaySeconds;
  }

  /** Looks up the bank plugin for {@code bankId}, or a 404 if no such bank is registered. */
  public NetbankingBankSimulator resolveBank(String bankId) {

    try {
    return factory.get(bankId);
    } catch (NoSuchElementException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  /** Looks up a previously-initiated transaction, or a 404 if none exists for that bank+txn id. */
  public SimulatorRecord getRecordOrThrow(String bankId, String txnId) {

    return store.find(bankId, txnId)
               .orElseThrow(() -> {
                 logger.warn("No simulator record for bankId={} txnId={}", bankId, txnId);
                 return new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                    "No simulator record for bankId="
                                                    + bankId
                                                    + " txnId="
                                                    + txnId);
               });
  }

  /** Bank-specific dropdown options for the simulator page's failure-reason field. */
  public List<String> knownFailureReasons(String bankId) {

    return resolveBank(bankId).knownFailureReasons();
  }

  // --- Leg 1: plays TXN_INIT_URL --------------------------------------------------------------

  /**
   * Validates and persists PG's {@code TXN_INIT_URL} call: resolves the bank, runs its
   * {@code preprocessInit}/{@code validateInit}/{@code parseInit}, and upserts the
   * {@link SimulatorRecord} (keyed on bankId+txnId, so a retried init call updates the same row
   * rather than duplicating it). Returns a failure outcome instead of throwing, so the controller
   * can turn a bad request into a 400 with the specific field/reason instead of a generic error.
   */
  public InitOutcome handleInit(String bankId, Map<String, String> rawParams) {

    logger.info("Received TXN_INIT_URL call for bankId={} rawParams={}", bankId, rawParams);
    NetbankingBankSimulator simulator = resolveBank(bankId);
    Map<String, String> initFields = simulator.preprocessInit(rawParams);
    ValidationResult validation = simulator.validateInit(initFields);
    if ( !validation.isValid()) {
      logger.warn("Init validation failed for bankId={} field={} reason={}",
                 bankId,
                 validation.getField(),
                 validation.getMessage());
      return InitOutcome.failure(validation);
    }

    ParsedInit parsed = simulator.parseInit(initFields);
    SimulatorRecord record = store.findOrCreate(bankId, parsed.getTransactionId());
    record.setBankId(bankId);
    record.setTxnId(parsed.getTransactionId());
    record.setMercId(parsed.getMerchantCode());
    record.setMerchantCode(parsed.getMerchantCode());
    record.setTxnAmount(parsed.getAmount());
    record.setTxnCurrency(parsed.getCurrency());
    record.setReturnUrl(parsed.getReturnUrl());
    record.setTxnStatus(SimulatorStatus.INITIATED);
    try {
      record.setRawInitParams(objectMapper.writeValueAsString(rawParams));
    } catch (Exception e) {
      logger.warn("Could not serialize raw init params for bankId={} txnId={}",
                 bankId,
                 parsed.getTransactionId(),
                 e);
    }
    store.save(record);
    logger.info("Simulator record initiated bankId={} txnId={} amount={} currency={} returnUrl={}",
               bankId,
               record.getTxnId(),
               record.getTxnAmount(),
               record.getTxnCurrency(),
               record.getReturnUrl());
    return InitOutcome.success(record);
  }

  // --- Leg 1 continued: tester's submit -> backend initiates the callback ----------------------

  /**
   * Applies the tester's chosen result/delivery to the record, waits out a DELAY if requested,
   * then fires the bank-built S2S callback to PG (twice, with a configured delay between, for
   * DUPLICATE_CALLBACK). {@code errorCode} is in-memory only for this call — it's not persisted
   * on {@link SimulatorRecord}, so it won't be visible again if the verification leg reconstructs
   * a {@link SimulatedCase} later. Returns whatever PG's S2S response says the tab should do next
   * (navigate, or nothing — see {@link SubmitOutcome}).
   */
  public SubmitOutcome handleSubmit(String bankId,
                                    String txnId,
                                    ResultOutcome result,
                                    DeliveryMode delivery,
                                    String failureReason,
                                    String errorCode,
                                    Integer delaySeconds) {

    logger.info("Tester submit for bankId={} txnId={} result={} delivery={} failureReason={} errorCode={} delaySeconds={}",
               bankId,
               txnId,
               result,
               delivery,
               failureReason,
               errorCode,
               delaySeconds);
    NetbankingBankSimulator simulator = resolveBank(bankId);
    SimulatorRecord record = getRecordOrThrow(bankId, txnId);
    record.setSelectedResult(result);
    record.setFailureReason(failureReason);
    record.setBankRef(generateBankRef());
    SimulatedCase chosenCase = new SimulatedCase(result, delivery, failureReason, errorCode);

    if (delivery == DeliveryMode.DELAY) {
      int seconds = delaySeconds == null ? defaultDelaySeconds : delaySeconds;
      seconds = Math.min(Math.max(seconds, 0), maxDelaySeconds);
      record.setDelaySeconds(seconds);
      logger.info("Simulating {}s DELAY for bankId={} txnId={} before sending the callback",
                 seconds,
                 bankId,
                 txnId);
      sleep(seconds);
    }

    record.setTxnStatus(SimulatorStatus.CALLBACK_SENT);
    store.save(record);

    // The tab is never redirected to the bankresponse URL we're calling here — that's PG's S2S
    // endpoint, not something meant for a browser. But PG's *response* to that call carries
    // return_url + transaction_response (its own next hop, e.g. the SDK result page), which is
    // what the tab actually needs to land on to show a real result instead of a blank page.
    CallbackDelivery callbackDelivery = simulator.buildCallbackResponse(record, chosenCase);
    String callbackUrl = UrlUtil.withQuery(callbackDelivery.getTargetUrl(),
                                          callbackDelivery.getQueryParams());
    logger.info("Sending S2S callback to PG for bankId={} txnId={} delivery={} url={}",
               bankId,
               txnId,
               delivery,
               callbackUrl);

    String browserRedirectUrl = fireS2SCallback(callbackUrl, callbackDelivery.getS2sMethod(), bankId, txnId, "primary");

    if (delivery == DeliveryMode.DUPLICATE_CALLBACK) {
      scheduler.schedule(() -> fireS2SCallback(callbackUrl,
                                               callbackDelivery.getS2sMethod(),
                                               bankId,
                                               txnId,
                                               "duplicate"),
                        duplicateCallDelayMs,
                        TimeUnit.MILLISECONDS);
    }

    return new SubmitOutcome(browserRedirectUrl);
  }

  // --- Leg 2: plays QRY_INIT_URL, the double-verification call ---------------------------------

  /**
   * Answers PG's double-verification call. Three distinct outcomes, all logged and all routed
   * through the bank plugin rather than fabricated here: the tester hasn't submitted yet, the
   * echoed fields don't match what was captured at init, or (the happy path) a real answer built
   * from the tester's originally chosen result.
   */
  public VerificationWireResponse handleVerification(String bankId, Map<String, String> rawParams) {

    logger.info("Received QRY_INIT_URL verification call for bankId={} rawParams={}", bankId, rawParams);
    NetbankingBankSimulator simulator = resolveBank(bankId);
    String txnId = simulator.extractVerificationTxnId(rawParams);
    if (txnId == null || txnId.isBlank()) {
      logger.warn("Cannot resolve transaction id from verification request for bankId={}", bankId);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Cannot resolve transaction id from verification request");
    }
    SimulatorRecord record = getRecordOrThrow(bankId, txnId);
    record.incrementVerifyCallCount();
    store.save(record);

    if (record.getSelectedResult() == null) {
      logger.info("Verification call #{} for bankId={} txnId={} arrived before the tester submitted an outcome",
                 record.getVerifyCallCount(),
                 bankId,
                 txnId);
      return logVerificationResponse(bankId,
                                     txnId,
                                     simulator.buildMismatchVerificationResponse(record,
                                                                                ValidationResult.fail("selectedResult",
                                                                                                     "tester has not submitted an outcome on the simulator page yet")));
    }

    ValidationResult validation = simulator.validateVerification(rawParams, record);
    if ( !validation.isValid()) {
      logger.warn("Verification-call validation failed for bankId={} txnId={} field={} reason={}",
                 bankId,
                 txnId,
                 validation.getField(),
                 validation.getMessage());
      return logVerificationResponse(bankId, txnId, simulator.buildMismatchVerificationResponse(record, validation));
    }

    SimulatedCase chosenCase = new SimulatedCase(record.getSelectedResult(),
                                                 null,
                                                 record.getFailureReason());
    VerificationWireResponse response = simulator.buildVerificationResponse(record, chosenCase);
    record.setTxnStatus(SimulatorStatus.VERIFIED);
    store.save(record);
    logger.info("Answered verification call #{} for bankId={} txnId={} result={}",
               record.getVerifyCallCount(),
               bankId,
               txnId,
               record.getSelectedResult());
    return logVerificationResponse(bankId, txnId, response);
  }

  private VerificationWireResponse logVerificationResponse(String bankId,
                                                            String txnId,
                                                            VerificationWireResponse response) {

    logger.info("Responding to verification call for bankId={} txnId={} contentType={} debugReason={} body={}",
               bankId,
               txnId,
               response.getContentType(),
               response.getDebugReason(),
               response.getBody());
    return response;
  }

  // --- helpers -----------------------------------------------------------------------------

  /**
   * Fires the S2S callback and, if PG's response body is JSON carrying {@code return_url} and
   * {@code transaction_response} (its own next hop for the tester's tab — e.g. the SDK result
   * page), returns that as a ready-to-navigate URL. Returns {@code null} on any failure or when
   * the response doesn't look like that shape, so the caller can fall back to just closing the
   * tab.
   */
  private String fireS2SCallback(String url, HttpMethod method, String bankId, String txnId, String kind) {

    try {
      logger.info("Sending {} S2S request to PG for bankId={} txnId={} method={} url={}",
                 kind,
                 bankId,
                 txnId,
                 method,
                 url);
      ResponseEntity<String> response = restTemplate.exchange(URI.create(url), method, null, String.class);
      logger.info("Received {} S2S response from PG for bankId={} txnId={} status={} body={}",
                 kind,
                 bankId,
                 txnId,
                 response.getStatusCode(),
                 response.getBody());
      return derivePgRedirectUrl(response.getBody());
    } catch (Exception e) {
      logger.warn("{} S2S callback to PG failed for bankId={} txnId={} (this is the simulator's own direct notification, separate from the browser redirect)",
                 kind,
                 bankId,
                 txnId,
                 e);
      return null;
    }
  }

  private String derivePgRedirectUrl(String s2sResponseBody) {

    if (s2sResponseBody == null || s2sResponseBody.isBlank()) {
      return null;
    }
    try {
      Map<?, ?> parsed = objectMapper.readValue(s2sResponseBody, Map.class);
      Object returnUrl = parsed.get("return_url");
      Object transactionResponse = parsed.get("transaction_response");
      if (returnUrl == null || transactionResponse == null) {
        return null;
      }
      return UrlUtil.withQuery(returnUrl.toString(),
                              Map.of("transaction_response", transactionResponse.toString()));
    } catch (Exception e) {
      logger.info("S2S callback response wasn't the return_url/transaction_response JSON shape — nothing for the tab to follow ({})",
                 e.getMessage());
      return null;
    }
  }

  private String generateBankRef() {

    return "SIM" + (100000000000L + (long) (RANDOM.nextDouble() * 899999999999L));
  }

  private void sleep(int seconds) {

    try {
      Thread.sleep(seconds * 1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

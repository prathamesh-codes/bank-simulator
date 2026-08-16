package com.billdesk.pg.payments.simulator.controller;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.billdesk.pg.payments.simulator.dto.InitOutcome;
import com.billdesk.pg.payments.simulator.dto.SubmitOutcome;
import com.billdesk.pg.payments.simulator.dto.ValidationResult;
import com.billdesk.pg.payments.simulator.dto.VerificationWireResponse;
import com.billdesk.pg.payments.simulator.enums.DeliveryMode;
import com.billdesk.pg.payments.simulator.enums.ResultOutcome;
import com.billdesk.pg.payments.simulator.model.SimulatorRecord;
import com.billdesk.pg.payments.simulator.service.SimulatorService;

/**
 * Pure HTTP binding — every endpoint here just parses the request and translates
 * {@link SimulatorService}'s result into a response. No persistence, validation, delivery, or
 * bank-specific logic lives in this class; see {@link SimulatorService} for all of that. Design
 * doc §3.
 */
@Controller
@RequestMapping("/simulator/netbanking")
public class SimulatorController {

  private final SimulatorService simulatorService;

  public SimulatorController(SimulatorService simulatorService) {

    this.simulatorService = simulatorService;
  }

  /**
   * Plays PG's {@code TXN_INIT_URL}. Accepts both POST (form-field banks, e.g. HRE) and GET
   * (query-param banks) — Spring binds either shape into the same {@code params} map, so which
   * one a given bank uses is entirely up to that bank's {@code NetbankingBankSimulator}. On
   * success, redirects the browser to {@link #page} so the tester can pick an outcome; on
   * failure, PG gets a 400 describing which field was wrong.
   */
  @RequestMapping(value = "/{bankId}", method = {RequestMethod.GET, RequestMethod.POST})
  @ResponseBody
  public ResponseEntity<?> init(@PathVariable String bankId, @RequestParam Map<String, String> params) {

    InitOutcome outcome = simulatorService.handleInit(bankId, params);
    if ( !outcome.isSuccess()) {
      ValidationResult failure = outcome.getFailure();
      return ResponseEntity.badRequest()
                           .body("Invalid request from PG. field=" + failure.getField()
                                + " reason=" + failure.getMessage());
    }
    SimulatorRecord record = outcome.getRecord();
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
                         .location(URI.create("/simulator/netbanking/" + bankId
                                              + "/"
                                              + record.getTxnId()))
                         .build();
  }

  /** Renders the tester-facing simulator page for one previously-initiated transaction. */
  @GetMapping("/{bankId}/{txnId}")
  public String page(@PathVariable String bankId, @PathVariable String txnId, Model model) {

    SimulatorRecord record = simulatorService.getRecordOrThrow(bankId, txnId);
    model.addAttribute("record", record);
    model.addAttribute("results", ResultOutcome.values());
    model.addAttribute("deliveries", DeliveryMode.values());
    model.addAttribute("knownFailureReasons", simulatorService.knownFailureReasons(bankId));
    model.addAttribute("defaultDelaySeconds", simulatorService.getDefaultDelaySeconds());
    return "simulator-page";
  }

  /**
   * The tester's pick (result/delivery/failure reason/error code), fired off to
   * {@link SimulatorService#handleSubmit}, which sends the S2S callback to PG. Always responds
   * {@code 200} with {@code {"status":"sent"}}; a {@code redirectUrl} is included only when PG's
   * S2S response carried its own next hop for the tab — see {@code simulator-page.html}'s submit
   * handler for what it does with either shape.
   */
  @PostMapping("/{bankId}/{txnId}/submit")
  public ResponseEntity<?> submit(@PathVariable String bankId,
                                  @PathVariable String txnId,
                                  @RequestParam ResultOutcome result,
                                  @RequestParam DeliveryMode delivery,
                                  @RequestParam(required = false) String failureReason,
                                  @RequestParam(required = false) String errorCode,
                                  @RequestParam(required = false) Integer delaySeconds) {

    SubmitOutcome outcome = simulatorService.handleSubmit(bankId,
                                                          txnId,
                                                          result,
                                                          delivery,
                                                          failureReason,
                                                          errorCode,
                                                          delaySeconds);
    Map<String, String> body = new HashMap<>();
    body.put("status", "sent");
    if (outcome.getRedirectUrl() != null) {
      body.put("redirectUrl", outcome.getRedirectUrl());
    }
    return ResponseEntity.ok(body);
  }

  /**
   * Plays PG's {@code QRY_INIT_URL} double-verification call. The response body/content-type are
   * entirely bank-specific ({@link VerificationWireResponse}); a mismatch is surfaced as a debug
   * header, never fabricated into a fake match.
   */
  @RequestMapping(value = "/checkTxnStatus/{bankId}", method = {RequestMethod.GET, RequestMethod.POST})
  @ResponseBody
  public ResponseEntity<String> checkTxnStatus(@PathVariable String bankId,
                                              @RequestParam Map<String, String> rawParams) {

    VerificationWireResponse response = simulatorService.handleVerification(bankId, rawParams);
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                                                       .contentType(MediaType.parseMediaType(response.getContentType()));
    if (response.getDebugReason() != null) {
      builder.header("X-Simulator-Mismatch-Reason", response.getDebugReason());
    }
    return builder.body(response.getBody());
  }
}

package com.billdesk.pg.payments.simulator.dto;

/**
 * Result of the tester's submit — the S2S callback was fired to PG directly. The tab is never
 * sent to the bankresponse URL itself (that's PG's S2S endpoint, not a browser destination); if
 * PG's response to that call carried its own next hop ({@code return_url} +
 * {@code transaction_response}), the tab navigates there, otherwise it just closes — see
 * {@link com.billdesk.pg.payments.simulator.service.SimulatorService#handleSubmit}.
 */
public class SubmitOutcome {

  private final String redirectUrl;

  /** @param redirectUrl PG-provided next hop for the tab, or null if the tab should just close. */
  public SubmitOutcome(String redirectUrl) {

    this.redirectUrl = redirectUrl;
  }

  public String getRedirectUrl() {

    return redirectUrl;
  }
}

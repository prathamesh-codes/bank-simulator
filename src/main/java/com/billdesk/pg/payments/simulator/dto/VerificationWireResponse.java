package com.billdesk.pg.payments.simulator.dto;

/**
 * A bank's response to the double-verification call (bank_master.QRY_INIT_URL), in that bank's
 * own wire format — key=value for HRE, XML for ICI, bare HTML string for FBK, hex/JSON for BOI
 * (design doc §1.4's table). Deliberately just {@code contentType}/{@code body} so every shape
 * fits without a bank-specific response type.
 */
public class VerificationWireResponse {

  private final String contentType;
  private final String body;
  private final String debugReason;

  public VerificationWireResponse(String contentType, String body) {

    this(contentType, body, null);
  }

  public VerificationWireResponse(String contentType, String body, String debugReason) {

    this.contentType = contentType;
    this.body = body;
    this.debugReason = debugReason;
  }

  public String getContentType() {

    return contentType;
  }

  public String getBody() {

    return body;
  }

  /** Non-null only on a mismatch response (§3.1) — surfaced as a response header, not on the wire body. */
  public String getDebugReason() {

    return debugReason;
  }
}

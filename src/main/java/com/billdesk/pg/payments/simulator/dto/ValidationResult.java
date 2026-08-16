package com.billdesk.pg.payments.simulator.dto;

/**
 * Outcome of validating an inbound PG request (init leg or verification leg) — design doc §3.1.
 * A failure carries which field was wrong and why, so a tester sees a real diagnostic instead of
 * a generic rejection.
 */
public class ValidationResult {

  private static final ValidationResult OK = new ValidationResult(true, null, null);

  private final boolean valid;
  private final String field;
  private final String message;

  private ValidationResult(boolean valid, String field, String message) {

    this.valid = valid;
    this.field = field;
    this.message = message;
  }

  public static ValidationResult ok() {

    return OK;
  }

  public static ValidationResult fail(String field, String message) {

    return new ValidationResult(false, field, message);
  }

  public boolean isValid() {

    return valid;
  }

  public String getField() {

    return field;
  }

  public String getMessage() {

    return message;
  }
}

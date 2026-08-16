package com.billdesk.pg.payments.simulator.dto;

import com.billdesk.pg.payments.simulator.model.SimulatorRecord;

/** Result of validating + persisting PG's TXN_INIT_URL POST — see service.SimulatorService#handleInit. */
public class InitOutcome {

  private final boolean success;
  private final ValidationResult failure;
  private final SimulatorRecord record;

  private InitOutcome(boolean success, ValidationResult failure, SimulatorRecord record) {

    this.success = success;
    this.failure = failure;
    this.record = record;
  }

  public static InitOutcome success(SimulatorRecord record) {

    return new InitOutcome(true, null, record);
  }

  public static InitOutcome failure(ValidationResult failure) {

    return new InitOutcome(false, failure, null);
  }

  public boolean isSuccess() {

    return success;
  }

  public ValidationResult getFailure() {

    return failure;
  }

  public SimulatorRecord getRecord() {

    return record;
  }
}

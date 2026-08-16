package com.billdesk.pg.payments.simulator.dto;

import com.billdesk.pg.payments.simulator.enums.DeliveryMode;
import com.billdesk.pg.payments.simulator.enums.ResultOutcome;
import com.billdesk.pg.payments.simulator.model.SimulatorRecord;

/**
 * The tester's pick on the simulator page — a result plus a delivery mode. {@code errorCode} is
 * free text, submit-leg only (not persisted on {@link SimulatorRecord} — it's a tester-supplied
 * value the wire format may or may not have a slot for, not simulator state), so it's only ever
 * populated when this is built straight from the submit call, not when
 * {@code SimulatorService.handleVerification} reconstructs a {@code SimulatedCase} later from a
 * saved record.
 */
public class SimulatedCase {

  private final ResultOutcome result;
  private final DeliveryMode delivery;
  private final String failureReason;
  private final String errorCode;

  public SimulatedCase(ResultOutcome result, DeliveryMode delivery, String failureReason) {

    this(result, delivery, failureReason, null);
  }

  public SimulatedCase(ResultOutcome result, DeliveryMode delivery, String failureReason, String errorCode) {

    this.result = result;
    this.delivery = delivery;
    this.failureReason = failureReason;
    this.errorCode = errorCode;
  }

  public ResultOutcome getResult() {

    return result;
  }

  public DeliveryMode getDelivery() {

    return delivery;
  }

  public String getFailureReason() {

    return failureReason;
  }

  public String getErrorCode() {

    return errorCode;
  }

  public boolean isSuccess() {

    return result == ResultOutcome.SUCCESS;
  }
}

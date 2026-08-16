package com.billdesk.pg.payments.simulator.enums;

/**
 * How the callback/return leg is delivered, layered on top of a {@link ResultOutcome}. Split out
 * from the result itself (design doc §6/§10 open question #1) so "a delayed success" and "a
 * delayed failure" are both expressible instead of forcing one flat five-way choice.
 */
public enum DeliveryMode {
  NORMAL,
  DELAY,
  DUPLICATE_CALLBACK
}

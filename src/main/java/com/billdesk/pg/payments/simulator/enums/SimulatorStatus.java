package com.billdesk.pg.payments.simulator.enums;

/** Lifecycle of one simulated transaction row, distinct from the tester's chosen outcome. */
public enum SimulatorStatus {
  INITIATED,
  CALLBACK_SENT,
  VERIFIED
}

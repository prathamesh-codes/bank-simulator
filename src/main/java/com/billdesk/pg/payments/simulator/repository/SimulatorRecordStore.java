package com.billdesk.pg.payments.simulator.repository;

import com.billdesk.pg.payments.simulator.model.SimulatorRecord;
import java.util.Optional;

/**
 * The only DB-facing surface {@code service.SimulatorService} talks to — every leg's insert/fetch
 * need reduces to one of these three calls, regardless of which bank or which leg is calling.
 * Keeps JPA specifics (and the bank_id+txn_id upsert pattern) out of the service layer, behind an
 * interface a different persistence implementation could stand in for later without
 * {@code SimulatorService} changing at all.
 */
public interface SimulatorRecordStore {

  /** Fetches the existing row for this bank+txn id, or a fresh unsaved {@link SimulatorRecord} if none exists yet. */
  SimulatorRecord findOrCreate(String bankId, String txnId);

  /** Fetches the existing row for this bank+txn id, if any. */
  Optional<SimulatorRecord> find(String bankId, String txnId);

  /** Inserts or updates the row, returning the persisted instance (with a generated id, if new). */
  SimulatorRecord save(SimulatorRecord record);
}

package com.billdesk.pg.payments.simulator.repository;

import com.billdesk.pg.payments.simulator.model.SimulatorRecord;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The only class in this codebase that talks to {@link SimulatorRecordRepository} directly. */
@Component
public class SimulatorRecordStoreImpl implements SimulatorRecordStore {

  private final SimulatorRecordRepository repository;

  public SimulatorRecordStoreImpl(SimulatorRecordRepository repository) {

    this.repository = repository;
  }

  @Override
  public SimulatorRecord findOrCreate(String bankId, String txnId) {

    return repository.findByBankIdAndTxnId(bankId, txnId).orElseGet(SimulatorRecord::new);
  }

  @Override
  public Optional<SimulatorRecord> find(String bankId, String txnId) {

    return repository.findByBankIdAndTxnId(bankId, txnId);
  }

  @Override
  public SimulatorRecord save(SimulatorRecord record) {

    return repository.save(record);
  }
}

package com.billdesk.pg.payments.simulator.repository;

import com.billdesk.pg.payments.simulator.model.SimulatorRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorRecordRepository extends JpaRepository<SimulatorRecord, Long> {

  Optional<SimulatorRecord> findByBankIdAndTxnId(String bankId, String txnId);
}

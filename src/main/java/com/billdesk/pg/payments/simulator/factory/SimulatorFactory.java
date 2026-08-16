package com.billdesk.pg.payments.simulator.factory;

import com.billdesk.pg.payments.simulator.core.NetbankingBankSimulator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Resolves the right {@link NetbankingBankSimulator} plugin by bank id, mirroring how the real
 * {@code banks} service's {@code bankingServiceFactory.getBankingService(bankId)} works (design
 * doc §2). Adding a bank is just adding one more {@code @Component} under {@code bank/<id>/} —
 * nothing here, or anywhere outside that new package, needs to change.
 */
@Component
public class SimulatorFactory {

  private static final Logger logger = LogManager.getLogger(SimulatorFactory.class);

  private final Map<String, NetbankingBankSimulator> byBankId;

  public SimulatorFactory(List<NetbankingBankSimulator> simulators) {

    this.byBankId = simulators.stream()
                              .collect(Collectors.toMap(s -> s.bankId().toUpperCase(),
                                                       s -> s));
    logger.info("Registered {} netbanking simulator plugin(s): {}",
               byBankId.size(),
               byBankId.keySet());
  }

  public NetbankingBankSimulator get(String bankId) {

    NetbankingBankSimulator simulator = byBankId.get(bankId == null ? null : bankId.toUpperCase());
    if (simulator == null) {
      logger.warn("No simulator plugin registered for bankId={} (known: {})", bankId, byBankId.keySet());
      throw new NoSuchElementException("No simulator plugin registered for bankId=" + bankId);
    }
    return simulator;
  }
}

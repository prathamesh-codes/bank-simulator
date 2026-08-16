package com.billdesk.pg.payments.simulator.dto;

import com.billdesk.pg.payments.simulator.core.NetbankingBankSimulator;
import java.util.Map;

/**
 * Bank-agnostic view of whatever PG's TXN_INIT_URL call actually contained, produced by a
 * plugin's {@link NetbankingBankSimulator#parseInit(Map)} after
 * {@link NetbankingBankSimulator#validateInit(Map)} has already passed.
 */
public class ParsedInit {

  private final String transactionId;
  private final String merchantCode;
  private final String amount;
  private final String currency;
  private final String returnUrl;

  public ParsedInit(String transactionId,
                    String merchantCode,
                    String amount,
                    String currency,
                    String returnUrl) {

    this.transactionId = transactionId;
    this.merchantCode = merchantCode;
    this.amount = amount;
    this.currency = currency;
    this.returnUrl = returnUrl;
  }

  public String getTransactionId() {

    return transactionId;
  }

  public String getMerchantCode() {

    return merchantCode;
  }

  public String getAmount() {

    return amount;
  }

  public String getCurrency() {

    return currency;
  }

  public String getReturnUrl() {

    return returnUrl;
  }
}

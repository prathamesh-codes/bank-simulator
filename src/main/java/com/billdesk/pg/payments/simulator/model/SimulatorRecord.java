package com.billdesk.pg.payments.simulator.model;

import com.billdesk.pg.payments.simulator.enums.ResultOutcome;
import com.billdesk.pg.payments.simulator.enums.SimulatorStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * One row per simulated net-banking transaction attempt. Field set and naming follow
 * banks/docs/netbanking/simulator-jar-design.md §5 (PGI_NETBANKING_SIMULATOR).
 */
@Entity
@Table(name = "PGI_NETBANKING_SIMULATOR",
       uniqueConstraints = @UniqueConstraint(columnNames = {"BANK_ID", "TXN_ID"}))
public class SimulatorRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  private Long id;

  @Column(name = "TXN_ID", nullable = false, length = 50)
  private String txnId;

  @Column(name = "BANK_ID", nullable = false, length = 10)
  private String bankId;

  /** See design doc §5 caveat: for HRE this is MerchantCode (meBankId), not BillDesk's raw mercid. */
  @Column(name = "MERC_ID", length = 30)
  private String mercId;

  @Enumerated(EnumType.STRING)
  @Column(name = "TXN_STATUS", nullable = false, length = 20)
  private SimulatorStatus txnStatus = SimulatorStatus.INITIATED;

  @Enumerated(EnumType.STRING)
  @Column(name = "SELECTED_RESULT", length = 10)
  private ResultOutcome selectedResult;

  @Column(name = "FAILURE_REASON", length = 500)
  private String failureReason;

  @Column(name = "BANK_REF", length = 30)
  private String bankRef;

  @Column(name = "MERCHANT_CODE", length = 30)
  private String merchantCode;

  @Column(name = "TXN_AMOUNT", length = 20)
  private String txnAmount;

  @Column(name = "TXN_CURRENCY", length = 5)
  private String txnCurrency;

  @Column(name = "RETURN_URL", length = 1000)
  private String returnUrl;

  @Lob
  @Column(name = "RAW_INIT_PARAMS")
  private String rawInitParams;

  @Column(name = "DELAY_SECONDS")
  private Integer delaySeconds;

  @Column(name = "VERIFY_CALL_COUNT", nullable = false)
  private int verifyCallCount = 0;

  @Column(name = "CREATED_AT", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "UPDATED_AT", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {

    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {

    updatedAt = LocalDateTime.now();
  }

  public Long getId() {

    return id;
  }

  public String getTxnId() {

    return txnId;
  }

  public void setTxnId(String txnId) {

    this.txnId = txnId;
  }

  public String getBankId() {

    return bankId;
  }

  public void setBankId(String bankId) {

    this.bankId = bankId;
  }

  public String getMercId() {

    return mercId;
  }

  public void setMercId(String mercId) {

    this.mercId = mercId;
  }

  public SimulatorStatus getTxnStatus() {

    return txnStatus;
  }

  public void setTxnStatus(SimulatorStatus txnStatus) {

    this.txnStatus = txnStatus;
  }

  public ResultOutcome getSelectedResult() {

    return selectedResult;
  }

  public void setSelectedResult(ResultOutcome selectedResult) {

    this.selectedResult = selectedResult;
  }

  public String getFailureReason() {

    return failureReason;
  }

  public void setFailureReason(String failureReason) {

    this.failureReason = failureReason;
  }

  public String getBankRef() {

    return bankRef;
  }

  public void setBankRef(String bankRef) {

    this.bankRef = bankRef;
  }

  public String getMerchantCode() {

    return merchantCode;
  }

  public void setMerchantCode(String merchantCode) {

    this.merchantCode = merchantCode;
  }

  public String getTxnAmount() {

    return txnAmount;
  }

  public void setTxnAmount(String txnAmount) {

    this.txnAmount = txnAmount;
  }

  public String getTxnCurrency() {

    return txnCurrency;
  }

  public void setTxnCurrency(String txnCurrency) {

    this.txnCurrency = txnCurrency;
  }

  public String getReturnUrl() {

    return returnUrl;
  }

  public void setReturnUrl(String returnUrl) {

    this.returnUrl = returnUrl;
  }

  public String getRawInitParams() {

    return rawInitParams;
  }

  public void setRawInitParams(String rawInitParams) {

    this.rawInitParams = rawInitParams;
  }

  public Integer getDelaySeconds() {

    return delaySeconds;
  }

  public void setDelaySeconds(Integer delaySeconds) {

    this.delaySeconds = delaySeconds;
  }

  public int getVerifyCallCount() {

    return verifyCallCount;
  }

  public void incrementVerifyCallCount() {

    this.verifyCallCount++;
  }

  public LocalDateTime getCreatedAt() {

    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {

    return updatedAt;
  }
}

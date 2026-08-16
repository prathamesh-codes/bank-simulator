# Adding a new bank

Adding a bank is **one new class** — nothing in `controller/`, `service/`, `factory/`, the DB
entity, or the Thymeleaf page needs to change. `bank/hre/HreService.java` is the reference
implementation.

## Steps

1. Create `bank/<bankid>/<BankId>Service.java`, annotated `@Component`, implementing
   `core.NetbankingBankSimulator`. Spring's classpath scan + `SimulatorFactory` pick it up
   automatically by `bankId()` — nothing to register manually.
2. Implement the interface methods:
   - `bankId()` — the exact `bank_master.BANK_ID` value.
   - `preprocessInit(fields)` *(optional, default no-op)* — override only if this bank's init
     fields arrive encrypted/encoded (e.g. one opaque `data` param); decode once here rather than
     inside both `validateInit` and `parseInit`.
   - `validateInit(fields)` / `parseInit(fields)` — required-field checks and checksum, then pull
     out `ParsedInit` (txn id, merchant code, amount, currency, return URL).
   - `buildCallbackResponse(record, chosenCase)` — this bank's outbound wire format, as a
     `CallbackDelivery` (target URL — use `record.getReturnUrl()`, never a hardcoded URL — plus
     query params and the S2S HTTP method).
   - `validateVerification` / `buildVerificationResponse` / `buildMismatchVerificationResponse` /
     `extractVerificationTxnId` — the double-verification (`QRY_INIT_URL`) leg.
   - `knownFailureReasons()` *(optional)* — failure messages offered on the simulator page.
3. If the bank needs its own config (a checksum key, etc.), inject it with
   `@Value("${simulator.<bankid>.<setting>:<default>}")` inside the new class, and document the
   property in `application.properties` — same pattern as `HreService`'s
   `simulator.hre.checksum-key`.

## Verify it

Same shape as the README's "Try it end to end": POST/GET the init call, open the returned page and
submit, then hit `checkTxnStatus/<BANKID>` with this bank's verify params. Every leg logs the raw
request/response at `INFO` — grep the bank id or txn id in the running log to see the full round
trip if something looks wron  g.

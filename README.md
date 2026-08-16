# netbanking-simulator

A standalone net-banking bank simulator for BillDesk PG UAT/testing. Plugs in per bank — adding a
new one is a single class; see [`docs/adding-a-new-bank.md`](docs/adding-a-new-bank.md).

## What it does

Plays "the bank" for PG's net-banking flow:

1. **The login/redirect leg** — receives PG's `TXN_INIT_URL` call (POST form fields or GET query
   params, depending on the bank), shows a page to pick an outcome (Success/Failure, plus a
   delivery mode: Normal/Delay/Duplicate callback), then fires the resulting callback to PG as an
   S2S call.
2. **The double-verification leg** — answers PG's server-to-server "did this really succeed?" call
   (`QRY_INIT_URL`), consistently with whatever was picked on the login page.

Both legs validate the inbound PG request first (required fields, cross-field consistency, and —
once a bank's test key is configured — a real checksum recomputation) before doing anything else.

Built as a plugin-per-bank (`core.NetbankingBankSimulator`) — the controller, service, DB entity,
and Thymeleaf page are all bank-agnostic. Every bank-specific decision (wire format, checksum
scheme, S2S delivery shape) lives entirely inside that bank's own plugin under `bank/<bankid>/`;
nothing else needs to change to add one.

## Run it

```
mvn spring-boot:run
```

Configuration lives in `src/main/resources/application.properties`:

- `server.port` / TLS settings — set to whatever this is deployed against; not a fixed default.
- `spring.datasource.*` — this connects to a real Oracle DB, not an embedded one. Schema is manual
  DDL (`docs/schema.sql`), applied separately — `spring.jpa.hibernate.ddl-auto=none`, so a schema
  change here means running the matching `ALTER`/`CREATE` against that DB yourself.
- `simulator.delay.default-seconds` / `max-seconds` — bounds for the DELAY delivery mode.
- `simulator.duplicate.second-call-delay-ms` — gap between the two S2S calls fired for
  DUPLICATE_CALLBACK.
- Per-bank settings (checksum keys, etc.) are namespaced `simulator.<bankid>.*`, injected inside
  that bank's own plugin class.

For running the packaged jar as a background process with logging, see `start.sh`/`stop.sh`.

## Try it end to end

```bash
# 1. Simulate PG's TXN_INIT_URL call for a given bank
curl -i -X POST http://<host>:<port>/simulator/netbanking/<BANKID> --data-urlencode "..."
# -> 303, Location: /simulator/netbanking/<BANKID>/<txnId>

# 2. Open that URL in a browser, pick Result + Delivery mode, submit.
#    -> fires the S2S callback to PG; the tab then either closes or navigates wherever PG's
#       response said comes next (its own return_url + transaction_response, if it gave one).

# 3. Simulate PG's double-verification call:
curl "http://<host>:<port>/simulator/netbanking/checkTxnStatus/<BANKID>?..."
```

Every leg logs the raw request/response it saw at `INFO` — grep the bank id or txn id in the
running log to see the full round trip if something looks wrong.

## Adding a new bank

See [`docs/adding-a-new-bank.md`](docs/adding-a-new-bank.md).

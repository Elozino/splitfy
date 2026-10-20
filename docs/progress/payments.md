# Phase 6B — disabled Paystack sandbox integration

Implemented on `codex/payments-integration`, based on `origin/feat/group-invites` at `012d2d2`.
The central implementation plan is intentionally unchanged. No onboarding screens, assets, or other UI changed.

## Delivered

- V10 creates payment intents with unique provider references and per-user request keys, plus uniquely keyed ledger effects. Positive NGN minor-unit amounts are enforced in the database. Financial references restrict deletion pending a retention decision.
- `PaystackSandboxProvider` implements transaction initialization, transfer submission, and verification behind `PaymentProvider`. Only `sk_test_` secrets are accepted; the endpoint is fixed to Paystack. Requests use stored references and integer minor units. HTTP/provider errors fail closed.
- `SandboxPayments` is an explicit local harness with **both wallet and payments flags false by default**. It is deliberately absent from application DI and public route registration. Existing application wallet flags remain OFF. No new production-facing endpoint or client payment flow is enabled.
- Top-up creation and checkout initialization never credit a wallet. Only a raw-body, constant-time verified HMAC-SHA512 webhook can credit it. Test-domain, amount, currency, event/status, reference, and customer email are checked. Unknown events/references have no effect.
- Reusable authorization codes are saved only from verified matching successful charges. This integration has no client-supplied authorization-token input.
- Withdrawal creation atomically reserves the spendable balance and writes its debit. Its intent remains PENDING until verified transfer success (COMPLETED), failure, or reversal (REVERSED). Reversal appends one compensating credit, including after completion. Late success cannot revive a reversed intent; late failure cannot undo a completed intent. Ledger rows remain immutable.
- Locked intent and wallet rows, unique effect keys, checked arithmetic, and shared database transactions prevent duplicate credits/refunds and concurrent overspending. Failed reservations roll back the intent too.
- Reconciliation reads pending intents in bounded batches, verifies with the provider, and reports status, mismatch, or failure. It does **not** synthesize webhook events or credit balances. Coroutine cancellation propagates.

## Local use and validation

`./gradlew :backend:test` — passed: 64 tests, 0 failures/errors/skips (2026-10-20).

`SandboxPaymentsTest` uses embedded PostgreSQL (actual Flyway migrations) and Ktor MockEngine. No Paystack account, external payment request, real money, paid service, or production deployment was used.

The focused cases cover default-disabled flags/live-key rejection, request-key payload conflicts, signature tampering, wrong amount/currency/customer/domain, concurrent duplicate webhooks, verified authorization capture, withdrawal completion/failure/reversal and reordered events, concurrent overspending, uncertain dispatch, read-only reconciliation, and provider HTTP request construction. `DatabaseSchemaTest` also checks the new tables against V10.

For a local harness, explicitly construct `SandboxPayments(repository, provider, verifier, SandboxPaymentFlags(wallet = true, payments = true))`. Create an intent first, then call `checkout(reference)` or `dispatchWithdrawal(reference)` using its persisted reference. Supply raw bytes and the `x-paystack-signature` header to `webhook`. No callback or HTTP success is settlement evidence. The harness's account email and recipient arguments must originate from trusted account verification/onboarding, not public request fields.

## Deliberate limitations and activation prerequisites

- **Provider/business approval:** Paystack/NGN is the plan's assumption, not an approved provider contract. Confirm that the proposed stored-balance/top-up/peer-payment/withdrawal model is supported, merchant entity and country, settlement accounts, fees, chargebacks/refunds, transaction limits, and recipient ownership verification.
- **Compliance decisions remain unresolved:** determine the permitted custody/ledger model, any required licensing or regulated partner arrangement, KYC/AML/sanctions responsibilities, fraud controls, data retention and deletion exceptions, consent, disputes and reconciliation ownership. This implementation does not resolve these matters or make a legal-compliance claim.
- **Missing sandbox configuration:** a merchant's Paystack `sk_test_...` secret (same secret for webhook verification), approved sandbox recipient codes, verified test customer account, and a chosen HTTPS webhook host/path registered in the test dashboard. The current code has no environment variable or network ingress configured for these. Never supply live keys; they are rejected.
- **Public API/client integration:** deliberately deferred while disabled. Before exposing endpoints, implement authenticated ownership checks, verified account email lookup, server-owned recipient enrollment and binding, request/rate/body-size controls, and webhook response/retry policy. Mounting the legacy simulated wallet write routes is not a substitute for this integration. Existing legacy client-token/payment-method routes are not used here.
- **Dispatch recovery:** intent creation/reservation commits before outbound dispatch. A crash or timeout leaves PENDING funds reserved for reconciliation; it does not automatically release or redispatch. A durable dispatch worker/claim mechanism and provider-supported retry policy are needed before enabling concurrent automatic dispatch. Operators must verify/resend the same reference, never create a fresh withdrawal to retry an uncertain one.
- **Reconciliation operations:** add scheduling, pagination/age policy, operational alerts and audited resolution; the bounded scaffold currently reports pending intents only. Completed-transfer reversal audits, refunds/disputes, fee accounting, and settlement reconciliation are not implemented.
- **Ledger presentation:** withdrawal status belongs to its payment intent, while immutable debit/compensating-credit rows are linked by `payment_effects`. Compensating credits use the existing TOP_UP credit enum with an explicit reversal description; introduce a dedicated reversal presentation/type before a customer-facing statement rollout.
- **Financial deletion policy:** V10 intentionally retains referential integrity instead of cascading financial records. Coordinate retention/anonymization with V11 account deletion before activation.
- **No live provider validation performed:** all HTTP was mocked. An authorized sandbox exercise is still needed to validate the merchant-specific transfer workflow, including OTP settings and real webhook payloads.

## Provider references checked

- [Paystack webhooks](https://paystack.com/docs/payments/webhooks/): raw payload HMAC-SHA512 and event/retry semantics.
- [Transaction API](https://paystack.com/docs/api/transaction/): initialization and reference verification.
- [Transfer API](https://paystack.com/docs/api/transfer/): submission and reference verification.

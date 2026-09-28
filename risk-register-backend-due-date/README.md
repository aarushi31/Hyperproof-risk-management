# Risk Register — Backend

Spring Boot 3 / Java 17 REST API for a Risk Register: create risks, score them (inherent + residual),
attach mitigations, filter and sort by severity.

## Run it (≈2 minutes)

Prerequisites: JDK 17+ and Maven 3.9+.

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```

API is on `http://localhost:8080`. CORS allows `localhost:5173` and `localhost:3000` (change `app.cors.allowed-origins`).

**Tests** :
```bash
mvn test
```

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/risks` | 201 + `Location`. `status` optional (defaults to `OPEN`). |
| GET | `/api/risks` | `?category=&status=&sortBy=residual\|inherent\|created&direction=asc\|desc`. Default: **residual desc**. Enum values are case-insensitive. |
| GET | `/api/risks/{id}` | Includes `mitigations`. |
| PUT | `/api/risks/{id}` | Full replacement of editable fields (`status` optional → unchanged). |
| DELETE | `/api/risks/{id}` | 204; mitigations are deleted with it. |
| GET / POST | `/api/risks/{riskId}/mitigations` | POST → 201. |
| PUT / DELETE | `/api/risks/{riskId}/mitigations/{id}` | A mitigation is only reachable through its own risk (else 404). |

Risk JSON: `id, title, description, category, owner, likelihood, impact, status, inherentScore, inherentSeverity,
residualScore, residualSeverity, mitigationCount, mitigations[], createdAt, updatedAt`.
Enums are upper-case: categories `OPERATIONAL|FINANCIAL|COMPLIANCE|SECURITY|STRATEGIC`, statuses `OPEN|MITIGATING|CLOSED`,
severities `LOW|MEDIUM|HIGH|CRITICAL`.

### Errors
Every error has the same shape:
```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "code": "VALIDATION_ERROR",
  "message": "Validation failed: likelihood must be an integer between 1 and 5",
  "fieldErrors": [ { "field": "likelihood", "message": "likelihood must be an integer between 1 and 5" } ] }
```
| Status | When | `code` |
|---|---|---|
| 400 | Bean-validation failures (ranges, blanks, lengths) | `VALIDATION_ERROR` |
| 400 | Malformed JSON, `3.5` or `"abc"` for an integer, unknown enum value (message lists allowed values) | `MALFORMED_REQUEST` |
| 400 | Bad query parameter (`?category=nope`) | `BAD_REQUEST` |
| 404 | Unknown risk / mitigation | `NOT_FOUND` |
| 409 | Domain rule violated (see below) | `RISK_CLOSURE_REQUIRES_MITIGATION`, `LAST_MITIGATION_ON_CLOSED_RISK` |

Ratings are also protected at the database level with `CHECK` constraints, so data integrity does not rely on the API layer alone.

## Next review date (stretch goal)
`nextReviewDate` (`YYYY-MM-DD`) is stored on the risk (Flyway `V2`). Responses include `overdue`, computed server-side by `RiskPolicy.isOverdue`: true only when the date is **before today** (due today is not overdue) and the risk is **not Closed** (nothing left to review). Assumptions: past dates are accepted on create/update (e.g. importing an already-late review); `PUT` without the field clears it; "today" uses the server's time zone via an injectable `Clock`.

## Scoring

**Inherent** = likelihood × impact (1–25).

**Residual** — each mitigation with effectiveness *e* (1–5) leaves `1 − 0.15·e` of the risk in place; mitigations compound multiplicatively:

```
factor   = max( ∏ (1 − 0.15·eᵢ) , 0.10 )
residual = max( 1 , round_half_up( inherent × factor ) )
```

| effectiveness | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| risk remaining | 85% | 70% | 55% | 40% | 25% |

Worked examples (inherent = 20): none → **20** · one *e=1* → **17** · one *e=3* → **11** · one *e=5* → **5** · *e=5 + e=3* → **3** · *e=5 + e=5* → **2** (capped).

**Why this formula**
- *Sanity checks hold:* no mitigations ⇒ factor 1 ⇒ residual = inherent; one *e=5* control cuts 75% (20 → 5); never below 1; never above inherent.
- *Multiplicative, not additive/averaging:* a second control removes a share of what is *left*, so returns diminish naturally and it can never go negative. Adding a mitigation can never raise residual; order doesn't matter.
- *Not averaging:* averaging would mean adding a weak control to a strong one makes the risk *worse* — indefensible in an audit.
- *90% cap:* no set of controls makes a risk vanish; auditors are skeptical of "zero" residual. The cap also stops ten mediocre controls from beating one excellent one.
- *Integer output, half-up:* severity bands are defined on integers (1–5, 6–12, …), so an integer residual keeps banding unambiguous. Exact `BigDecimal` math keeps rounding deterministic.
- *Derived, never stored:* scores are computed on read, so editing/adding/removing a mitigation can never leave a stale score.

The constants (`15%` per point, `0.10` floor) are in `RiskScoring` and would be the natural knobs to make configurable per organization.

## Business rule: closing a risk

> **A risk cannot be `CLOSED` while it has zero mitigations → `409 Conflict`.** The reverse is enforced too: you cannot delete the last mitigation of a `CLOSED` risk (re-open first, or add another).

Reasoning: in a compliance product, "Closed" tells an auditor *"this was dealt with"*. A closed risk with no recorded control has no evidence trail — it is either an unaddressed risk hidden from the dashboard or an undocumented decision. Making it a hard invariant (`Closed ⇒ ≥ 1 mitigation`) rather than a one-time check on the status change keeps the data trustworthy no matter which endpoint is used. It is `409` (not `400`) because the request is well-formed; it conflicts with current state. The rule lives in `RiskPolicy` (pure Java, unit-tested).

Known gap: legitimately closing a risk that needs no control (e.g. the asset was decommissioned, or leadership formally *accepts* it) isn't expressible. With more time I'd add an `ACCEPTED`/`RETIRED` status requiring a justification and approver rather than loosening this rule.

## Assumptions & trade-offs
- **Single implicit org, no auth** (out of scope). CORS is open to local dev origins only.
- **Sorting by residual happens in memory.** Residual is derived, so it can't be `ORDER BY`-ed without storing or a SQL expression. Filters run in SQL and mitigations are fetched in the same query (no N+1); fine for hundreds–low thousands of risks. To scale: materialize residual on write (recomputed in the same transaction as any mitigation change) or add pagination + a computed-column/view.
- **Risk responses always embed mitigations** (simplicity; lists are small). A summary DTO would be a cheap optimization.
- **`PUT` is full replacement** of editable fields; `status` is the one optional field.
- **Owner is required** free text (accountability), title ≤ 200 chars, description ≤ 4000.
- **PostgreSQL is primary.** H2 (PostgreSQL mode) is used for tests and the optional `h2` profile. Trade-off: tests don't exercise real PG behavior; the schema SQL is deliberately portable. With more time I'd use Testcontainers.
- **Flyway owns the schema**; Hibernate `ddl-auto=none`. Enums are stored as strings with `CHECK` constraints.
- **No optimistic locking / audit history.** Concurrent edits are last-write-wins. A real product would add `@Version` and a change log (who changed a score, when) — very relevant for auditors.
- **Integer coercion:** floats like `3.5` are rejected (not truncated); numeric strings like `"4"` are still accepted by Jackson's default coercion.

## What I'd do with more time
Testcontainers Postgres in CI; `@Version` + audit trail; pagination; configurable scoring parameters per org; `ACCEPTED` status with justification; `nextReviewDate` + overdue flag; framework mapping (NIST CSF / SOC 2 TSC); OpenAPI docs (springdoc).

## Layout
```
scoring/     RiskScoring, Severity        pure logic, no Spring — the most heavily tested part
domain/      Risk, Mitigation, RiskPolicy JPA entities + lifecycle rules
service/     RiskService, MitigationService, RiskMapper
web/         controllers, DTOs (records), GlobalExceptionHandler
```
Tests: `RiskScoringTest` (scoring + bands), `RiskPolicyTest` (closure rule), `RiskApiIntegrationTest` (HTTP → DB end to end).

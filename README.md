# Risk Register

A small full-stack Risk Register: create risks, score them (inherent and residual), attach mitigations, and see everything on a severity-colour-coded dashboard.

- **Backend:** Java 17, Spring Boot 3, Spring Data JPA, Flyway, in-memory H2 database (`risk-register-backend/`)
- **Frontend:** React 18, TypeScript, Vite (`risk-register-frontend/`)

## Run it locally (about 5 minutes)

**Prerequisites:** JDK 17+, Maven 3.9+, Node 18+ (`JAVA_HOME` must point at your JDK).

**1. Start the backend** (terminal 1):
```bash
cd risk-register-backend-due-date
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```
On Windows PowerShell, put the option in quotes:
```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```
The API runs on http://localhost:8080. The `h2` profile is required: it selects the in-memory database, and the data is reset every time the app stops.

**2. Start the frontend** (terminal 2):
```bash
cd risk-register-frontend-due-date
npm install
npm run dev
```
Open http://localhost:5173. The Vite dev server proxies `/api` to `localhost:8080`, so no CORS setup is needed.

**3. Try it:** click **+ New risk**, open it, add a mitigation, and watch the residual score drop.

## Run the tests

```bash
cd risk-register-backend-due-date && mvn test        # scoring, severity bands, business rules, end-to-end API tests
cd risk-register-frontend-due-date && npm test       # severity-band / inherent-score helpers
```
Backend tests need no setup; they use their own in-memory database.

## Project layout

```
risk-register-backend-due-date/
  scoring/    RiskScoring, Severity        pure logic, no Spring; the most heavily tested part
  domain/     Risk, Mitigation, RiskPolicy JPA entities and lifecycle rules
  service/    RiskService, MitigationService, RiskMapper
  web/        controllers, DTOs, GlobalExceptionHandler
  resources/db/migration/                  Flyway schema (V1 tables, V2 next review date)
risk-register-frontend-due-date/
  src/lib/         api client, types, severity helpers (+ tests)
  src/components/  Dashboard, RiskForm, RiskDetail, SeverityBadge, ReviewDate
```

## Features

**Backend API**

| Method | Path | Notes |
|---|---|---|
| POST | `/api/risks` | 201 + `Location`. `status` optional (defaults to `OPEN`). |
| GET | `/api/risks` | `?category=&status=&sortBy=residual\|inherent\|created&direction=asc\|desc`. Default: residual, highest first. Values are case-insensitive. |
| GET | `/api/risks/{id}` | Includes mitigations. |
| PUT | `/api/risks/{id}` | Full replacement of the editable fields. |
| DELETE | `/api/risks/{id}` | 204. Its mitigations are deleted too. |
| GET, POST | `/api/risks/{riskId}/mitigations` | POST returns 201. |
| PUT, DELETE | `/api/risks/{riskId}/mitigations/{id}` | A mitigation is only reachable through its own risk (otherwise 404). |

Enums are upper-case: categories `OPERATIONAL, FINANCIAL, COMPLIANCE, SECURITY, STRATEGIC`; statuses `OPEN, MITIGATING, CLOSED`; severities `LOW, MEDIUM, HIGH, CRITICAL`.

**Errors** always use one shape: `{ timestamp, status, error, code, message, fieldErrors[] }`.

| Status | When | `code` |
|---|---|---|
| 400 | Likelihood, impact or effectiveness not an integer 1–5, blank fields, bad enum value, bad date | `VALIDATION_ERROR` / `MALFORMED_REQUEST` |
| 400 | Bad query parameter (`?category=nope`) | `BAD_REQUEST` |
| 404 | Unknown risk or mitigation | `NOT_FOUND` |
| 409 | Business rule violated (see below) | `RISK_CLOSURE_REQUIRES_MITIGATION`, `LAST_MITIGATION_ON_CLOSED_RISK` |

Values like `3.5` are rejected, not truncated. The database also has `CHECK` constraints on ratings, category and status.

**Frontend**
- **Dashboard:** title, category, status, inherent score, residual score, mitigation count, next review date. Filter by category and status; click the *Residual* header to flip the sort. Severity is shown as colour **and** a text label on each badge, plus a coloured left border on each row, so the worst risks stand out at a glance.
- **Create/edit form:** the inherent score and its severity band update live as likelihood and impact change. Server error messages are shown as-is.
- **Detail view:** mitigations list, add/remove mitigation. The risk is re-fetched after each change, so the residual score always comes from the backend.
- **Stretch goals done:** next review date with an overdue indicator, and loading/error states on API calls.

## Scoring

**Inherent** = likelihood × impact (1–25).

**Residual.** Each mitigation with effectiveness *e* (1–5) leaves `1 − 0.15·e` of the risk in place, and mitigations compound multiplicatively:

```
factor   = max( ∏ (1 − 0.15·eᵢ) , 0.10 )
residual = max( 1 , round_half_up( inherent × factor ) )
```

| Effectiveness | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| Risk remaining | 85% | 70% | 55% | 40% | 25% |

Examples with inherent = 20: no mitigations gives **20**; one *e=1* gives **17**; one *e=3* gives **11**; one *e=5* gives **5**; *e=5 and e=3* gives **3**; *e=5 and e=5* gives **2** (capped).

**Why this formula**
- **Sanity checks hold:** no mitigations means residual equals inherent; one highly effective control cuts 75% (20 becomes 5); residual is never below 1 and never above inherent.
- **Multiplicative, not additive:** each extra control removes a share of what is *left*, so returns diminish and the score can't go negative. Adding a mitigation can never raise the residual, and order doesn't matter.
- **Not an average:** averaging would let a weak control make a well-mitigated risk look *worse*, which can't be defended in an audit.
- **90% cap:** no set of controls makes a risk vanish, and ten mediocre controls can't beat one excellent one.
- **Integer, half-up rounding:** severity bands are defined on integers, and exact `BigDecimal` math keeps rounding deterministic.
- **Derived, never stored:** scores are computed on read, so they can't go stale.

**Severity bands** (inherent and residual): Low 1–5, Medium 6–12, High 13–19, Critical 20–25.

## Business rule: closing a risk

> A risk cannot be **Closed** while it has zero mitigations (`409 Conflict`). The reverse is enforced too: the last mitigation of a Closed risk cannot be deleted.

In a compliance product, "Closed" tells an auditor that the risk was dealt with. A closed risk with no recorded control has no evidence trail. Enforcing it as an invariant ("Closed implies at least one mitigation") rather than a one-time check keeps the data trustworthy through every endpoint. It is a 409 rather than a 400 because the request is well-formed but conflicts with the risk's current state.

**Known gap:** you can't close a risk that legitimately needs no control (for example, the asset was decommissioned or leadership formally accepted the risk). With more time I'd add an `ACCEPTED`/`RETIRED` status that requires a justification and approver.

## Next review date (stretch goal)

`nextReviewDate` (`YYYY-MM-DD`) is stored on the risk. The API returns an `overdue` flag, computed by the backend: true only when the date is **before today** (due today is not overdue) and the risk is **not Closed**. The UI shows a red **OVERDUE** badge and doesn't recompute the flag, so it can't disagree with the API. Past dates are accepted (for example, importing an already-late review), and an update without the field clears it. "Today" uses the server's time zone.

## Assumptions and trade-offs

- **Database:** the app uses in-memory **H2 in PostgreSQL-compatibility mode** to keep local setup to one command. The trade-off is that data is lost on restart and tests don't exercise real PostgreSQL behaviour. The schema is managed by Flyway migrations with portable SQL, so moving to PostgreSQL later needs a datasource change, not a redesign.
- **No auth or multi-tenancy** (out of scope): one implicit organization.
- **Sorting by residual happens in memory**, because residual is derived from mitigations. Filters run in SQL and mitigations are fetched in the same query (no N+1). This is fine for hundreds to low thousands of risks. To scale, I'd store the residual on write and paginate.
- **Risk responses always include their mitigations** (simple; lists are small).
- **`PUT` replaces the editable fields;** `status` is the one optional field (unchanged when omitted).
- **Owner is required** free text, for accountability.
- **No optimistic locking or audit history:** concurrent edits are last-write-wins.
- **Frontend:** state-based navigation (no router, so no deep links); no UI for editing a mitigation (the API supports it); no component tests; plain CSS and no design system. The live inherent score preview duplicates a trivial backend rule, is unit-tested, and the backend stays authoritative.
- **Skipped on purpose:** exhaustive edge cases, authentication, deployment and CI, per the brief.

## With more time I would

Audit trail and optimistic locking; pagination and stored residual scores; per-organization scoring parameters; `ACCEPTED` status with justification; framework mapping (NIST CSF or SOC 2 criteria) surfaced in the UI; React Testing Library tests and optimistic updates; router-based deep links; OpenAPI docs.

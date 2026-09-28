# Risk Register — Frontend (React + TypeScript + Vite)

Requires Node 18+ and the backend running on `http://localhost:8080` (`mvn spring-boot:run "-Dspring-boot.run.profiles=h2"`).

```bash
npm install
npm run dev      # http://localhost:5173  (Vite proxies /api -> localhost:8080, so no CORS setup)
npm test         # unit tests for the severity-band / inherent-score helpers
npm run build    # type-check + production build
```

## What's here
- **Dashboard**: title, category, status, inherent, residual, mitigation count. Filter by category/status; click the *Residual* header to flip sort (default: highest first). Severity is shown as colour **plus** a text label on badges, and a coloured left border on each row, so the worst risks stand out without reading numbers.
- **Create/edit form**: inherent score and band update live as likelihood/impact change. Backend errors (e.g. the 409 for closing a risk with no mitigations) are shown verbatim.
- **Detail view**: mitigation list, add/remove mitigation. After each change the risk is re-fetched, so the residual score always comes from the backend (single source of truth — the residual formula is not duplicated in the UI).
- **Next review date**: date picker on the form, a *Next review* column and detail line, with a red **⚠ OVERDUE** badge when the backend's `overdue` flag is true (the UI doesn't recompute it, so it can't disagree with the API).
- Loading and error states on all API calls.

## Trade-offs / with more time
- State-based navigation (no react-router), so views have no URLs/deep links.
- The live inherent score/band duplicates a trivial backend rule (L×I, band thresholds); it is unit-tested, but the backend remains authoritative.
- No component tests (React Testing Library) or optimistic updates; no edit-mitigation UI (the API supports it).
- Plain CSS, no design system, desktop-first layout.

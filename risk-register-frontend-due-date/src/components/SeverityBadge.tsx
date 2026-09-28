import type { Severity } from '../lib/severity';

/** Colour + text label (never colour alone) so severity is readable at a glance and accessible. */
export function SeverityBadge({ score, severity }: { score: number; severity: Severity }) {
  return <span className={`badge sev-${severity}`}>{score} · {severity}</span>;
}

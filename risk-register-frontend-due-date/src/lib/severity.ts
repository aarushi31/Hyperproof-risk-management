export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/** Mirrors the backend so the form can show the inherent score live, before saving. */
export const inherentScore = (likelihood: number, impact: number): number => likelihood * impact;

export function severityFor(score: number): Severity {
  if (score <= 5) return 'LOW';
  if (score <= 12) return 'MEDIUM';
  if (score <= 19) return 'HIGH';
  return 'CRITICAL';
}

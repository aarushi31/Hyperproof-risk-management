import type { Severity } from './severity';

export const CATEGORIES = ['OPERATIONAL', 'FINANCIAL', 'COMPLIANCE', 'SECURITY', 'STRATEGIC'] as const;
export const STATUSES = ['OPEN', 'MITIGATING', 'CLOSED'] as const;
export type Category = (typeof CATEGORIES)[number];
export type Status = (typeof STATUSES)[number];

export interface Mitigation { id: number; riskId: number; description: string; effectiveness: number; createdAt: string }
export interface Risk {
  id: number; title: string; description: string | null; category: Category; owner: string;
  likelihood: number; impact: number; status: Status; nextReviewDate: string | null; overdue: boolean;
  inherentScore: number; inherentSeverity: Severity; residualScore: number; residualSeverity: Severity;
  mitigationCount: number; mitigations: Mitigation[]; createdAt: string; updatedAt: string;
}
export interface RiskInput {
  title: string; description: string; category: Category; owner: string; likelihood: number; impact: number; status: Status;
  nextReviewDate: string | null; // YYYY-MM-DD, null = none
}
export interface MitigationInput { description: string; effectiveness: number }

import type { Mitigation, MitigationInput, Risk, RiskInput } from './types';

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch('/api' + path, { headers: { 'Content-Type': 'application/json' }, ...init });
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try { message = (await res.json()).message ?? message; } catch { /* non-JSON error body */ }
    throw new Error(message); // backend messages are already user-readable
  }
  return res.status === 204 ? (undefined as T) : res.json();
}
const send = (method: string, body?: unknown): RequestInit => ({ method, body: body ? JSON.stringify(body) : undefined });

export const api = {
  listRisks(f: { category?: string; status?: string; direction: 'asc' | 'desc' }) {
    const q = new URLSearchParams({ sortBy: 'residual', direction: f.direction });
    if (f.category) q.set('category', f.category);
    if (f.status) q.set('status', f.status);
    return req<Risk[]>('/risks?' + q);
  },
  getRisk: (id: number) => req<Risk>(`/risks/${id}`),
  createRisk: (b: RiskInput) => req<Risk>('/risks', send('POST', b)),
  updateRisk: (id: number, b: RiskInput) => req<Risk>(`/risks/${id}`, send('PUT', b)),
  deleteRisk: (id: number) => req<void>(`/risks/${id}`, send('DELETE')),
  addMitigation: (riskId: number, b: MitigationInput) => req<Mitigation>(`/risks/${riskId}/mitigations`, send('POST', b)),
  deleteMitigation: (riskId: number, id: number) => req<void>(`/risks/${riskId}/mitigations/${id}`, send('DELETE')),
};

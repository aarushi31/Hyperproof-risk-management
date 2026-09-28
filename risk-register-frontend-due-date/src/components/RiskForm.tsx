import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../lib/api';
import { inherentScore, severityFor } from '../lib/severity';
import { CATEGORIES, STATUSES, type RiskInput } from '../lib/types';
import { SeverityBadge } from './SeverityBadge';

const empty: RiskInput = { title: '', description: '', category: 'OPERATIONAL', owner: '', likelihood: 3, impact: 3, status: 'OPEN', nextReviewDate: null };
const RATINGS = [1, 2, 3, 4, 5];

export function RiskForm({ id, onDone, onCancel }: { id?: number; onDone: (id: number) => void; onCancel: () => void }) {
  const [form, setForm] = useState<RiskInput>(empty);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = <K extends keyof RiskInput>(k: K, v: RiskInput[K]) => setForm((f) => ({ ...f, [k]: v }));

  useEffect(() => {
    if (id === undefined) return;
    api.getRisk(id).then((r) => setForm({ ...r, description: r.description ?? '' })).catch((e) => setError(e.message));
  }, [id]);

  const score = inherentScore(form.likelihood, form.impact);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      const saved = id === undefined ? await api.createRisk(form) : await api.updateRisk(id, form);
      onDone(saved.id);
    } catch (err) {
      setError((err as Error).message); // e.g. the 409 "cannot close without mitigations" message
    } finally {
      setSaving(false);
    }
  }

  const rating = (k: 'likelihood' | 'impact') => (
    <select value={form[k]} onChange={(e) => set(k, Number(e.target.value))}>{RATINGS.map((n) => <option key={n}>{n}</option>)}</select>
  );

  return (
    <form onSubmit={submit} className="card form">
      <h2>{id === undefined ? 'New risk' : 'Edit risk'}</h2>
      <label>Title<input required maxLength={200} value={form.title} onChange={(e) => set('title', e.target.value)} /></label>
      <label>Description<textarea maxLength={4000} value={form.description} onChange={(e) => set('description', e.target.value)} /></label>
      <div className="grid">
        <label>Category<select value={form.category} onChange={(e) => set('category', e.target.value as RiskInput['category'])}>
          {CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select></label>
        <label>Owner<input required maxLength={200} value={form.owner} onChange={(e) => set('owner', e.target.value)} /></label>
        <label>Likelihood (1–5){rating('likelihood')}</label>
        <label>Impact (1–5){rating('impact')}</label>
        <label>Status<select value={form.status} onChange={(e) => set('status', e.target.value as RiskInput['status'])}>
          {STATUSES.map((s) => <option key={s}>{s}</option>)}</select></label>
        <label>Next review date<input type="date" value={form.nextReviewDate ?? ''} onChange={(e) => set('nextReviewDate', e.target.value || null)} /></label>
        <div>Inherent score<div><SeverityBadge score={score} severity={severityFor(score)} /></div>
          <div className="muted">{form.likelihood} × {form.impact}</div></div>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button className="primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
        <button type="button" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

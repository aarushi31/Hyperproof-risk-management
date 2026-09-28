import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api } from '../lib/api';
import type { Risk } from '../lib/types';
import { ReviewDate } from './ReviewDate';
import { SeverityBadge } from './SeverityBadge';

export function RiskDetail({ id, onBack, onEdit }: { id: number; onBack: () => void; onEdit: () => void }) {
  const [risk, setRisk] = useState<Risk | null>(null);
  const [error, setError] = useState('');
  const [description, setDescription] = useState('');
  const [effectiveness, setEffectiveness] = useState(3);

  const load = useCallback(() => api.getRisk(id).then(setRisk).catch((e) => setError(e.message)), [id]);
  useEffect(() => { load(); }, [load]);

  // Every mutation re-fetches the risk, so the residual score shown always comes from the backend.
  async function run(action: () => Promise<unknown>) {
    setError('');
    try { await action(); await load(); } catch (e) { setError((e as Error).message); }
  }

  async function addMitigation(e: FormEvent) {
    e.preventDefault();
    await run(async () => { await api.addMitigation(id, { description, effectiveness }); setDescription(''); });
  }

  async function removeRisk() {
    if (!window.confirm('Delete this risk and its mitigations?')) return;
    try { await api.deleteRisk(id); onBack(); } catch (e) { setError((e as Error).message); }
  }

  if (!risk) return error ? <p className="error">{error}</p> : <p className="muted">Loading…</p>;

  return (
    <div>
      <button onClick={onBack}>← Back</button>
      <div className="card">
        <div className="toolbar">
          <h2>{risk.title}</h2>
          <button onClick={onEdit}>Edit</button>
          <button className="danger" onClick={removeRisk}>Delete</button>
        </div>
        <p className="muted">{risk.category} · {risk.status} · Owner: {risk.owner}</p>
        <p>Next review: <ReviewDate date={risk.nextReviewDate} overdue={risk.overdue} /></p>
        {risk.description && <p>{risk.description}</p>}
        <div className="scores">
          <div>Inherent ({risk.likelihood} × {risk.impact})<br /><SeverityBadge score={risk.inherentScore} severity={risk.inherentSeverity} /></div>
          <div className="arrow">→</div>
          <div>Residual<br /><SeverityBadge score={risk.residualScore} severity={risk.residualSeverity} /></div>
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="card">
        <h3>Mitigations ({risk.mitigationCount})</h3>
        {risk.mitigations.length === 0 && <p className="muted">No mitigations yet — residual equals inherent.</p>}
        <ul className="mitigations">
          {risk.mitigations.map((m) => (
            <li key={m.id}>
              <span>{m.description} <span className="muted">(effectiveness {m.effectiveness}/5)</span></span>
              <button className="danger" onClick={() => run(() => api.deleteMitigation(id, m.id))}>Remove</button>
            </li>
          ))}
        </ul>
        <form onSubmit={addMitigation} className="form">
          <h4>Add mitigation</h4>
          <label>Description<input required maxLength={2000} value={description} onChange={(e) => setDescription(e.target.value)} /></label>
          <label>Effectiveness (1 = weak, 5 = very strong)
            <select value={effectiveness} onChange={(e) => setEffectiveness(Number(e.target.value))}>
              {[1, 2, 3, 4, 5].map((n) => <option key={n}>{n}</option>)}
            </select>
          </label>
          <div className="actions"><button className="primary">Add mitigation</button></div>
        </form>
      </div>
    </div>
  );
}

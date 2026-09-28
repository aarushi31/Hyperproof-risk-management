import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { CATEGORIES, STATUSES, type Risk } from '../lib/types';
import { ReviewDate } from './ReviewDate';
import { SeverityBadge } from './SeverityBadge';

export function Dashboard({ onOpen, onNew }: { onOpen: (id: number) => void; onNew: () => void }) {
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [risks, setRisks] = useState<Risk[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    api.listRisks({ category, status, direction }).then(setRisks).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, [category, status, direction]);

  return (
    <div>
      <div className="toolbar">
        <h2>Risk dashboard</h2>
        <label>Category
          <select value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All</option>{CATEGORIES.map((c) => <option key={c}>{c}</option>)}
          </select>
        </label>
        <label>Status
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}
          </select>
        </label>
        <button className="primary" onClick={onNew}>+ New risk</button>
      </div>
      {error && <p className="error">Could not load risks: {error}</p>}
      {loading && <p className="muted">Loading…</p>}
      {!loading && !error && risks.length === 0 && <p className="muted">No risks match. Create one to get started.</p>}
      {risks.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Title</th><th>Category</th><th>Status</th><th>Inherent</th>
              <th className="sortable" onClick={() => setDirection(direction === 'desc' ? 'asc' : 'desc')}>
                Residual {direction === 'desc' ? '▼' : '▲'}
              </th>
              <th>Mitigations</th><th>Next review</th>
            </tr>
          </thead>
          <tbody>
            {risks.map((r) => (
              <tr key={r.id} className={`row sev-border-${r.residualSeverity}`} onClick={() => onOpen(r.id)}>
                <td><strong>{r.title}</strong><div className="muted">{r.owner}</div></td>
                <td>{r.category}</td><td>{r.status}</td>
                <td><SeverityBadge score={r.inherentScore} severity={r.inherentSeverity} /></td>
                <td><SeverityBadge score={r.residualScore} severity={r.residualSeverity} /></td>
                <td>{r.mitigationCount}</td>
                <td><ReviewDate date={r.nextReviewDate} overdue={r.overdue} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

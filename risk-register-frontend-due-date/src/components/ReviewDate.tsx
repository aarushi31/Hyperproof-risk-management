export function ReviewDate({ date, overdue }: { date: string | null; overdue: boolean }) {
  if (!date) return <span className="muted">Not set</span>;
  return (
    <span className={overdue ? 'overdue-text' : ''}>
      {date}
      {overdue && <span className="badge overdue">⚠ OVERDUE</span>}
    </span>
  );
}

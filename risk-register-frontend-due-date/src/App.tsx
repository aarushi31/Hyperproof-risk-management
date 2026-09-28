import { useState } from 'react';
import { Dashboard } from './components/Dashboard';
import { RiskDetail } from './components/RiskDetail';
import { RiskForm } from './components/RiskForm';

type View = { name: 'list' } | { name: 'detail'; id: number } | { name: 'form'; id?: number };

export default function App() {
  const [view, setView] = useState<View>({ name: 'list' });
  return (
    <div className="app">
      <header><h1 onClick={() => setView({ name: 'list' })}>Risk Register</h1></header>
      <main>
        {view.name === 'list' && <Dashboard onOpen={(id) => setView({ name: 'detail', id })} onNew={() => setView({ name: 'form' })} />}
        {view.name === 'detail' && (
          <RiskDetail key={view.id} id={view.id} onBack={() => setView({ name: 'list' })} onEdit={() => setView({ name: 'form', id: view.id })} />
        )}
        {view.name === 'form' && (
          <RiskForm id={view.id} onDone={(id) => setView({ name: 'detail', id })}
            onCancel={() => setView(view.id === undefined ? { name: 'list' } : { name: 'detail', id: view.id })} />
        )}
      </main>
    </div>
  );
}

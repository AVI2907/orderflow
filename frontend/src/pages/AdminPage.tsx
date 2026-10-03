import { useEffect, useState } from 'react';
import { catalogApi } from '../api/client';
import { useAuth } from '../context/AuthContext';

interface SellerRow {
  id: string;
  email: string;
  businessName: string;
  status: 'PENDING' | 'APPROVED' | 'SUSPENDED';
  createdAt: string;
}

const STATUS_ORDER = { PENDING: 0, APPROVED: 1, SUSPENDED: 2 };

export function AdminPage() {
  const { role } = useAuth();
  const [sellers, setSellers] = useState<SellerRow[] | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function load() {
    catalogApi
      .get<SellerRow[]>('/sellers')
      // Pending sellers first, since they need action
      .then((res) => setSellers([...res.data].sort((a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status])))
      .catch(() => setError('Could not load sellers.'));
  }

  useEffect(() => {
    if (role === 'ADMIN') load();
  }, [role]);

  async function setStatus(seller: SellerRow, status: SellerRow['status']) {
    if (status === 'SUSPENDED' && !window.confirm(`Suspend ${seller.businessName}? Their products will be hidden from the store.`)) return;
    setError(null);
    setBusyId(seller.id);
    try {
      await catalogApi.patch(`/sellers/${seller.id}/status`, { status });
      load();
    } catch {
      setError('Could not update that seller.');
    } finally {
      setBusyId(null);
    }
  }

  if (role !== 'ADMIN') return <p>This page is for administrators only.</p>;
  if (error && !sellers) return <p style={{ color: '#c0392b' }}>{error}</p>;
  if (!sellers) return <p>Loading sellers...</p>;

  const pendingCount = sellers.filter((s) => s.status === 'PENDING').length;

  return (
    <div>
      <h1>Admin: Sellers</h1>
      <p className="muted">
        {sellers.length} seller{sellers.length === 1 ? '' : 's'} · {pendingCount} waiting for approval
      </p>
      {error && <p style={{ color: '#c0392b' }}>{error}</p>}

      <div className="table-scroll">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Business</th>
              <th>Email</th>
              <th>Joined</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {sellers.map((s) => (
              <tr key={s.id}>
                <td>{s.businessName}</td>
                <td>{s.email}</td>
                <td>{new Date(s.createdAt).toLocaleDateString()}</td>
                <td><span className={`status-pill status-${s.status.toLowerCase()}`}>{s.status}</span></td>
                <td className="admin-actions">
                  {s.status !== 'APPROVED' && (
                    <button className="btn-primary" disabled={busyId !== null} onClick={() => setStatus(s, 'APPROVED')}>
                      {s.status === 'SUSPENDED' ? 'Reactivate' : 'Approve'}
                    </button>
                  )}
                  {s.status !== 'SUSPENDED' && (
                    <button className="btn-secondary btn-danger" disabled={busyId !== null} onClick={() => setStatus(s, 'SUSPENDED')}>
                      Suspend
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

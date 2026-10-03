import { useEffect, useState } from 'react';
import { orderApi } from '../api/client';
import { ShippingAddressView, type ShippingAddress } from '../components/ShippingAddressView';
import { CARRIER_NAMES, TrackingTimeline } from '../components/TrackingTimeline';
import { useAuth } from '../context/AuthContext';

interface OrderItem {
  id: string;
  productName: string;
  unitPrice: number;
  quantity: number;
}

interface SellerOrder {
  id: string;
  sellerId: string;
  subtotal: number;
  status: string;
  items: OrderItem[];
  shippingAddress?: ShippingAddress | null;
  carrier?: string | null;
  trackingNumber?: string | null;
  trackingUrl?: string | null;
  paidAt?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
  cancelledAt?: string | null;
  cancelledBy?: string | null;
  refundedAmount?: number | null;
  createdAt: string;
}

interface ShipForm {
  carrier: string;
  trackingNumber: string;
}

export function MySellerOrdersPage() {
  const { sellerId } = useAuth();
  const [orders, setOrders] = useState<SellerOrder[]>([]);
  const [forms, setForms] = useState<Record<string, ShipForm>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function loadOrders() {
    if (!sellerId) return;
    orderApi
      .get<SellerOrder[]>(`/seller-orders/seller/${sellerId}`)
      .then((res) =>
        setOrders([...res.data].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()))
      )
      .catch(() => setError('Failed to load your orders'));
  }

  useEffect(loadOrders, [sellerId]);

  function formFor(id: string): ShipForm {
    return forms[id] ?? { carrier: 'UPS', trackingNumber: '' };
  }

  function updateForm(id: string, field: keyof ShipForm, value: string) {
    setForms((prev) => ({ ...prev, [id]: { ...formFor(id), [field]: value } }));
  }

  async function updateStatus(id: string, body: Record<string, string>) {
    setError(null);
    setBusyId(id);
    try {
      await orderApi.patch(`/seller-orders/${id}/status`, body);
      loadOrders();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to update order status.');
    } finally {
      setBusyId(null);
    }
  }

  async function cancelOrder(o: SellerOrder) {
    if (!window.confirm(`Cancel this order? The buyer will be refunded $${o.subtotal.toFixed(2)} and the items go back into your stock.`)) return;
    setError(null);
    setBusyId(o.id);
    try {
      await orderApi.post(`/seller-orders/${o.id}/cancel`);
      loadOrders();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Could not cancel this order.');
    } finally {
      setBusyId(null);
    }
  }

  if (!sellerId) {
    return <p>Log in as a seller to view your orders.</p>;
  }

  return (
    <div>
      <h1>My Orders</h1>
      {error && <p style={{ color: '#c0392b' }}>{error}</p>}
      {orders.length === 0 && <p>No orders yet.</p>}
      {orders.map((o) => {
        const form = formFor(o.id);
        const busy = busyId === o.id;
        return (
          <div key={o.id} className="order-box">
            <div className="order-card-header">
              <span className="muted">{new Date(o.createdAt).toLocaleString()}</span>
            </div>

            <TrackingTimeline
              info={{
                status: o.status,
                placedAt: o.createdAt,
                paidAt: o.paidAt,
                shippedAt: o.shippedAt,
                deliveredAt: o.deliveredAt,
                carrier: o.carrier,
                trackingNumber: o.trackingNumber,
                trackingUrl: o.trackingUrl,
                cancelledAt: o.cancelledAt,
                cancelledBy: o.cancelledBy,
                refundedAmount: o.refundedAmount,
              }}
            />

            <ShippingAddressView address={o.shippingAddress} />
            {o.items.map((item) => (
              <p key={item.id}>
                {item.productName} × {item.quantity} — ${(item.unitPrice * item.quantity).toFixed(2)}
              </p>
            ))}
            <p className="muted">Subtotal: ${o.subtotal.toFixed(2)}</p>

            {o.status === 'PLACED' && <p className="muted">Waiting for the buyer to pay.</p>}
            {o.status === 'PAID' && (
              <button className="btn-secondary btn-danger" disabled={busy} onClick={() => cancelOrder(o)} style={{ marginTop: '0.5rem' }}>
                Cancel and refund
              </button>
            )}

            {o.status === 'PAID' && (
              <form
                className="ship-form"
                onSubmit={(e) => {
                  e.preventDefault();
                  updateStatus(o.id, { status: 'SHIPPED', carrier: form.carrier, trackingNumber: form.trackingNumber });
                }}
              >
                <select value={form.carrier} onChange={(e) => updateForm(o.id, 'carrier', e.target.value)}>
                  {Object.entries(CARRIER_NAMES).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
                <input
                  placeholder="Tracking number"
                  value={form.trackingNumber}
                  onChange={(e) => updateForm(o.id, 'trackingNumber', e.target.value)}
                  required
                  minLength={4}
                  maxLength={64}
                />
                <button type="submit" className="btn-primary" disabled={busy}>
                  {busy ? 'Saving...' : 'Mark as shipped'}
                </button>
              </form>
            )}

            {o.status === 'SHIPPED' && (
              <button className="btn-primary" disabled={busy} onClick={() => updateStatus(o.id, { status: 'DELIVERED' })} style={{ marginTop: '0.5rem' }}>
                {busy ? 'Saving...' : 'Mark as delivered'}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

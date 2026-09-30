import { useEffect, useState } from 'react';
import { orderApi } from '../api/client';
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
}

const NEXT_STATUS: Record<string, string | null> = {
  PLACED: null,
  PAID: 'SHIPPED',
  SHIPPED: 'DELIVERED',
  DELIVERED: null,
  CANCELLED: null,
};

export function MySellerOrdersPage() {
  const { sellerId } = useAuth();
  const [orders, setOrders] = useState<SellerOrder[]>([]);
  const [error, setError] = useState<string | null>(null);

  function loadOrders() {
    if (!sellerId) return;
    orderApi
      .get<SellerOrder[]>(`/seller-orders/seller/${sellerId}`)
      .then((res) => setOrders(res.data))
      .catch(() => setError('Failed to load your orders'));
  }

  useEffect(loadOrders, [sellerId]);

  async function advanceStatus(orderId: string, nextStatus: string) {
    setError(null);
    try {
      await orderApi.patch(`/seller-orders/${orderId}/status`, { status: nextStatus });
      loadOrders();
    } catch {
      setError('Failed to update order status.');
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
        const next = NEXT_STATUS[o.status];
        return (
          <div key={o.id} className="order-box">
            <p>Status: <span className="status-pill">{o.status}</span></p>
            {o.items.map((item) => (
              <p key={item.id}>{item.productName} x{item.quantity} — ${(item.unitPrice * item.quantity).toFixed(2)}</p>
            ))}
            <p>Subtotal: ${o.subtotal.toFixed(2)}</p>
            {next && (
              <button className="btn-primary" onClick={() => advanceStatus(o.id, next)} style={{ marginTop: '0.5rem' }}>
                Mark as {next}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
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
  status: string;
  items: OrderItem[];
  shippedAt?: string | null;
  deliveredAt?: string | null;
}

interface Order {
  id: string;
  totalAmount: number;
  overallStatus: string;
  createdAt: string;
  sellerOrders: SellerOrder[];
}

const STATUS_LABELS: Record<string, string> = {
  PLACED: 'Awaiting payment',
  PAID: 'Paid',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

function shortDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

/** e.g. "Shipped Oct 3" or "Delivered Oct 5", using the latest date across the order's packages. */
function statusText(order: Order): string {
  const label = STATUS_LABELS[order.overallStatus] ?? order.overallStatus;
  const dates = order.sellerOrders
    .map((so) => (order.overallStatus === 'DELIVERED' ? so.deliveredAt : order.overallStatus === 'SHIPPED' ? so.shippedAt : null))
    .filter((d): d is string => !!d)
    .sort();
  return dates.length ? `${label} ${shortDate(dates[dates.length - 1])}` : label;
}

export function MyOrdersPage() {
  const { buyerId } = useAuth();
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buyerId) return;
    orderApi
      .get<Order[]>(`/orders/buyer/${buyerId}`)
      .then((res) =>
        setOrders(
          [...res.data].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
        )
      )
      .catch(() => setError('Could not load your orders. Please try again.'));
  }, [buyerId]);

  if (!buyerId) return <p>Log in with a buyer account to see your orders.</p>;
  if (error) return <p style={{ color: '#c0392b' }}>{error}</p>;
  if (!orders) return <p>Loading your orders...</p>;

  return (
    <div>
      <h1>My Orders</h1>

      {orders.length === 0 && (
        <p>
          You haven't placed any orders yet. <Link to="/">Start shopping</Link>
        </p>
      )}

      {orders.map((order) => {
        const items = order.sellerOrders.flatMap((so) => so.items);
        const shown = items.slice(0, 3);
        const more = items.length - shown.length;
        const awaitingPayment = order.overallStatus === 'PLACED';

        return (
          <div key={order.id} className="order-box">
            <div className="order-card-header">
              <span className="muted">
                {new Date(order.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })}
              </span>
              <span className="status-pill">{statusText(order)}</span>
            </div>

            <ul className="order-card-items">
              {shown.map((item) => (
                <li key={item.id}>
                  {item.productName} × {item.quantity}
                </li>
              ))}
              {more > 0 && <li className="muted">+ {more} more item{more === 1 ? '' : 's'}</li>}
            </ul>

            <div className="order-card-footer">
              <strong>${order.totalAmount.toFixed(2)}</strong>
              <span className="order-card-links">
                {awaitingPayment && <Link to={`/checkout/${order.id}`}>Complete payment</Link>}
                <Link to={`/order/${order.id}`}>View details</Link>
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

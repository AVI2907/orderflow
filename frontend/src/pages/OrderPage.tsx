import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { orderApi } from '../api/client';
import { ShippingAddressView, type ShippingAddress } from '../components/ShippingAddressView';
import { TrackingTimeline } from '../components/TrackingTimeline';

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
  carrier?: string | null;
  trackingNumber?: string | null;
  trackingUrl?: string | null;
  paidAt?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
  createdAt: string;
}

interface Order {
  id: string;
  totalAmount: number;
  overallStatus: string;
  sellerOrders: SellerOrder[];
  shippingAddress?: ShippingAddress | null;
  createdAt: string;
}

const POLL_INTERVAL_MS = 2000;
const MAX_POLLS = 15;

export function OrderPage() {
  const { orderId } = useParams();
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let polls = 0;
    let timer: number | undefined;

    async function load() {
      try {
        const res = await orderApi.get<Order>(`/orders/${orderId}`);
        if (cancelled) return;
        setOrder(res.data);
        // Payment is confirmed by Stripe's webhook, which can take a moment to arrive
        if (res.data.overallStatus === 'PLACED' && polls < MAX_POLLS) {
          polls++;
          timer = window.setTimeout(load, POLL_INTERVAL_MS);
        }
      } catch {
        if (!cancelled) setError('Order not found');
      }
    }

    load();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [orderId]);

  if (error) return <p style={{ color: '#c0392b' }}>{error}</p>;
  if (!order) return <p>Loading...</p>;

  const awaitingPayment = order.overallStatus === 'PLACED';
  const multiplePackages = order.sellerOrders.length > 1;

  return (
    <div>
      <p><Link to="/my-orders">← My Orders</Link></p>
      <h1>{awaitingPayment ? 'Confirming payment…' : 'Your order'}</h1>
      {awaitingPayment && (
        <p>
          This usually takes a few seconds. If you haven't paid yet,{' '}
          <Link to={`/checkout/${order.id}`}>complete your payment</Link>.
        </p>
      )}
      <p className="muted">
        Placed {new Date(order.createdAt).toLocaleString()} · Order {order.id}
      </p>
      <p style={{ fontWeight: 700, fontSize: '1.2rem' }}>Total: ${order.totalAmount.toFixed(2)}</p>
      <ShippingAddressView address={order.shippingAddress} />
      {multiplePackages && (
        <p className="muted">This order ships in {order.sellerOrders.length} packages from different sellers.</p>
      )}

      {order.sellerOrders.map((so, i) => (
        <div key={so.id} className="order-box">
          {multiplePackages && <h3>Package {i + 1}</h3>}
          <TrackingTimeline
            info={{
              status: so.status,
              placedAt: so.createdAt,
              paidAt: so.paidAt,
              shippedAt: so.shippedAt,
              deliveredAt: so.deliveredAt,
              carrier: so.carrier,
              trackingNumber: so.trackingNumber,
              trackingUrl: so.trackingUrl,
            }}
          />
          {so.items.map((item) => (
            <p key={item.id}>
              {item.productName} × {item.quantity} — ${(item.unitPrice * item.quantity).toFixed(2)}
            </p>
          ))}
          <p className="muted">Subtotal: ${so.subtotal.toFixed(2)}</p>
        </div>
      ))}
    </div>
  );
}

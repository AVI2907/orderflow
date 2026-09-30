import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { orderApi } from '../api/client';
import { ShippingAddressView, type ShippingAddress } from '../components/ShippingAddressView';

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

interface Order {
  id: string;
  totalAmount: number;
  overallStatus: string;
  sellerOrders: SellerOrder[];
  shippingAddress?: ShippingAddress | null;
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

  return (
    <div>
      <h1>{awaitingPayment ? 'Confirming payment…' : 'Order Confirmed'}</h1>
      {awaitingPayment && (
        <p>
          This usually takes a few seconds. If you haven't paid yet,{' '}
          <Link to={`/checkout/${order.id}`}>complete your payment</Link>.
        </p>
      )}
      <p>Order ID: {order.id}</p>
      <p>Overall status: <span className="status-pill">{order.overallStatus}</span></p>
      <p style={{ fontWeight: 700, fontSize: '1.2rem' }}>Total: ${order.totalAmount.toFixed(2)}</p>
      <ShippingAddressView address={order.shippingAddress} />

      {order.sellerOrders.map((so) => (
        <div key={so.id} className="order-box">
          <p>Seller sub-order — status: <span className="status-pill">{so.status}</span></p>
          {so.items.map((item) => (
            <p key={item.id}>{item.productName} x{item.quantity} — ${(item.unitPrice * item.quantity).toFixed(2)}</p>
          ))}
          <p>Subtotal: ${so.subtotal.toFixed(2)}</p>
        </div>
      ))}
    </div>
  );
}

import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { orderApi } from '../api/client';

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
}

export function OrderPage() {
  const { orderId } = useParams();
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    orderApi
      .get<Order>(`/orders/${orderId}`)
      .then((res) => setOrder(res.data))
      .catch(() => setError('Order not found'));
  }, [orderId]);

  if (error) return <p style={{ color: '#c0392b' }}>{error}</p>;
  if (!order) return <p>Loading...</p>;

  return (
    <div>
      <h1>Order Confirmed</h1>
      <p>Order ID: {order.id}</p>
      <p>Overall status: <span className="status-pill">{order.overallStatus}</span></p>
      <p style={{ fontWeight: 700, fontSize: '1.2rem' }}>Total: ${order.totalAmount.toFixed(2)}</p>

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

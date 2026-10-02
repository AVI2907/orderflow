import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { loadStripe } from '@stripe/stripe-js';
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { orderApi } from '../api/client';
import { ShippingAddressView, type ShippingAddress } from '../components/ShippingAddressView';
import { useAuth } from '../context/AuthContext';

interface OrderItem {
  id: string;
  productName: string;
  unitPrice: number;
  quantity: number;
}

interface Order {
  id: string;
  totalAmount: number;
  overallStatus: string;
  sellerOrders: { id: string; items: OrderItem[] }[];
  shippingAddress?: ShippingAddress | null;
}

const publishableKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as string | undefined;
// Loaded once per page load, outside the component, as Stripe recommends
const stripePromise = publishableKey ? loadStripe(publishableKey) : null;

function CheckoutForm({ orderId, total }: { orderId: string; total: number }) {
  const stripe = useStripe();
  const elements = useElements();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!stripe || !elements) return;
    setError(null);
    setPaying(true);

    // Card details go straight from the browser to Stripe, never to our servers.
    // Stripe shows 3D Secure verification itself if the bank requires it.
    const result = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: `${window.location.origin}/order/${orderId}` },
      redirect: 'if_required',
    });

    if (result.error) {
      setError(result.error.message ?? 'Payment failed. Please try again.');
      setPaying(false);
      return;
    }
    // Stripe accepted the payment; the webhook marks the order PAID on our side
    navigate(`/order/${orderId}`);
  }

  return (
    <form onSubmit={handleSubmit} className="payment-form">
      <PaymentElement />
      {error && <p style={{ color: '#c0392b' }}>{error}</p>}
      <button type="submit" className="btn-primary" disabled={!stripe || paying}>
        {paying ? 'Processing...' : `Pay $${total.toFixed(2)}`}
      </button>
    </form>
  );
}

export function PaymentPage() {
  const { orderId } = useParams();
  const { buyerId } = useAuth();
  const navigate = useNavigate();
  const [order, setOrder] = useState<Order | null>(null);
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!buyerId) {
      navigate('/login');
      return;
    }
    async function load() {
      try {
        const res = await orderApi.get<Order>(`/orders/${orderId}`);
        if (res.data.overallStatus !== 'PLACED') {
          navigate(`/order/${orderId}`, { replace: true });
          return;
        }
        setOrder(res.data);
        const intent = await orderApi.post<{ clientSecret: string }>(`/orders/${orderId}/payment-intent`, {});
        setClientSecret(intent.data.clientSecret);
      } catch {
        setLoadError('Could not start payment. Please try again.');
      }
    }
    load();
  }, [orderId, buyerId, navigate]);

  if (!stripePromise) return <p style={{ color: '#c0392b' }}>Payments are not configured.</p>;
  if (loadError) return <p style={{ color: '#c0392b' }}>{loadError}</p>;
  if (!order || !clientSecret) return <p>Loading...</p>;

  const items = order.sellerOrders.flatMap((so) => so.items);

  return (
    <div className="payment-layout">
      <section className="order-box">
        <h2>Order summary</h2>
        {items.map((item) => (
          <div key={item.id} className="summary-row">
            <span>{item.productName} × {item.quantity}</span>
            <span>${(item.unitPrice * item.quantity).toFixed(2)}</span>
          </div>
        ))}
        <div className="summary-row summary-total">
          <span>Total</span>
          <span>${order.totalAmount.toFixed(2)}</span>
        </div>
        <ShippingAddressView address={order.shippingAddress} />
      </section>

      <section className="order-box">
        <h2>Payment</h2>
        <p className="demo-banner">
          Test mode — no real charges. Use card <strong>4242 4242 4242 4242</strong>, any future
          date and any CVC. Please don't enter a real card.
        </p>
        <Elements stripe={stripePromise} options={{ clientSecret }}>
          <CheckoutForm orderId={order.id} total={order.totalAmount} />
        </Elements>
      </section>
    </div>
  );
}

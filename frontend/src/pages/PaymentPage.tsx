import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { orderApi } from '../api/client';
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
}

// Same test numbers Stripe uses, so the flow carries over when real payments are added
const DECLINE_TEST_CARD = '4000000000000002';

function luhnValid(digits: string) {
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let d = Number(digits[i]);
    if (double) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
    double = !double;
  }
  return sum % 10 === 0;
}

function formatCardNumber(value: string) {
  return value.replace(/\D/g, '').slice(0, 16).replace(/(.{4})/g, '$1 ').trim();
}

function formatExpiry(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 4);
  return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
}

function validate(name: string, cardNumber: string, expiry: string, cvc: string): string | null {
  const digits = cardNumber.replace(/\s/g, '');
  if (!name.trim()) return 'Enter the name on the card.';
  if (digits.length !== 16 || !luhnValid(digits)) return 'Enter a valid 16-digit card number.';

  const match = expiry.match(/^(\d{2})\/(\d{2})$/);
  if (!match) return 'Enter the expiry date as MM/YY.';
  const month = Number(match[1]);
  const year = 2000 + Number(match[2]);
  if (month < 1 || month > 12) return 'Enter a valid expiry month.';
  // A card is valid through the last day of its expiry month
  if (new Date(year, month, 1) <= new Date()) return 'This card has expired.';

  if (!/^\d{3,4}$/.test(cvc)) return 'Enter a valid CVC.';
  return null;
}

export function PaymentPage() {
  const { orderId } = useParams();
  const { buyerId } = useAuth();
  const navigate = useNavigate();

  const [order, setOrder] = useState<Order | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [cardNumber, setCardNumber] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvc, setCvc] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);

  useEffect(() => {
    if (!buyerId) {
      navigate('/login');
      return;
    }
    orderApi
      .get<Order>(`/orders/${orderId}`)
      .then((res) => {
        // Already paid (or cancelled) — nothing to pay for, show the order instead
        if (res.data.overallStatus !== 'PLACED') {
          navigate(`/orders/${orderId}`, { replace: true });
          return;
        }
        setOrder(res.data);
      })
      .catch(() => setLoadError('Order not found.'));
  }, [orderId, buyerId, navigate]);

  async function handlePay(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    const problem = validate(name, cardNumber, expiry, cvc);
    if (problem) {
      setError(problem);
      return;
    }

    setPaying(true);
    try {
      // Demo mode: card details never leave the browser. Only the simulated outcome is sent.
      const simulateFailure = cardNumber.replace(/\s/g, '') === DECLINE_TEST_CARD;
      await orderApi.post(`/orders/${orderId}/pay`, { simulateFailure });
      navigate(`/orders/${orderId}`);
    } catch (err: any) {
      const status = err?.response?.status;
      if (status === 402) {
        setError('Your card was declined. Please try a different card.');
      } else if (status === 409) {
        navigate(`/orders/${orderId}`);
      } else {
        setError('Payment failed. Please try again.');
      }
    } finally {
      setPaying(false);
    }
  }

  if (loadError) return <p style={{ color: '#c0392b' }}>{loadError}</p>;
  if (!order) return <p>Loading...</p>;

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
      </section>

      <section className="order-box">
        <h2>Payment</h2>
        <p className="demo-banner">
          Demo mode — no real charge is made and card details are not sent anywhere.
          Use <strong>4242 4242 4242 4242</strong> with any future date and any CVC.
          Use 4000 0000 0000 0002 to see a declined payment. Please don't enter a real card.
        </p>

        <form onSubmit={handlePay} className="payment-form" noValidate>
          <label>
            Name on card
            <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="cc-name" />
          </label>
          <label>
            Card number
            <input
              value={cardNumber}
              onChange={(e) => setCardNumber(formatCardNumber(e.target.value))}
              inputMode="numeric"
              placeholder="1234 5678 9012 3456"
              autoComplete="cc-number"
            />
          </label>
          <div className="payment-row">
            <label>
              Expiry
              <input
                value={expiry}
                onChange={(e) => setExpiry(formatExpiry(e.target.value))}
                inputMode="numeric"
                placeholder="MM/YY"
                autoComplete="cc-exp"
              />
            </label>
            <label>
              CVC
              <input
                value={cvc}
                onChange={(e) => setCvc(e.target.value.replace(/\D/g, '').slice(0, 4))}
                inputMode="numeric"
                placeholder="123"
                autoComplete="cc-csc"
              />
            </label>
          </div>

          {error && <p style={{ color: '#c0392b' }}>{error}</p>}

          <button type="submit" className="btn-primary" disabled={paying}>
            {paying ? 'Processing...' : `Pay $${order.totalAmount.toFixed(2)}`}
          </button>
        </form>
      </section>
    </div>
  );
}

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { catalogApi, orderApi } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

const EMPTY_ADDRESS = {
  fullName: '',
  line1: '',
  line2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'United States',
};
type Address = typeof EMPTY_ADDRESS;
const REQUIRED: (keyof Address)[] = ['fullName', 'line1', 'city', 'state', 'postalCode', 'country'];

export function CartPage() {
  const { items, removeItem, clearCart, total } = useCart();
  const { buyerId } = useAuth();
  const navigate = useNavigate();
  const [address, setAddress] = useState<Address>(EMPTY_ADDRESS);
  const [error, setError] = useState<string | null>(null);
  const [placing, setPlacing] = useState(false);
  const [profile, setProfile] = useState<{ name: string; phone: string | null } | null>(null);
  const [saveAsDefault, setSaveAsDefault] = useState(false);
  const [prefilledFrom, setPrefilledFrom] = useState<'profile' | 'last-order' | null>(null);

  // Pre-fill the address: the profile's default first, otherwise the last order's address
  useEffect(() => {
    if (!buyerId) return;
    let cancelled = false;
    const toForm = (a: any): Address => ({
      fullName: a.fullName ?? '', line1: a.line1 ?? '', line2: a.line2 ?? '', city: a.city ?? '',
      state: a.state ?? '', postalCode: a.postalCode ?? '', country: a.country ?? 'United States',
    });
    // Only fill an untouched form, so nothing the buyer already typed gets overwritten
    const fill = (a: Address) =>
      setAddress((cur) => (JSON.stringify(cur) === JSON.stringify(EMPTY_ADDRESS) ? a : cur));
    (async () => {
      try {
        const res = await catalogApi.get('/buyers/me');
        if (cancelled) return;
        setProfile({ name: res.data.name, phone: res.data.phone });
        if (res.data.defaultAddress) {
          fill(toForm(res.data.defaultAddress));
          setPrefilledFrom('profile');
          return;
        }
        setSaveAsDefault(true); // no default yet: offer to save this one
      } catch {
        // profile unavailable: fall back to the last order
      }
      try {
        const last = await orderApi.get('/orders/last-address');
        if (!cancelled && last.status === 200 && last.data?.line1) {
          fill(toForm(last.data));
          setPrefilledFrom('last-order');
        }
      } catch {
        // nothing saved: the buyer types the address
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [buyerId]);

  function field(name: keyof Address) {
    return {
      value: address[name],
      onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
        setAddress((current) => ({ ...current, [name]: e.target.value })),
    };
  }

  async function handleCheckout(e: React.FormEvent) {
    e.preventDefault();
    if (!buyerId) {
      navigate('/login');
      return;
    }
    if (REQUIRED.some((f) => !address[f].trim())) {
      setError('Please fill in all required delivery address fields.');
      return;
    }

    setError(null);
    setPlacing(true);

    // Only product IDs and quantities: the server looks up prices, names and sellers itself
    const orderItems = items.map((i) => ({ productId: i.product.id, quantity: i.quantity }));

    try {
      const response = await orderApi.post('/orders', { items: orderItems, shippingAddress: address });
      const orderId = response.data.id;
      if (saveAsDefault && profile) {
        // Best effort: the order is already placed, so this must never block payment
        await catalogApi.put('/buyers/me', { name: profile.name, phone: profile.phone, defaultAddress: address }).catch(() => {});
      }
      clearCart();
      navigate(`/checkout/${orderId}`);
    } catch (err: any) {
      const status = err?.response?.status;
      const serverMessage = err?.response?.data?.message;
      if ((status === 409 || status === 503) && serverMessage) {
        setError(serverMessage);
      } else if (status === 400) {
        setError('Please check your delivery address and try again.');
      } else {
        setError('Could not place your order. Please try again.');
      }
    } finally {
      setPlacing(false);
    }
  }

  if (items.length === 0) return <p>Your cart is empty.</p>;

  return (
    <div>
      <h1>Cart</h1>
      {items.map((item) => (
        <div key={item.product.id} className="cart-row">
          <span>{item.product.name} x{item.quantity}</span>
          <span>
            ${(item.product.price * item.quantity).toFixed(2)}
            <button className="btn-secondary" onClick={() => removeItem(item.product.id)} style={{ marginLeft: '1rem' }}>Remove</button>
          </span>
        </div>
      ))}
      <p style={{ fontWeight: 700, fontSize: '1.2rem', marginTop: '1.5rem' }}>Total: ${total.toFixed(2)}</p>

      <form onSubmit={handleCheckout} className="checkout-form">
        <h2>Delivery address</h2>
        {prefilledFrom && (
          <p className="muted">
            Filled in from {prefilledFrom === 'profile' ? 'your default address' : 'your last order'}. You can change it.
          </p>
        )}
        <label>
          Full name
          <input {...field('fullName')} autoComplete="name" required />
        </label>
        <label>
          Address line 1
          <input {...field('line1')} autoComplete="address-line1" placeholder="Street address" required />
        </label>
        <label>
          Address line 2 (optional)
          <input {...field('line2')} autoComplete="address-line2" placeholder="Apartment, suite, unit" />
        </label>
        <div className="address-grid">
          <label>
            City
            <input {...field('city')} autoComplete="address-level2" required />
          </label>
          <label>
            State
            <input {...field('state')} autoComplete="address-level1" required />
          </label>
          <label>
            ZIP / postal code
            <input {...field('postalCode')} autoComplete="postal-code" required />
          </label>
          <label>
            Country
            <input {...field('country')} autoComplete="country-name" required />
          </label>
        </div>

        {profile && (
          <label className="checkbox-row">
            <input type="checkbox" checked={saveAsDefault} onChange={(e) => setSaveAsDefault(e.target.checked)} />
            Save as my default address
          </label>
        )}
        {!buyerId && <p style={{ color: '#666', fontSize: '0.9rem' }}>You'll need to log in as a buyer to check out.</p>}
        {error && <p style={{ color: '#c0392b' }}>{error}</p>}
        <button type="submit" className="btn-primary" disabled={placing}>
          {placing ? 'Placing order...' : 'Proceed to payment'}
        </button>
      </form>
    </div>
  );
}

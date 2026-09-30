import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { orderApi } from '../api/client';
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

    // Group cart items by seller — matches the backend's multi-vendor order shape
    const bySeller = new Map<string, typeof items>();
    for (const item of items) {
      const sellerId = item.product.seller.id;
      if (!bySeller.has(sellerId)) bySeller.set(sellerId, []);
      bySeller.get(sellerId)!.push(item);
    }

    const sellerOrders = Array.from(bySeller.entries()).map(([sellerId, sellerItems]) => ({
      sellerId,
      items: sellerItems.map((i) => ({
        productId: i.product.id,
        productName: i.product.name,
        unitPrice: i.product.price,
        quantity: i.quantity,
      })),
    }));

    try {
      const response = await orderApi.post('/orders', { sellerOrders, shippingAddress: address });
      const orderId = response.data.id;
      clearCart();
      navigate(`/checkout/${orderId}`);
    } catch (err: any) {
      setError(
        err?.response?.status === 400
          ? 'Please check your delivery address and try again.'
          : 'Could not place your order. Please try again.'
      );
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

        {!buyerId && <p style={{ color: '#666', fontSize: '0.9rem' }}>You'll need to log in as a buyer to check out.</p>}
        {error && <p style={{ color: '#c0392b' }}>{error}</p>}
        <button type="submit" className="btn-primary" disabled={placing}>
          {placing ? 'Placing order...' : 'Proceed to payment'}
        </button>
      </form>
    </div>
  );
}

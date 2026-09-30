import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { orderApi } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

export function CartPage() {
  const { items, removeItem, clearCart, total } = useCart();
  const { buyerId } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [placing, setPlacing] = useState(false);

  async function handleCheckout() {
    if (!buyerId) {
      navigate('/login');
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
      // Step 1: create the order (status PLACED). Payment happens on the next page.
      const response = await orderApi.post('/orders', { sellerOrders });
      const orderId = response.data.id;
      clearCart();
      navigate(`/checkout/${orderId}`);
    } catch {
      setError('Could not place your order. Please try again.');
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
      {!buyerId && <p style={{ color: '#666', fontSize: '0.9rem' }}>You'll need to log in as a buyer to check out.</p>}
      {error && <p style={{ color: '#c0392b' }}>{error}</p>}
      <button className="btn-primary" onClick={handleCheckout} disabled={placing} style={{ marginTop: '1rem' }}>
        {placing ? 'Placing order...' : 'Proceed to payment'}
      </button>
    </div>
  );
}

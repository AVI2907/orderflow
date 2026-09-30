import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { useCart } from '../context/CartContext';
import type { Product } from '../types';

export function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const toastTimer = useRef<number | undefined>(undefined);
  const { addItem } = useCart();

  useEffect(() => {
    catalogApi
      .get<Product[]>('/products')
      .then((res) => setProducts(res.data))
      .catch(() => setError('Failed to load products'))
      .finally(() => setLoading(false));
  }, []);

  // Clear any pending timer if the page unmounts
  useEffect(() => () => window.clearTimeout(toastTimer.current), []);

  function handleAdd(product: Product) {
    addItem(product, 1);
    setToast(`${product.name} added to cart`);
    // Restart the timer so rapid clicks keep the message visible
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2500);
  }

  if (loading) return <p>Loading products...</p>;
  if (error) return <p style={{ color: '#c0392b' }}>{error}</p>;

  return (
    <div>
      <div className="hero">
        <span className="script">Curated finds</span>
        <h1>Shop the marketplace</h1>
      </div>

      {products.length === 0 && <p>No products yet.</p>}
      <div className="product-grid">
        {products.map((product) => (
          <div key={product.id} className="product-card">
            <h3>{product.name}</h3>
            <p className="desc">{product.description}</p>
            <p className="price">${product.price.toFixed(2)}</p>
            <p className="seller">Sold by {product.seller.businessName}</p>
            <button className="btn-primary" onClick={() => handleAdd(product)}>Add to cart</button>
          </div>
        ))}
      </div>

      {toast && (
        <div className="toast" role="status" aria-live="polite">
          <span>✓ {toast}</span>
          <Link to="/cart">View cart</Link>
        </div>
      )}
    </div>
  );
}

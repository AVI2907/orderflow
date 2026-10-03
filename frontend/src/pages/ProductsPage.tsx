import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { useCart } from '../context/CartContext';
import { Stars } from '../components/StarRating';
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

  useEffect(() => () => window.clearTimeout(toastTimer.current), []);

  function handleAdd(product: Product) {
    addItem(product, 1);
    setToast(`${product.name} added to cart`);
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
            <Link to={`/item/${product.id}`} className="product-card-link">
              <div className="product-thumb">
                {product.imageUrl ? <img src={product.imageUrl} alt={product.name} /> : <span>No photo yet</span>}
              </div>
              <h3>{product.name}</h3>
            </Link>
            <p className="rating-line">
              {product.reviewCount > 0 ? (
                <>
                  <Stars value={product.averageRating} /> {product.averageRating.toFixed(1)} ({product.reviewCount})
                </>
              ) : (
                <span className="muted">No reviews yet</span>
              )}
            </p>
            <p className="price">${product.price.toFixed(2)}</p>
            <p className="seller">Sold by {product.seller.businessName}</p>
            <button className="btn-primary" onClick={() => handleAdd(product)} disabled={product.stockQuantity < 1}>
              {product.stockQuantity < 1 ? 'Out of stock' : 'Add to cart'}
            </button>
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

import { useEffect, useState } from 'react';
import { catalogApi } from '../api/client';
import { useCart } from '../context/CartContext';
import type { Product } from '../types';

export function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { addItem } = useCart();

  useEffect(() => {
    catalogApi
      .get<Product[]>('/products')
      .then((res) => setProducts(res.data))
      .catch(() => setError('Failed to load products'))
      .finally(() => setLoading(false));
  }, []);

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
            <button className="btn-primary" onClick={() => addItem(product, 1)}>Add to cart</button>
          </div>
        ))}
      </div>
    </div>
  );
}

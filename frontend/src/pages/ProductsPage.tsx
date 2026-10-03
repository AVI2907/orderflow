import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { useCart } from '../context/CartContext';
import { Stars } from '../components/StarRating';
import type { Product } from '../types';

const SORT_OPTIONS: [string, string][] = [
  ['newest', 'Newest'],
  ['price_asc', 'Price: low to high'],
  ['price_desc', 'Price: high to low'],
  ['rating', 'Top rated'],
  ['name', 'Name A–Z'],
];

export function ProductsPage() {
  // Filters live in the address bar, so the back button, bookmarks and shared links all work
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') ?? '';
  const category = searchParams.get('category') ?? '';
  const sort = searchParams.get('sort') ?? 'newest';
  const inStock = searchParams.get('inStock') === 'true';

  const [searchText, setSearchText] = useState(q);
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const toastTimer = useRef<number | undefined>(undefined);
  const { addItem } = useCart();

  function updateParam(key: string, value: string) {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    setSearchParams(next, { replace: true });
  }

  function clearFilters() {
    setSearchText('');
    setSearchParams({}, { replace: true });
  }

  // Wait until typing pauses before searching
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (searchText.trim() !== q) updateParam('q', searchText.trim());
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchText]);

  // Keep the box in sync when the address changes (e.g. back button or "Clear filters")
  useEffect(() => {
    setSearchText(q);
  }, [q]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    catalogApi
      .get<Product[]>('/products', {
        params: { q: q || undefined, category: category || undefined, sort, inStock: inStock || undefined },
      })
      .then((res) => {
        if (cancelled) return;
        setProducts(res.data);
        setError(null);
      })
      .catch(() => {
        if (!cancelled) setError('Failed to load products');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [q, category, sort, inStock]);

  useEffect(() => {
    catalogApi
      .get<string[]>('/products/categories')
      .then((res) => setCategories(res.data))
      .catch(() => {});
  }, []);

  useEffect(() => () => window.clearTimeout(toastTimer.current), []);

  function handleAdd(product: Product) {
    addItem(product, 1);
    setToast(`${product.name} added to cart`);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2500);
  }

  const filtersActive = !!(q || category || inStock || sort !== 'newest');

  return (
    <div>
      <div className="hero">
        <span className="script">Curated finds</span>
        <h1>Shop the marketplace</h1>
      </div>

      <div className="filters">
        <input
          type="search"
          placeholder="Search products"
          aria-label="Search products"
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          maxLength={100}
        />
        <select aria-label="Category" value={category} onChange={(e) => updateParam('category', e.target.value)}>
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <select
          aria-label="Sort by"
          value={sort}
          onChange={(e) => updateParam('sort', e.target.value === 'newest' ? '' : e.target.value)}
        >
          {SORT_OPTIONS.map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <label className="filter-check">
          <input type="checkbox" checked={inStock} onChange={(e) => updateParam('inStock', e.target.checked ? 'true' : '')} />
          In stock only
        </label>
      </div>

      <p className="muted result-line">
        {loading ? 'Loading…' : `${products.length} product${products.length === 1 ? '' : 's'}`}
        {filtersActive && (
          <>
            {' · '}
            <button type="button" className="link-button" onClick={clearFilters}>Clear filters</button>
          </>
        )}
      </p>

      {error && <p style={{ color: '#c0392b' }}>{error}</p>}
      {!loading && !error && products.length === 0 && (
        <p>{filtersActive ? 'No products match your search.' : 'No products yet.'}</p>
      )}

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

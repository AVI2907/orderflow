import { useEffect, useState } from 'react';
import { catalogApi } from '../api/client';
import { useAuth } from '../context/AuthContext';
import type { Product } from '../types';

export function MyProductsPage() {
  const { sellerId } = useAuth();
  const [products, setProducts] = useState<Product[]>([]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [stock, setStock] = useState('');
  const [category, setCategory] = useState('');
  const [error, setError] = useState<string | null>(null);

  function loadProducts() {
    if (!sellerId) return;
    catalogApi
      .get<Product[]>(`/products/seller/${sellerId}`)
      .then((res) => setProducts(res.data))
      .catch(() => setError('Failed to load your products'));
  }

  useEffect(loadProducts, [sellerId]);

  async function handleAddProduct(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!sellerId) {
      setError('You must be logged in as a seller to add products.');
      return;
    }

    try {
      await catalogApi.post('/products', {
        sellerId,
        name,
        description,
        price: parseFloat(price),
        stockQuantity: parseInt(stock, 10),
        category,
      });
      setName('');
      setDescription('');
      setPrice('');
      setStock('');
      setCategory('');
      loadProducts();
    } catch {
      setError('Failed to add product.');
    }
  }

  if (!sellerId) {
    return <p>Log in as a seller to manage your products.</p>;
  }

  return (
    <div>
      <h1>My Products</h1>

      <form onSubmit={handleAddProduct} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxWidth: 360, margin: '1.5rem 0 2.5rem' }}>
        <h2>Add a product</h2>
        <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} required />
        <input placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
        <input placeholder="Price" type="number" step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} required />
        <input placeholder="Stock quantity" type="number" value={stock} onChange={(e) => setStock(e.target.value)} required />
        <input placeholder="Category" value={category} onChange={(e) => setCategory(e.target.value)} />
        {error && <p style={{ color: '#c0392b' }}>{error}</p>}
        <button type="submit" className="btn-primary">Add product</button>
      </form>

      <h2>Your listings</h2>
      {products.length === 0 && <p>You haven't listed any products yet.</p>}
      {products.map((p) => (
        <div key={p.id} className="order-box">
          <strong>{p.name}</strong> — ${p.price.toFixed(2)} — stock: {p.stockQuantity}
        </div>
      ))}
    </div>
  );
}

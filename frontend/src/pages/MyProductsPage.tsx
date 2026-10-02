import { useEffect, useRef, useState } from 'react';
import { catalogApi } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { IMAGE_ACCEPT, errorMessage, uploadProductImage } from '../utils/uploadImage';
import type { Product } from '../types';

export function MyProductsPage() {
  const { sellerId } = useAuth();
  const [products, setProducts] = useState<Product[]>([]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [stock, setStock] = useState('');
  const [category, setCategory] = useState('');
  const [photo, setPhoto] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyProductId, setBusyProductId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  function loadProducts() {
    if (!sellerId) return;
    catalogApi
      .get<Product[]>(`/products/seller/${sellerId}`)
      .then((res) => setProducts(res.data))
      .catch(() => setError('Failed to load your products'));
  }

  useEffect(loadProducts, [sellerId]);

  // Free the preview image from memory when it changes or the page closes
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  function handlePhotoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setPhoto(file);
    setPreview(file ? URL.createObjectURL(file) : null);
  }

  async function handleAddProduct(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const imageUrl = photo ? await uploadProductImage(photo) : null;
      await catalogApi.post('/products', {
        name,
        description,
        price: parseFloat(price),
        stockQuantity: parseInt(stock, 10),
        category,
        imageUrl,
      });
      setName('');
      setDescription('');
      setPrice('');
      setStock('');
      setCategory('');
      setPhoto(null);
      setPreview(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      loadProducts();
    } catch (err) {
      setError(errorMessage(err, 'Failed to add product.'));
    } finally {
      setSaving(false);
    }
  }

  async function handleChangePhoto(product: Product, file: File) {
    setError(null);
    setBusyProductId(product.id);
    try {
      const imageUrl = await uploadProductImage(file);
      await catalogApi.patch(`/products/${product.id}/image`, { imageUrl });
      loadProducts();
    } catch (err) {
      setError(errorMessage(err, 'Failed to update the photo.'));
    } finally {
      setBusyProductId(null);
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
        <textarea placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} rows={4} />
        <input placeholder="Price" type="number" step="0.01" min="0.01" value={price} onChange={(e) => setPrice(e.target.value)} required />
        <input placeholder="Stock quantity" type="number" min="0" value={stock} onChange={(e) => setStock(e.target.value)} required />
        <input placeholder="Category" value={category} onChange={(e) => setCategory(e.target.value)} />
        <label style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', fontSize: '0.85rem', fontWeight: 600 }}>
          Photo (optional, JPEG/PNG/WebP, up to 5 MB)
          <input ref={fileInputRef} type="file" accept={IMAGE_ACCEPT} onChange={handlePhotoChange} />
        </label>
        {preview && <img src={preview} alt="Selected photo preview" className="photo-preview" />}
        {error && <p style={{ color: '#c0392b' }}>{error}</p>}
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving ? (photo ? 'Uploading photo...' : 'Saving...') : 'Add product'}
        </button>
      </form>

      <h2>Your listings</h2>
      {products.length === 0 && <p>You haven't listed any products yet.</p>}
      {products.map((p) => (
        <div key={p.id} className="order-box listing-row">
          <div className="listing-thumb">
            {p.imageUrl ? <img src={p.imageUrl} alt="" /> : <span>No photo</span>}
          </div>
          <div className="listing-info">
            <strong>{p.name}</strong>
            <div className="muted">${p.price.toFixed(2)} · stock: {p.stockQuantity}</div>
          </div>
          <label className="btn-secondary listing-photo-btn">
            {busyProductId === p.id ? 'Uploading...' : p.imageUrl ? 'Change photo' : 'Add photo'}
            <input
              type="file"
              accept={IMAGE_ACCEPT}
              hidden
              disabled={busyProductId !== null}
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = '';
                if (file) handleChangePhoto(p, file);
              }}
            />
          </label>
        </div>
      ))}
    </div>
  );
}

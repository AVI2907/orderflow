import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { IMAGE_ACCEPT, errorMessage, uploadProductImage } from '../utils/uploadImage';
import type { Product } from '../types';

interface EditForm {
  name: string;
  description: string;
  price: string;
  stock: string;
  category: string;
}

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
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<EditForm>({ name: '', description: '', price: '', stock: '', category: '' });
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

  function startEditing(product: Product) {
    setError(null);
    setEditingId(product.id);
    setEditForm({
      name: product.name,
      description: product.description ?? '',
      price: String(product.price),
      stock: String(product.stockQuantity),
      category: product.category ?? '',
    });
  }

  async function handleSaveEdit(productId: string) {
    setError(null);
    setBusyProductId(productId);
    try {
      await catalogApi.put(`/products/${productId}`, {
        name: editForm.name,
        description: editForm.description,
        price: parseFloat(editForm.price),
        stockQuantity: parseInt(editForm.stock, 10),
        category: editForm.category,
      });
      setEditingId(null);
      loadProducts();
    } catch (err) {
      setError(errorMessage(err, 'Failed to save your changes.'));
    } finally {
      setBusyProductId(null);
    }
  }

  async function handleDelete(product: Product) {
    if (!window.confirm(`Delete "${product.name}"? This also removes its reviews and can't be undone.`)) return;
    setError(null);
    setBusyProductId(product.id);
    try {
      await catalogApi.delete(`/products/${product.id}`);
      loadProducts();
    } catch (err) {
      setError(errorMessage(err, 'Failed to delete the product.'));
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
      {error && <p style={{ color: '#c0392b' }}>{error}</p>}

      <form onSubmit={handleAddProduct} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxWidth: 360, margin: '1.5rem 0 2.5rem' }}>
        <h2>Add a product</h2>
        <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={255} />
        <textarea placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} rows={4} maxLength={2000} />
        <input placeholder="Price" type="number" step="0.01" min="0.01" value={price} onChange={(e) => setPrice(e.target.value)} required />
        <input placeholder="Stock quantity" type="number" min="0" value={stock} onChange={(e) => setStock(e.target.value)} required />
        <input placeholder="Category" value={category} onChange={(e) => setCategory(e.target.value)} maxLength={255} />
        <label style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', fontSize: '0.85rem', fontWeight: 600 }}>
          Photo (optional, JPEG/PNG/WebP, up to 5 MB)
          <input ref={fileInputRef} type="file" accept={IMAGE_ACCEPT} onChange={handlePhotoChange} />
        </label>
        {preview && <img src={preview} alt="Selected photo preview" className="photo-preview" />}
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving ? (photo ? 'Uploading photo...' : 'Saving...') : 'Add product'}
        </button>
      </form>

      <h2>Your listings</h2>
      {products.length === 0 && <p>You haven't listed any products yet.</p>}
      {products.map((p) => {
        const busy = busyProductId === p.id;

        if (editingId === p.id) {
          return (
            <form
              key={p.id}
              className="order-box edit-form"
              onSubmit={(e) => {
                e.preventDefault();
                handleSaveEdit(p.id);
              }}
            >
              <strong>Edit product</strong>
              <label>
                Name
                <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} required maxLength={255} />
              </label>
              <label>
                Description
                <textarea value={editForm.description} onChange={(e) => setEditForm({ ...editForm, description: e.target.value })} rows={4} maxLength={2000} />
              </label>
              <div className="edit-row">
                <label>
                  Price
                  <input type="number" step="0.01" min="0.01" value={editForm.price} onChange={(e) => setEditForm({ ...editForm, price: e.target.value })} required />
                </label>
                <label>
                  Stock
                  <input type="number" min="0" value={editForm.stock} onChange={(e) => setEditForm({ ...editForm, stock: e.target.value })} required />
                </label>
                <label>
                  Category
                  <input value={editForm.category} onChange={(e) => setEditForm({ ...editForm, category: e.target.value })} maxLength={255} />
                </label>
              </div>
              <div className="edit-actions">
                <button type="submit" className="btn-primary" disabled={busy}>{busy ? 'Saving...' : 'Save changes'}</button>
                <button type="button" className="btn-secondary" onClick={() => setEditingId(null)} disabled={busy}>Cancel</button>
              </div>
            </form>
          );
        }

        return (
          <div key={p.id} className="order-box listing-row">
            <div className="listing-thumb">
              {p.imageUrl ? <img src={p.imageUrl} alt="" /> : <span>No photo</span>}
            </div>
            <div className="listing-info">
              <strong>{p.name}</strong>
              <div className="muted">
                ${p.price.toFixed(2)} · {p.stockQuantity > 0 ? `stock: ${p.stockQuantity}` : 'out of stock'}
              </div>
            </div>
            <div className="listing-actions">
              <Link to={`/item/${p.id}`}>View</Link>
              <button className="btn-secondary" onClick={() => startEditing(p)} disabled={busyProductId !== null}>Edit</button>
              <label className="btn-secondary listing-photo-btn">
                {busy ? 'Working...' : p.imageUrl ? 'Change photo' : 'Add photo'}
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
              <button className="btn-secondary btn-danger" onClick={() => handleDelete(p)} disabled={busyProductId !== null}>Delete</button>
            </div>
          </div>
        );
      })}
    </div>
  );
}

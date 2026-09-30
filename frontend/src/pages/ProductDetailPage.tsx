import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { StarInput, Stars } from '../components/StarRating';
import type { Product, Review } from '../types';

export function ProductDetailPage() {
  const { productId } = useParams();
  const { buyerId } = useAuth();
  const { addItem } = useCart();
  const [product, setProduct] = useState<Product | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [added, setAdded] = useState(false);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const addedTimer = useRef<number | undefined>(undefined);

  function load() {
    Promise.all([
      catalogApi.get<Product>(`/products/${productId}`),
      catalogApi.get<Review[]>(`/products/${productId}/reviews`),
    ])
      .then(([p, r]) => {
        setProduct(p.data);
        setReviews(r.data);
      })
      .catch(() => setLoadError('Product not found.'));
  }

  useEffect(load, [productId]);
  useEffect(() => () => window.clearTimeout(addedTimer.current), []);

  function handleAdd() {
    if (!product) return;
    addItem(product, 1);
    setAdded(true);
    window.clearTimeout(addedTimer.current);
    addedTimer.current = window.setTimeout(() => setAdded(false), 2500);
  }

  async function handleSubmitReview(e: React.FormEvent) {
    e.preventDefault();
    if (rating < 1) {
      setReviewError('Please choose a star rating.');
      return;
    }
    setReviewError(null);
    setSubmitting(true);
    try {
      await catalogApi.post(`/products/${productId}/reviews`, { rating, comment });
      setRating(0);
      setComment('');
      load(); // refresh the list and the average
    } catch (err: any) {
      setReviewError(
        err?.response?.status === 409
          ? "You've already reviewed this product."
          : 'Could not submit your review. Please try again.'
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loadError) return <p style={{ color: '#c0392b' }}>{loadError}</p>;
  if (!product) return <p>Loading...</p>;

  const alreadyReviewed = reviews.some((r) => r.buyerId === buyerId);
  const inStock = product.stockQuantity > 0;

  return (
    <div>
      <p><Link to="/">← Back to products</Link></p>

      <div className="product-detail">
        <div className="product-image">
          {product.imageUrl ? <img src={product.imageUrl} alt={product.name} /> : <span>No photo yet</span>}
        </div>

        <div>
          <h1>{product.name}</h1>
          <p className="seller">Sold by {product.seller.businessName}</p>
          <p className="rating-line">
            {product.reviewCount > 0 ? (
              <>
                <Stars value={product.averageRating} /> {product.averageRating.toFixed(1)} ({product.reviewCount}{' '}
                review{product.reviewCount === 1 ? '' : 's'})
              </>
            ) : (
              <span className="muted">No reviews yet</span>
            )}
          </p>
          <p className="price">${product.price.toFixed(2)}</p>
          <p className="muted">{inStock ? `${product.stockQuantity} in stock` : 'Out of stock'}</p>
          <button className="btn-primary" onClick={handleAdd} disabled={!inStock}>Add to cart</button>
          {added && (
            <p role="status">✓ Added to cart. <Link to="/cart">View cart</Link></p>
          )}

          <h2 style={{ marginTop: '2rem' }}>Description</h2>
          <p style={{ whiteSpace: 'pre-line' }}>{product.description || 'No description provided.'}</p>
        </div>
      </div>

      <section className="reviews">
        <h2>Customer reviews</h2>

        {buyerId && !alreadyReviewed && (
          <form onSubmit={handleSubmitReview} className="review-form">
            <strong>Write a review</strong>
            <StarInput value={rating} onChange={setRating} />
            <textarea
              placeholder="What did you think of this product? (optional)"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              maxLength={2000}
            />
            {reviewError && <p style={{ color: '#c0392b' }}>{reviewError}</p>}
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? 'Submitting...' : 'Submit review'}
            </button>
          </form>
        )}
        {buyerId && alreadyReviewed && <p className="muted">Thanks for reviewing this product.</p>}
        {!buyerId && <p className="muted">Only buyer accounts can write reviews.</p>}

        {reviews.length === 0 ? (
          <p>No reviews yet.</p>
        ) : (
          reviews.map((r) => (
            <div key={r.id} className="review">
              <div>
                <Stars value={r.rating} /> <strong>{r.buyerName}</strong>{' '}
                <span className="muted">{new Date(r.createdAt).toLocaleDateString()}</span>
              </div>
              {r.comment && <p style={{ whiteSpace: 'pre-line' }}>{r.comment}</p>}
            </div>
          ))
        )}
      </section>
    </div>
  );
}

import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { useAuth } from '../context/AuthContext';

export function SellerRegisterPage() {
  const [businessName, setBusinessName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await catalogApi.post('/sellers/register', { email, password, businessName });
      await login(email.trim().toLowerCase(), password);
      navigate('/seller/products');
    } catch (err: any) {
      if (err?.response?.status === 409) {
        setError('An account with that email already exists.');
      } else {
        setError('Registration failed. Please check your details and try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ maxWidth: 360, margin: '2rem auto' }}>
      <h1>Sell on OrderFlow</h1>
      <p className="muted">Create a seller account. An admin reviews new sellers before their products go live.</p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1rem' }}>
        <input placeholder="Business name" value={businessName} onChange={(e) => setBusinessName(e.target.value)} required maxLength={255} />
        <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        <input
          type="password"
          placeholder="Password (at least 8 characters)"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={100}
        />
        {error && <p style={{ color: '#c0392b' }}>{error}</p>}
        <button className="btn-primary" type="submit" disabled={submitting}>
          {submitting ? 'Creating account...' : 'Create seller account'}
        </button>
      </form>
      <p style={{ marginTop: '1rem', fontSize: '0.9rem' }}>
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </div>
  );
}

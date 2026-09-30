import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { isTokenValid } from '../utils/jwt';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const { login, token } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // The page the user originally asked for, set by the login guard
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  // Already logged in: skip the form
  if (isTokenValid(token)) return <Navigate to={from} replace />;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch {
      setError('Login failed. Check your email and password.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ maxWidth: 360, margin: '3rem auto' }}>
      <h1>Welcome to OrderFlow</h1>
      <p style={{ color: '#666', marginBottom: '1.5rem' }}>Log in to start shopping.</p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        <input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        {error && <p style={{ color: '#c0392b' }}>{error}</p>}
        <button className="btn-primary" type="submit" disabled={submitting}>
          {submitting ? 'Logging in...' : 'Log in'}
        </button>
      </form>
      <p style={{ marginTop: '1rem', fontSize: '0.9rem' }}>
        New here? <Link to="/buyer-register">Create a buyer account</Link>
      </p>
    </div>
  );
}

import { createContext, useContext, useState, type ReactNode } from 'react';
import { catalogApi } from '../api/client';
import { decodeToken } from '../utils/jwt';

interface AuthContextType {
  token: string | null;
  email: string | null;
  sellerId: string | null;
  buyerId: string | null;
  role: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function deriveFromToken(token: string | null) {
  if (!token) return { sellerId: null, buyerId: null, role: null };
  const decoded = decodeToken(token);
  return {
    sellerId: decoded?.sellerId ?? null,
    buyerId: decoded?.buyerId ?? null,
    role: decoded?.role ?? null,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const initialToken = localStorage.getItem('orderflow_token');
  const [token, setToken] = useState<string | null>(initialToken);
  const [email, setEmail] = useState<string | null>(
    localStorage.getItem('orderflow_email')
  );
  const [{ sellerId, buyerId, role }, setDerived] = useState(deriveFromToken(initialToken));

  async function login(loginEmail: string, password: string) {
    const response = await catalogApi.post('/auth/login', {
      email: loginEmail,
      password,
    });
    const { token: newToken } = response.data;
    localStorage.setItem('orderflow_token', newToken);
    localStorage.setItem('orderflow_email', loginEmail);
    setToken(newToken);
    setEmail(loginEmail);
    setDerived(deriveFromToken(newToken));
  }

  function logout() {
    localStorage.removeItem('orderflow_token');
    localStorage.removeItem('orderflow_email');
    setToken(null);
    setEmail(null);
    setDerived({ sellerId: null, buyerId: null, role: null });
  }

  return (
    <AuthContext.Provider value={{ token, email, sellerId, buyerId, role, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}

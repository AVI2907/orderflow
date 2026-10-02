import type { ReactNode } from 'react';
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { CartProvider, useCart } from './context/CartContext';
import { isTokenValid } from './utils/jwt';
import { ProductsPage } from './pages/ProductsPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { LoginPage } from './pages/LoginPage';
import { BuyerRegisterPage } from './pages/BuyerRegisterPage';
import { CartPage } from './pages/CartPage';
import { PaymentPage } from './pages/PaymentPage';
import { OrderPage } from './pages/OrderPage';
import { MyProductsPage } from './pages/MyProductsPage';
import { MySellerOrdersPage } from './pages/MySellerOrdersPage';

/** Sends logged-out (or expired) users to the login page, remembering where they were going. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  const location = useLocation();
  if (!isTokenValid(token)) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}

function Nav() {
  const { token, email, sellerId, logout } = useAuth();
  const { items } = useCart();
  const itemCount = items.reduce((sum, i) => sum + i.quantity, 0);
  const loggedIn = isTokenValid(token);

  return (
    <nav style={{ display: 'flex', gap: '1rem', padding: '1rem', borderBottom: '1px solid #ccc', alignItems: 'center' }}>
      <strong>OrderFlow</strong>
      {loggedIn && (
        <>
          <Link to="/">Products</Link>
          <Link to="/cart">Cart ({itemCount})</Link>
          {sellerId && (
            <>
              <Link to="/seller/products">My Products</Link>
              <Link to="/seller/orders">My Orders</Link>
            </>
          )}
        </>
      )}
      <div style={{ marginLeft: 'auto' }}>
        {loggedIn ? (
          <>
            <span style={{ marginRight: '1rem' }}>{email}</span>
            <button onClick={logout}>Log out</button>
          </>
        ) : (
          <Link to="/login">Log in</Link>
        )}
      </div>
    </nav>
  );
}

const protect = (page: ReactNode) => <RequireAuth>{page}</RequireAuth>;

function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <BrowserRouter>
          <Nav />
          <div style={{ padding: '1.5rem' }}>
            <Routes>
              {/* Public */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/buyer-register" element={<BuyerRegisterPage />} />

              {/* Everything else requires login */}
              <Route path="/" element={protect(<ProductsPage />)} />
              <Route path="/item/:productId" element={protect(<ProductDetailPage />)} />
              <Route path="/cart" element={protect(<CartPage />)} />
              <Route path="/checkout/:orderId" element={protect(<PaymentPage />)} />
              <Route path="/order/:orderId" element={protect(<OrderPage />)} />
              <Route path="/seller/products" element={protect(<MyProductsPage />)} />
              <Route path="/seller/orders" element={protect(<MySellerOrdersPage />)} />

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </div>
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  );
}

export default App;

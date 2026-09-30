import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { CartProvider, useCart } from './context/CartContext';
import { ProductsPage } from './pages/ProductsPage';
import { LoginPage } from './pages/LoginPage';
import { BuyerRegisterPage } from './pages/BuyerRegisterPage';
import { CartPage } from './pages/CartPage';
import { OrderPage } from './pages/OrderPage';
import { PaymentPage } from './pages/PaymentPage';
import { MyProductsPage } from './pages/MyProductsPage';
import { MySellerOrdersPage } from './pages/MySellerOrdersPage';

function Nav() {
  const { email, sellerId, logout } = useAuth();
  const { items } = useCart();
  const itemCount = items.reduce((sum, i) => sum + i.quantity, 0);

  return (
    <nav style={{ display: 'flex', gap: '1rem', padding: '1rem', borderBottom: '1px solid #ccc', alignItems: 'center' }}>
      <Link to="/">Products</Link>
      <Link to="/cart">Cart ({itemCount})</Link>
      {sellerId && (
        <>
          <Link to="/seller/products">My Products</Link>
          <Link to="/seller/orders">My Orders</Link>
        </>
      )}
      <div style={{ marginLeft: 'auto' }}>
        {email ? (
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

function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <BrowserRouter>
          <Nav />
          <div style={{ padding: '1.5rem' }}>
            <Routes>
              <Route path="/" element={<ProductsPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/buyer-register" element={<BuyerRegisterPage />} />
              <Route path="/cart" element={<CartPage />} />
              <Route path="/orders/:orderId" element={<OrderPage />} />
              <Route path="/checkout/:orderId" element={<PaymentPage />} />
              <Route path="/seller/products" element={<MyProductsPage />} />
              <Route path="/seller/orders" element={<MySellerOrdersPage />} />
            </Routes>
          </div>
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  );
}

export default App;

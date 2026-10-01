import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { CartItem, Product } from '../types';
import { useAuth } from './AuthContext';

interface CartContextType {
  items: CartItem[];
  addItem: (product: Product, quantity: number) => void;
  removeItem: (productId: string) => void;
  clearCart: () => void;
  total: number;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

// Each user gets their own saved cart in this browser
const STORAGE_PREFIX = 'orderflow_cart:';

function loadCart(key: string | null): CartItem[] {
  if (!key) return [];
  try {
    const parsed = JSON.parse(localStorage.getItem(key) ?? '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return []; // corrupted or unreadable: start with an empty cart
  }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const { email } = useAuth();
  const storageKey = email ? STORAGE_PREFIX + email : null;
  const [items, setItems] = useState<CartItem[]>(() => loadCart(storageKey));
  const [loadedKey, setLoadedKey] = useState(storageKey);

  // A different user logged in (or everyone logged out): switch to that user's saved cart
  if (loadedKey !== storageKey) {
    setLoadedKey(storageKey);
    setItems(loadCart(storageKey));
  }

  useEffect(() => {
    if (!storageKey) return;
    try {
      localStorage.setItem(storageKey, JSON.stringify(items));
    } catch {
      // Storage full or blocked (some private modes): the cart still works for this visit
    }
  }, [items, storageKey]);

  function addItem(product: Product, quantity: number) {
    setItems((prev) => {
      const existing = prev.find((i) => i.product.id === product.id);
      if (existing) {
        return prev.map((i) =>
          i.product.id === product.id ? { ...i, quantity: i.quantity + quantity } : i
        );
      }
      return [...prev, { product, quantity }];
    });
  }

  function removeItem(productId: string) {
    setItems((prev) => prev.filter((i) => i.product.id !== productId));
  }

  function clearCart() {
    setItems([]);
  }

  const total = items.reduce((sum, item) => sum + item.product.price * item.quantity, 0);

  return (
    <CartContext.Provider value={{ items, addItem, removeItem, clearCart, total }}>
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used within CartProvider');
  return context;
}

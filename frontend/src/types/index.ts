export interface Seller {
  id: string;
  email: string;
  businessName: string;
  status: string;
  createdAt: string;
}

export interface Product {
  id: string;
  seller: Seller;
  name: string;
  description: string;
  price: number;
  stockQuantity: number;
  category: string;
  imageUrl: string | null;
  createdAt: string;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

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
  averageRating: number;
  reviewCount: number;
  createdAt: string;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface Review {
  id: string;
  buyerId: string;
  buyerName: string;
  rating: number;
  comment: string | null;
  createdAt: string;
}

export interface DecodedToken {
  sub: string;
  role: string;
  sellerId?: string;
  buyerId?: string;
  exp: number;
}

export function decodeToken(token: string): DecodedToken | null {
  try {
    const payload = token.split('.')[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decoded);
  } catch {
    return null;
  }
}

/** True only for a well-formed token that hasn't expired yet. */
export function isTokenValid(token: string | null | undefined): boolean {
  if (!token) return false;
  const decoded = decodeToken(token);
  return !!decoded && decoded.exp * 1000 > Date.now();
}

export interface ShippingAddress {
  fullName: string;
  line1: string;
  line2?: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export function ShippingAddressView({ address }: { address?: ShippingAddress | null }) {
  // Orders placed before addresses were added have none
  if (!address || !address.line1) return null;
  return (
    <div className="address-view">
      <strong>Ship to</strong>
      <div>{address.fullName}</div>
      <div>{address.line1}</div>
      {address.line2 && <div>{address.line2}</div>}
      <div>{address.city}, {address.state} {address.postalCode}</div>
      <div>{address.country}</div>
    </div>
  );
}

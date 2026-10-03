const STEPS = [
  { key: 'PLACED', label: 'Placed' },
  { key: 'PAID', label: 'Paid' },
  { key: 'SHIPPED', label: 'Shipped' },
  { key: 'DELIVERED', label: 'Delivered' },
];

export const CARRIER_NAMES: Record<string, string> = {
  UPS: 'UPS',
  USPS: 'USPS',
  FEDEX: 'FedEx',
  DHL: 'DHL',
  OTHER: 'Other carrier',
};

export interface TrackingInfo {
  status: string;
  placedAt?: string | null;
  paidAt?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
  carrier?: string | null;
  trackingNumber?: string | null;
  trackingUrl?: string | null;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
}

/** Placed → Paid → Shipped → Delivered, with dates and the carrier's tracking link. */
export function TrackingTimeline({ info }: { info: TrackingInfo }) {
  if (info.status === 'CANCELLED') {
    return <p className="muted">This part of the order was cancelled.</p>;
  }

  const current = STEPS.findIndex((s) => s.key === info.status);
  const dates = [info.placedAt, info.paidAt, info.shippedAt, info.deliveredAt];

  return (
    <div>
      <ol className="timeline">
        {STEPS.map((step, i) => (
          <li key={step.key} className={i <= current ? 'done' : ''} aria-current={i === current ? 'step' : undefined}>
            <span className="dot" />
            <span className="step-label">{step.label}</span>
            {i <= current && dates[i] && <span className="step-date">{formatDate(dates[i] as string)}</span>}
          </li>
        ))}
      </ol>
      {info.trackingNumber && (
        <p className="tracking-number">
          {CARRIER_NAMES[info.carrier ?? ''] ?? info.carrier} tracking number: <strong>{info.trackingNumber}</strong>
          {info.trackingUrl && (
            <>
              {' · '}
              <a href={info.trackingUrl} target="_blank" rel="noopener noreferrer">Track package</a>
            </>
          )}
        </p>
      )}
    </div>
  );
}

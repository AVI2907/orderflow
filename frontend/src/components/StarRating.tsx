const FIVE_STARS = '★★★★★';

/** Read-only stars, e.g. for an average rating. */
export function Stars({ value }: { value: number }) {
  const filled = Math.max(0, Math.min(5, Math.round(value)));
  return (
    <span className="stars" role="img" aria-label={`${value.toFixed(1)} out of 5 stars`}>
      {FIVE_STARS.slice(0, filled)}
      <span className="stars-empty">{FIVE_STARS.slice(filled)}</span>
    </span>
  );
}

/** Clickable 1-5 star picker for the review form. */
export function StarInput({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <div className="star-input" role="radiogroup" aria-label="Your rating">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n} star${n > 1 ? 's' : ''}`}
          className={n <= value ? 'on' : ''}
          onClick={() => onChange(n)}
        >
          ★
        </button>
      ))}
    </div>
  );
}

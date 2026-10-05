import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { useAuth } from '../context/AuthContext';

interface Address {
  fullName: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

interface Profile {
  id: string;
  email: string;
  name: string;
  phone: string | null;
  defaultAddress: Partial<Record<keyof Address, string | null>> | null;
  createdAt: string;
}

type Msg = { ok: boolean; text: string } | null;

const EMPTY_ADDRESS: Address = { fullName: '', line1: '', line2: '', city: '', state: '', postalCode: '', country: 'United States' };
const REQUIRED: (keyof Address)[] = ['fullName', 'line1', 'city', 'state', 'postalCode', 'country'];

function toForm(a: Profile['defaultAddress']): Address {
  if (!a) return EMPTY_ADDRESS;
  return {
    fullName: a.fullName ?? '', line1: a.line1 ?? '', line2: a.line2 ?? '', city: a.city ?? '',
    state: a.state ?? '', postalCode: a.postalCode ?? '', country: a.country ?? 'United States',
  };
}

function errorText(err: any, fallback: string): string {
  return err?.response?.data?.message || fallback;
}

export function ProfilePage() {
  const { buyerId, logout } = useAuth();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState<Address>(EMPTY_ADDRESS);
  const [detailsMsg, setDetailsMsg] = useState<Msg>(null);
  const [savingDetails, setSavingDetails] = useState(false);

  const [currentPw, setCurrentPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [pwMsg, setPwMsg] = useState<Msg>(null);
  const [savingPw, setSavingPw] = useState(false);

  const [newEmail, setNewEmail] = useState('');
  const [emailPw, setEmailPw] = useState('');
  const [emailMsg, setEmailMsg] = useState<Msg>(null);
  const [savingEmail, setSavingEmail] = useState(false);

  useEffect(() => {
    if (!buyerId) return;
    catalogApi
      .get<Profile>('/buyers/me')
      .then((res) => {
        setProfile(res.data);
        setName(res.data.name);
        setPhone(res.data.phone ?? '');
        setAddress(toForm(res.data.defaultAddress));
      })
      .catch(() => setLoadError('Could not load your profile. Please try again.'));
  }, [buyerId]);

  function addressField(key: keyof Address) {
    return {
      value: address[key],
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => setAddress((cur) => ({ ...cur, [key]: e.target.value })),
    };
  }

  async function saveDetails(e: React.FormEvent) {
    e.preventDefault();
    setDetailsMsg(null);
    const anyFilled = REQUIRED.some((k) => k !== 'country' && address[k].trim());
    const allFilled = REQUIRED.every((k) => address[k].trim());
    if (anyFilled && !allFilled) {
      setDetailsMsg({ ok: false, text: 'Fill in every required address field, or leave the address empty.' });
      return;
    }
    setSavingDetails(true);
    try {
      const res = await catalogApi.put<Profile>('/buyers/me', {
        name,
        phone: phone.trim() || null,
        defaultAddress: allFilled ? address : null,
      });
      setProfile(res.data);
      setDetailsMsg({ ok: true, text: 'Your details are saved.' });
    } catch (err) {
      setDetailsMsg({ ok: false, text: errorText(err, 'Could not save your details.') });
    } finally {
      setSavingDetails(false);
    }
  }

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    setPwMsg(null);
    if (newPw.length < 8) return setPwMsg({ ok: false, text: 'The new password must be at least 8 characters.' });
    if (newPw !== confirmPw) return setPwMsg({ ok: false, text: 'The new passwords do not match.' });
    setSavingPw(true);
    try {
      await catalogApi.post('/buyers/me/password', { currentPassword: currentPw, newPassword: newPw });
      setCurrentPw('');
      setNewPw('');
      setConfirmPw('');
      setPwMsg({ ok: true, text: 'Your password has been changed.' });
    } catch (err) {
      setPwMsg({ ok: false, text: errorText(err, 'Could not change your password.') });
    } finally {
      setSavingPw(false);
    }
  }

  async function changeEmail(e: React.FormEvent) {
    e.preventDefault();
    setEmailMsg(null);
    if (!window.confirm('After changing your email you will need to log in again with the new one. Continue?')) return;
    setSavingEmail(true);
    try {
      await catalogApi.post('/buyers/me/email', { currentPassword: emailPw, newEmail });
      // The old login is tied to the old email, so start a fresh session
      logout();
      navigate('/login', { replace: true });
    } catch (err) {
      setEmailMsg({ ok: false, text: errorText(err, 'Could not change your email.') });
      setSavingEmail(false);
    }
  }

  if (!buyerId) return <p>Profiles are available for buyer accounts.</p>;
  if (loadError) return <p className="form-error">{loadError}</p>;
  if (!profile) return <p>Loading...</p>;

  return (
    <div className="profile-page">
      <h1>My profile</h1>
      <p className="muted">
        Signed in as <strong>{profile.email}</strong> · member since {new Date(profile.createdAt).toLocaleDateString()} ·{' '}
        <Link to="/my-orders">View my orders</Link>
      </p>

      <section className="order-box">
        <h2>Personal details and default address</h2>
        <form onSubmit={saveDetails} className="checkout-form">
          <label>
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required maxLength={100} />
          </label>
          <label>
            Phone (optional)
            <input value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" inputMode="tel" maxLength={30} />
          </label>

          <h3>Default delivery address</h3>
          <p className="muted">Used to fill in checkout for you. Clear every field to remove it.</p>
          <label>
            Full name
            <input {...addressField('fullName')} autoComplete="shipping name" />
          </label>
          <label>
            Address line 1
            <input {...addressField('line1')} autoComplete="shipping address-line1" placeholder="Street address" />
          </label>
          <label>
            Address line 2 (optional)
            <input {...addressField('line2')} autoComplete="shipping address-line2" placeholder="Apartment, suite, unit" />
          </label>
          <div className="address-grid">
            <label>
              City
              <input {...addressField('city')} autoComplete="shipping address-level2" />
            </label>
            <label>
              State
              <input {...addressField('state')} autoComplete="shipping address-level1" />
            </label>
            <label>
              ZIP / postal code
              <input {...addressField('postalCode')} autoComplete="shipping postal-code" />
            </label>
            <label>
              Country
              <input {...addressField('country')} autoComplete="shipping country-name" />
            </label>
          </div>

          {detailsMsg && <p className={detailsMsg.ok ? 'form-ok' : 'form-error'}>{detailsMsg.text}</p>}
          <button type="submit" className="btn-primary" disabled={savingDetails}>
            {savingDetails ? 'Saving...' : 'Save details'}
          </button>
        </form>
      </section>

      <section className="order-box">
        <h2>Change password</h2>
        <form onSubmit={changePassword} className="checkout-form">
          <label>
            Current password
            <input type="password" value={currentPw} onChange={(e) => setCurrentPw(e.target.value)} autoComplete="current-password" required />
          </label>
          <label>
            New password (at least 8 characters)
            <input type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} autoComplete="new-password" required minLength={8} />
          </label>
          <label>
            Confirm new password
            <input type="password" value={confirmPw} onChange={(e) => setConfirmPw(e.target.value)} autoComplete="new-password" required />
          </label>
          {pwMsg && <p className={pwMsg.ok ? 'form-ok' : 'form-error'}>{pwMsg.text}</p>}
          <button type="submit" className="btn-primary" disabled={savingPw}>
            {savingPw ? 'Changing...' : 'Change password'}
          </button>
        </form>
      </section>

      <section className="order-box">
        <h2>Change email</h2>
        <form onSubmit={changeEmail} className="checkout-form">
          <label>
            New email
            <input type="email" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} autoComplete="email" required />
          </label>
          <label>
            Current password
            <input type="password" value={emailPw} onChange={(e) => setEmailPw(e.target.value)} autoComplete="current-password" required />
          </label>
          {emailMsg && <p className={emailMsg.ok ? 'form-ok' : 'form-error'}>{emailMsg.text}</p>}
          <button type="submit" className="btn-secondary" disabled={savingEmail}>
            {savingEmail ? 'Changing...' : 'Change email'}
          </button>
        </form>
      </section>
    </div>
  );
}

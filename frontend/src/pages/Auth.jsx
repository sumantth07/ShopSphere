import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, Eye, EyeOff, KeyRound, Mail, ShieldCheck, Sparkles } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import toast from 'react-hot-toast';
import { api, API_BASE } from '../api/client.js';
import { useAuth } from '../api/auth.jsx';
import { Button } from '../components/ui.jsx';

function AuthFrame({ children, title = 'A good place\nto come back to.' }) {
  return <div className="auth-page"><div className="auth-visual"><div className="auth-visual-glow" /><Link className="brand-mark" to="/"><span className="brand-icon"><span /></span><span>shop<span className="brand-light">sphere</span><i>®</i></span></Link><div className="auth-visual-copy"><span className="eyebrow">A MORE PERSONAL MARKETPLACE</span><h2>{title.split('\n').map((part, i) => <span key={i}>{part}<br /></span>)}</h2><p>Thoughtful finds, saved places,<br />and your next favorite thing.</p></div><span className="auth-visual-index">SS / 01 — YOUR ACCOUNT</span></div><div className="auth-panel-wrap"><div className="auth-panel">{children}</div><div className="auth-panel-foot"><Link to="/">← Back to exploring</Link><span>Secure sign-in · ShopSphere</span></div></div></div>;
}

function GoogleAuthButton() {
  return <a className="button button-secondary auth-submit" href={`${API_BASE}/oauth2/authorization/google`}>
    <span aria-hidden="true" className="google-mark">G</span> Continue with Google
  </a>;
}

export function Login() {
  const [form, setForm] = useState({ email: '', password: '' });
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  async function submit(e) { e.preventDefault(); setBusy(true); try { await login(form.email.trim(), form.password); toast.success('Welcome back'); navigate('/account'); } catch (err) { toast.error(err.message || 'Could not sign in'); } finally { setBusy(false); } }
  return <AuthFrame><span className="eyebrow">WELCOME BACK</span><h1>Sign in to your<br /><em>little corner.</em></h1><p className="auth-lead">Your saved finds and orders are right where you left them.</p><form className="form-stack" onSubmit={submit}><label>Email address<div className="input-icon"><Mail size={17} /><input type="email" autoComplete="email" required placeholder="you@example.com" value={form.email} onChange={e => setForm({...form,email:e.target.value})} /></div></label><label>Password<div className="input-icon"><KeyRound size={17} /><input type={show ? 'text':'password'} autoComplete="current-password" required placeholder="Your password" value={form.password} onChange={e => setForm({...form,password:e.target.value})} /><button className="input-end-button" type="button" onClick={() => setShow(!show)} aria-label="Toggle password visibility">{show ? <EyeOff size={16}/> : <Eye size={16}/>}</button></div></label><div className="form-aux"><span>Secure sign-in</span><Link to="/forgot-password">Forgot password?</Link></div><Button type="submit" disabled={busy} className="auth-submit">{busy ? 'Signing in…' : 'Sign in'} <ArrowRight size={16}/></Button></form><GoogleAuthButton/><p className="auth-switch">New around here? <Link to="/register">Make an account <ArrowRight size={14}/></Link></p><div className="auth-trust"><ShieldCheck size={15}/> Your details stay yours. Always.</div></AuthFrame>;
}

export function Register() {
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '' });
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  async function submit(e) { e.preventDefault(); if (form.password !== form.confirmPassword) return toast.error('Those passwords don’t match.'); setBusy(true); try { const result = await api('/api/auth/register', { method: 'POST', body: form }); sessionStorage.setItem('shopsphere.pendingEmail', form.email.trim()); toast.success(result?.message || 'Check your email for a code'); navigate('/verify-email'); } catch (err) { toast.error(err.message || 'Could not create account'); } finally { setBusy(false); } }
  return <AuthFrame title={'Find your place.\nKeep your favorites.'}><span className="eyebrow">JOIN THE SHOPSPHERE EDIT</span><h1>Make it<br /><em>your space.</em></h1><p className="auth-lead">Save the finds you love and keep your orders close.</p><form className="form-stack" onSubmit={submit}><label>Your name<input className="text-input" required maxLength="120" autoComplete="name" placeholder="First and last name" value={form.fullName} onChange={e=>setForm({...form,fullName:e.target.value})}/></label><label>Email address<input className="text-input" type="email" required autoComplete="email" placeholder="you@example.com" value={form.email} onChange={e=>setForm({...form,email:e.target.value})}/></label><label>Password<input className="text-input" type="password" required minLength="8" autoComplete="new-password" placeholder="At least 8 characters, with a number" value={form.password} onChange={e=>setForm({...form,password:e.target.value})}/></label><label>Confirm password<input className="text-input" type="password" required autoComplete="new-password" placeholder="Type it once more" value={form.confirmPassword} onChange={e=>setForm({...form,confirmPassword:e.target.value})}/></label><Button type="submit" disabled={busy} className="auth-submit">{busy ? 'Creating your account…':'Create account'} <ArrowRight size={16}/></Button></form><GoogleAuthButton/><p className="auth-switch">Already a member? <Link to="/login">Sign in instead</Link></p><p className="terms-note">By joining, you agree to our Terms and Privacy Policy.</p></AuthFrame>;
}

export function VerifyEmail() {
  const { verifyEmail } = useAuth();
  const [email, setEmail] = useState(sessionStorage.getItem('shopsphere.pendingEmail') || '');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  async function submit(e) { e.preventDefault(); setBusy(true); try { await verifyEmail(email.trim(), code.trim()); sessionStorage.removeItem('shopsphere.pendingEmail'); toast.success('Your email is verified'); navigate('/account'); } catch (err) { toast.error(err.message); } finally { setBusy(false); } }
  async function resend() { try { const result = await api('/api/auth/resend-verification', { method:'POST', body:{ email:email.trim() } }); toast.success(result?.message || 'A fresh code is on the way'); } catch (e) { toast.error(e.message); } }
  return <AuthFrame title={'A quick check.\nThen you’re in.'}><span className="auth-icon-disc"><Mail size={21}/></span><span className="eyebrow">ONE LAST LITTLE THING</span><h1>Check your<br /><em>inbox.</em></h1><p className="auth-lead">We sent a six-digit verification code to your email. Enter it below to finish setting up your account.</p><form className="form-stack" onSubmit={submit}><label>Email address<input className="text-input" type="email" required value={email} onChange={e=>setEmail(e.target.value)}/></label><label>Verification code<input className="text-input otp-input" inputMode="numeric" pattern="[0-9]{6}" maxLength="6" required placeholder="000000" value={code} onChange={e=>setCode(e.target.value.replace(/\D/g,'').slice(0,6))}/></label><Button type="submit" disabled={busy || code.length !== 6} className="auth-submit">{busy ? 'Verifying…':'Verify email'} <ArrowRight size={16}/></Button></form><p className="auth-switch">No email yet? <button className="link-button" onClick={resend}>Resend the code</button></p><Link className="back-link" to="/register"><ArrowLeft size={14}/> Back to sign up</Link></AuthFrame>;
}

export function ForgotPassword() {
  const [email, setEmail] = useState(''); const [sent, setSent] = useState(false); const [busy,setBusy]=useState(false);
  async function submit(e){e.preventDefault();setBusy(true);try{const result=await api('/api/auth/forgot-password',{method:'POST',body:{email:email.trim()}});toast.success(result?.message||'If the account exists, a code is on its way');sessionStorage.setItem('shopsphere.resetEmail',email.trim());setSent(true);}catch(err){toast.error(err.message)}finally{setBusy(false)}}
  return <AuthFrame><span className="eyebrow">A FRESH START</span><h1>Let’s get you<br /><em>back in.</em></h1><p className="auth-lead">Enter your account email and we’ll send a password reset code.</p>{sent ? <div className="success-panel"><Sparkles size={20}/><p>If there’s an account for <b>{email}</b>, a reset code is on its way.</p><Link className="button button-primary" to="/reset-password">Enter reset code <ArrowRight size={15}/></Link></div> : <form className="form-stack" onSubmit={submit}><label>Email address<input className="text-input" type="email" required autoComplete="email" placeholder="you@example.com" value={email} onChange={e=>setEmail(e.target.value)}/></label><Button type="submit" disabled={busy} className="auth-submit">{busy?'Sending…':'Send reset code'} <ArrowRight size={16}/></Button></form>}<p className="auth-switch"><Link to="/login">← Back to sign in</Link></p></AuthFrame>;
}

export function ResetPassword() {
  const [form,setForm]=useState({email:sessionStorage.getItem('shopsphere.resetEmail')||'',code:'',newPassword:''});const [busy,setBusy]=useState(false);const navigate=useNavigate();
  async function submit(e){e.preventDefault();setBusy(true);try{const result=await api('/api/auth/reset-password',{method:'POST',body:form});toast.success(result?.message||'Password updated');navigate('/login')}catch(err){toast.error(err.message)}finally{setBusy(false)}}
  return <AuthFrame><span className="eyebrow">NEW KEY, SAME YOU</span><h1>Choose a<br /><em>new password.</em></h1><p className="auth-lead">Use the six-digit code we sent and make a fresh password.</p><form className="form-stack" onSubmit={submit}><label>Email address<input className="text-input" type="email" required value={form.email} onChange={e=>setForm({...form,email:e.target.value})}/></label><label>Six-digit code<input className="text-input otp-input" inputMode="numeric" pattern="[0-9]{6}" maxLength="6" required value={form.code} onChange={e=>setForm({...form,code:e.target.value.replace(/\D/g,'').slice(0,6)})}/></label><label>New password<input className="text-input" type="password" minLength="8" required autoComplete="new-password" value={form.newPassword} onChange={e=>setForm({...form,newPassword:e.target.value})}/></label><Button type="submit" className="auth-submit" disabled={busy}>{busy?'Saving…':'Save new password'} <ArrowRight size={16}/></Button></form></AuthFrame>;
}

export function AuthCallback() {
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { accept } = useAuth();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    async function finishGoogleSignIn() {
      try {
        // OAuth returns to the frontend with the backend's refresh cookie.
        // Exchange it for the same access-token session used by email login.
        const session = await api('/api/auth/refresh', {
          method: 'POST',
          retryAuth: false,
        });

        if (!session?.accessToken) {
          throw new Error('The backend did not return an access token.');
        }

        const user = session.user || await api('/api/users/me');
        accept({ ...session, user });
        toast.success('Signed in with Google');
        navigate('/account', { replace: true });
      } catch (err) {
        setError(err.message || 'Google sign-in could not be completed.');
      }
    }

    finishGoogleSignIn();
  }, [accept, navigate]);

  return (
    <AuthFrame title={'You’re almost there.\nFinishing sign-in.'}>
      <span className="eyebrow">GOOGLE SIGN-IN</span>
      <h1>{error ? <>Sign-in needs<br /><em>one more step.</em></> : <>Finishing your<br /><em>sign-in.</em></>}</h1>
      {error ? (
        <>
          <p className="auth-lead">{error}</p>
          <Link className="button button-primary" to="/login">Return to sign in</Link>
        </>
      ) : (
        <p className="auth-lead">We’re securely connecting your ShopSphere account.</p>
      )}
    </AuthFrame>
  );
}

export function GoogleLinkConfirm() {
  const location = useLocation();
  const navigate = useNavigate();
  const { accept } = useAuth();
  const token = new URLSearchParams(location.search).get('token') || '';
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (!token) {
      toast.error('This Google link request is missing or expired. Start again from sign in.');
      return;
    }
    setBusy(true);
    try {
      const session = await api('/api/auth/google/confirm-link', {
        method: 'POST',
        body: { token, code: code.trim() },
      });
      accept(session);
      toast.success('Google is now linked to your ShopSphere account');
      navigate('/account', { replace: true });
    } catch (err) {
      toast.error(err.message || 'Could not confirm the Google link');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthFrame title={'One quick check.\nThen you’re linked.'}>
      <span className="auth-icon-disc"><Mail size={21} /></span>
      <span className="eyebrow">LINK YOUR GOOGLE ACCOUNT</span>
      <h1>Check your<br /><em>inbox.</em></h1>
      <p className="auth-lead">
        This email already has a ShopSphere account. Enter the six-digit code
        sent to that email to securely link Google.
      </p>
      {token ? (
        <form className="form-stack" onSubmit={submit}>
          <label>
            Verification code
            <input
              className="text-input otp-input"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength="6"
              required
              placeholder="000000"
              value={code}
              onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            />
          </label>
          <Button type="submit" disabled={busy || code.length !== 6} className="auth-submit">
            {busy ? 'Linking account…' : 'Confirm and sign in'} <ArrowRight size={16} />
          </Button>
        </form>
      ) : (
        <>
          <p className="auth-lead">The link request is missing or expired. Start Google sign-in again.</p>
          <Link className="button button-primary" to="/login">Return to sign in</Link>
        </>
      )}
    </AuthFrame>
  );
}

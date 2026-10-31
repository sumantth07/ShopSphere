import { lazy, Suspense } from 'react';
import { Link, Outlet } from 'react-router-dom';
import { ArrowUpRight, Instagram, Sparkles } from 'lucide-react';
import Header from './Header.jsx';
const ChatWidget = lazy(() => import('./ChatWidget.jsx'));

function Footer() {
  return <footer className="site-footer"><div className="footer-top"><div><Link className="brand-mark footer-brand" to="/"><span className="brand-icon"><span /></span><span>shop<span className="brand-light">sphere</span><i>®</i></span></Link><p>A little bit of everything, from people<br />who care about what they make.</p></div><div className="footer-links"><div><b>Explore</b><Link to="/shop">The marketplace</Link><Link to="/compare">Compare finds</Link><Link to="/wishlist">Your wishlist</Link></div><div><b>Your space</b><Link to="/account">My account</Link><Link to="/orders">Orders & delivery</Link><Link to="/seller/apply">Become a seller</Link></div><div><b>Questions?</b><a href="mailto:hello@shopsphere.local">Say hello <ArrowUpRight size={13} /></a><a href="/shop">Shipping & returns</a><a href="/shop">Our story</a></div></div></div><div className="footer-bottom"><span>© 2026 ShopSphere Studio</span><span>Made for the things you’ll keep.</span><a className="social-link" href="https://instagram.com" target="_blank" rel="noreferrer"><Instagram size={15} /> Follow along</a></div></footer>;
}

export default function Layout() {
  return <><Header /><main className="main-content"><Outlet /></main><Footer /><Suspense fallback={null}><ChatWidget /></Suspense></>;
}

export function StudioShell({ children, title = 'Studio' }) {
  return <div className="studio-shell"><aside className="studio-sidebar"><Link className="brand-mark" to="/"><span className="brand-icon"><span /></span><span>shop<span className="brand-light">sphere</span><i>®</i></span></Link><div className="studio-caption">{title}</div>{children}</aside><div className="studio-mobile-brand"><Link to="/"><Sparkles size={17} /> ShopSphere</Link></div><div className="studio-main" /></div>;
}

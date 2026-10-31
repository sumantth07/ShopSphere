import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { Menu, Search, ShoppingBag, UserRound, X, Heart, LayoutDashboard } from 'lucide-react';
import { useAuth } from '../api/auth.jsx';

function Logo() { return <Link className="brand-mark" to="/" aria-label="ShopSphere home"><span className="brand-icon"><span /></span><span>shop<span className="brand-light">sphere</span><i>®</i></span></Link>; }

export default function Header() {
  const { user } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = useNavigate();
  const role = user?.role?.toUpperCase().replace(/^ROLE_/, '');
  const workspace = role === 'ADMIN' ? ['/admin', 'Admin studio'] : role === 'SELLER' ? ['/seller', 'Seller studio'] : null;
  return <>
    <div className="announcement"><span className="announcement-dot" /> Thoughtful finds. Independent makers. <Link to="/shop">Meet the edit <span>↗</span></Link></div>
    <header className="site-header">
      <div className="header-inner">
        <button className="icon-button menu-toggle" aria-label="Open navigation" onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? <X size={19} /> : <Menu size={20} />}</button>
        <Logo />
        <nav className={`primary-nav ${menuOpen ? 'nav-open' : ''}`}>
          <NavLink to="/shop" onClick={() => setMenuOpen(false)}>Discover</NavLink>
          <a href="/shop?sort=popular" onClick={(e) => {e.preventDefault(); navigate('/shop?sort=popular'); setMenuOpen(false);}}>The edit</a>
          <NavLink to="/compare" onClick={() => setMenuOpen(false)}>Compare</NavLink>
          <a href="/shop?inStock=true" onClick={(e) => {e.preventDefault(); navigate('/shop?inStock=true'); setMenuOpen(false);}}>In stock</a>
        </nav>
        <div className="header-actions">
          <button className="icon-button search-shortcut" aria-label="Search products" onClick={() => navigate('/shop')}><Search size={19} /></button>
          <Link className="icon-button account-shortcut" to={user ? '/account' : '/login'} aria-label="Account"><UserRound size={19} /><span className="action-label">{user ? 'Account' : 'Sign in'}</span></Link>
          <Link className="icon-button wishlist-shortcut" to="/wishlist" aria-label="Wishlist"><Heart size={19} /></Link>
          <Link className="bag-button" to="/cart" aria-label="Shopping bag"><ShoppingBag size={18} /><span className="action-label">Bag</span></Link>
          {workspace && <Link className="workspace-link" to={workspace[0]}><LayoutDashboard size={16} /><span>{workspace[1]}</span></Link>}
        </div>
      </div>
    </header>
  </>;
}

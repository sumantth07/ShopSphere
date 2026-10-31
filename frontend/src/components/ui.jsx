import { motion } from 'motion/react';
import { ArrowUpRight, PackageOpen, RefreshCw, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';

export function Button({ children, variant = 'primary', size = '', className = '', type = 'button', disabled, onClick, ...props }) {
  return <button type={type} className={`button button-${variant} ${size ? `button-${size}` : ''} ${className}`} disabled={disabled} onClick={onClick} {...props}>{children}</button>;
}

export function PageIntro({ eyebrow, title, description, action }) {
  return <div className="page-intro"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1>{description && <p>{description}</p>}</div>{action}</div>;
}

export function EmptyState({ icon: Icon = PackageOpen, title = 'Nothing here yet', text, action }) {
  return <motion.div className="empty-state" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}><span className="empty-icon"><Icon size={23} /></span><h3>{title}</h3>{text && <p>{text}</p>}{action}</motion.div>;
}

export function ErrorState({ error, onRetry }) {
  return <div className="error-state"><div><span className="error-orb"><RefreshCw size={17} /></span><div><strong>We couldn’t reach the catalog</strong><p>{error?.message || 'Please try again in a moment.'}</p></div></div>{onRetry && <Button variant="quiet" onClick={onRetry}>Try again <ArrowUpRight size={15} /></Button>}</div>;
}

export function LoadingCards({ count = 4 }) {
  return <div className="product-grid">{Array.from({ length: count }, (_, i) => <div className="product-skeleton" key={i}><div className="skeleton-image" /><div className="skeleton-line" /><div className="skeleton-line short" /></div>)}</div>;
}

export function SectionHeading({ eyebrow, title, link, to = '/shop' }) {
  return <div className="section-heading"><div>{eyebrow && <span className="eyebrow">{eyebrow}</span>}<h2>{title}</h2></div>{link && <Link className="text-link" to={to}>{link}<ArrowUpRight size={15} /></Link>}</div>;
}

export function AmbientLabel({ children }) { return <span className="ambient-label"><Sparkles size={13} />{children}</span>; }

import { useQuery } from '@tanstack/react-query';
import { ArrowDown, ArrowRight, ArrowUpRight, BadgeCheck, ChevronRight, CircleDot, Compass, Sparkles, Truck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { api } from '../api/client.js';
import ProductCard from '../components/ProductCard.jsx';
import { AmbientLabel, ErrorState, LoadingCards, SectionHeading } from '../components/ui.jsx';

const categoriesArt = [
  { icon: '01', art: 'art-amber', title: 'Everyday objects', caption: 'Made to be lived with' },
  { icon: '02', art: 'art-lilac', title: 'Little rituals', caption: 'A softer kind of daily' },
  { icon: '03', art: 'art-moss', title: 'Wear & carry', caption: 'The well-loved essentials' },
];

export default function Home() {
  const products = useQuery({ queryKey: ['products', 'home'], queryFn: () => api('/api/products?page=0&size=8') });
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api('/api/categories') });
  const items = products.data?.content || products.data || [];
  const cat = categories.data || [];

  return <>
    <section className="hero-section">
      <div className="hero-backdrop"><div className="hero-aurora" /><div className="hero-grid" /></div>
      <div className="hero-copy">
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .6 }}><AmbientLabel>THE SHOPSPHERE EDIT</AmbientLabel></motion.div>
        <motion.h1 initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: .08, duration: .7 }}>Find your <span>next favorite.</span></motion.h1>
        <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: .2, duration: .7 }}>A little bit of everything, from people who care<br className="desktop-break" /> about what they make.</motion.p>
        <motion.div className="hero-actions" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: .3 }}><Link className="button button-primary" to="/shop">Explore the edit <ArrowUpRight size={16} /></Link><Link className="hero-sub-link" to="/seller/apply">A place for makers <ArrowRight size={15} /></Link></motion.div>
      </div>
      <motion.div className="hero-art" initial={{ opacity: 0, scale: .96, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }} transition={{ delay: .22, duration: .8, ease: [.16, 1, .3, 1] }}>
        <div className="hero-art-glow" />
        <div className="hero-object hero-object-one"><div className="object-lamp"><span /><i /></div><span className="object-caption">01 — STILL LIFE</span></div>
        <div className="hero-object hero-object-two"><span className="object-disc" /><span className="object-caption">02 — SLOW DAYS</span></div>
        <div className="hero-note"><span>Objects<br />with a point<br />of view.</span><i>CURATED FOR YOU</i></div>
        <div className="hero-index"><span>DISCOVER THE DIFFERENCE</span><b>01 <i>/</i> 04</b></div>
      </motion.div>
      <div className="hero-bottom"><span><CircleDot size={13} /> Independent makers, all in one place</span><a href="#featured">Scroll to discover <ArrowDown size={14} /></a></div>
      <div className="hero-side-mark">THOUGHTFULLY FOUND — EST. MMXXV</div>
    </section>

    <section className="trust-ribbon"><div><BadgeCheck size={17} /><span>Independent sellers</span></div><div><Truck size={17} /><span>Tracked delivery</span></div><div><Compass size={17} /><span>Carefully considered</span></div><div><Sparkles size={17} /><span>Made to be kept</span></div></section>

    <section className="editorial-section page-wrap" id="featured">
      <div className="editorial-intro"><div><span className="eyebrow">GOOD THINGS, FOUND</span><h2>Not more stuff.<br /><em>Better stuff.</em></h2></div><div className="editorial-note"><span className="editorial-asterisk">✳</span><p>Small-batch objects and everyday favorites with a little more thought behind them.</p><Link to="/shop">Get to know the edit <ChevronRight size={14} /></Link></div></div>
      <div className="category-tiles">{categoriesArt.map((item, i) => {
        const match = cat[i];
        return <Link key={item.title} to={match ? `/shop?categoryId=${match.id}` : '/shop'} className={`category-tile ${item.art}`}><span className="category-index">{item.icon} / 03</span><div className="tile-illustration"><i className="tile-shape" /><i className="tile-shape-two" /><span>{item.icon}</span></div><div className="category-tile-copy"><div><h3>{match?.name || item.title}</h3><p>{item.caption}</p></div><span className="tile-arrow"><ArrowUpRight size={17} /></span></div></Link>;
      })}</div>
    </section>

    <section className="featured-section page-wrap">
      <SectionHeading eyebrow="A FEW THINGS WE LOVE" title="Fresh from the edit" link="Shop all finds" />
      {products.isPending ? <LoadingCards count={4} /> : products.isError ? <ErrorState error={products.error} onRetry={() => products.refetch()} /> : items.length ? <div className="product-grid">{items.slice(0, 4).map((p, i) => <ProductCard key={p.id} product={p} index={i} />)}</div> : <div className="subtle-empty">The shelves are being restocked. <Link to="/shop">Take a look around <ArrowRight size={14} /></Link></div>}
    </section>

    <section className="maker-banner page-wrap"><div className="maker-banner-art"><span className="maker-sun" /><span className="maker-line line-a" /><span className="maker-line line-b" /><span className="maker-stamp">M<br />✳<br />K</span></div><div className="maker-banner-copy"><span className="eyebrow">GOOD THINGS START SOMEWHERE</span><h2>For the people<br />who <em>make things.</em></h2><p>Have a studio, a small shop, or a really good idea? There’s room at this table.</p><Link className="button button-light" to="/seller/apply">Bring your shop here <ArrowUpRight size={15} /></Link></div><span className="maker-vertical">MADE WITH INTENTION · FOUND WITH CARE</span></section>

    <section className="bottom-note page-wrap"><span>FIND THE THINGS YOU’LL KEEP</span><Link to="/shop">Take the long way around <ArrowUpRight size={15} /></Link></section>
  </>;
}

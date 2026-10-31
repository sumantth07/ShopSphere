import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowUpRight, Check, Heart, Scale, ShoppingBag, Star } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { motion } from 'motion/react';
import { api, assetUrl } from '../api/client.js';
import { useAuth } from '../api/auth.jsx';
import { money } from '../lib/format.js';

const compareSet = new Set(JSON.parse(localStorage.getItem('shopsphere.compare') || '[]'));
function productUrl(p) { return assetUrl(p.imageUrl || p.image || ''); }

export default function ProductCard({ product, index = 0, onCompare }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { user } = useAuth();
  const productId = Number(product.id);
  const wishlistQuery = useQuery({ queryKey: ['wishlist-ids'], queryFn: () => api('/api/wishlist/ids'), enabled: !!user });
  const ids = JSON.parse(localStorage.getItem('shopsphere.compare') || '[]');
  const addCart = useMutation({ mutationFn: () => api('/api/cart/items', { method: 'POST', body: { productId, quantity: 1 } }), onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['cart'] }); toast.success('Added to your bag'); }, onError: (e) => { if (e.status === 401) navigate('/login'); else toast.error(e.message || 'Could not add this item'); } });
  const wish = useMutation({ mutationFn: async () => { const current = await api('/api/wishlist/ids'); const exists = (current || []).map(Number).includes(productId); return exists ? api(`/api/wishlist/items/${productId}`, { method: 'DELETE' }) : api('/api/wishlist', { method: 'POST', body: { productId } }); }, onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['wishlist'] }); queryClient.invalidateQueries({ queryKey: ['wishlist-ids'] }); toast.success('Wishlist updated'); }, onError: (e) => { if (e.status === 401) navigate('/login'); else toast.error(e.message); } });
  const isWished = (wishlistQuery.data || []).map(Number).includes(productId);
  const compare = () => {
    const next = new Set(ids);
    if (next.has(productId)) next.delete(productId);
    else if (next.size >= 4) return toast('Compare up to 4 products');
    else next.add(productId);
    localStorage.setItem('shopsphere.compare', JSON.stringify([...next]));
    onCompare?.([...next]);
    toast.success(next.has(productId) ? 'Added to compare' : 'Removed from compare');
  };

  return <motion.article className="product-card" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-40px' }} transition={{ delay: Math.min(index * .035, .2), duration: .42 }}>
    <div className="product-card-media">
      <Link to={`/product/${productId}`} aria-label={`View ${product.name}`} className="product-image-link">
        {productUrl(product) ? <img src={productUrl(product)} alt={product.name} loading="lazy" onError={(e) => {e.currentTarget.style.display='none'; e.currentTarget.nextElementSibling.style.display='grid';}} /> : null}
        <div className="product-image-fallback" style={{display: productUrl(product) ? 'none' : 'grid'}}><span>{product.brand?.slice(0,1) || 'S'}</span><i>{product.category || 'Find'}</i></div>
      </Link>
      <span className={`stock-tag ${product.inStock === false || product.stockQuantity === 0 ? 'stock-out' : ''}`}>{product.inStock === false || product.stockQuantity === 0 ? 'Sold out' : 'In the edit'}</span>
      <div className="product-card-tools"><button className={`round-tool ${isWished ? 'is-active' : ''}`} aria-label="Save to wishlist" onClick={() => wish.mutate()}><Heart size={17} fill={isWished ? 'currentColor' : 'none'} /></button><button className={`round-tool ${ids.includes(productId) ? 'is-active' : ''}`} aria-label="Add to comparison" onClick={compare}><Scale size={16} /></button></div>
      <Link className="quick-view" to={`/product/${productId}`}>Take a closer look <ArrowUpRight size={14} /></Link>
    </div>
    <div className="product-card-copy"><div className="product-card-meta"><span>{product.brand || product.sellerName || 'Independent maker'}</span>{Number(product.rating) > 0 && <span className="rating"><Star size={12} fill="currentColor" /> {Number(product.rating).toFixed(1)}</span>}</div><Link to={`/product/${productId}`} className="product-title">{product.name}</Link><div className="product-card-bottom"><div><strong>{money(product.price)}</strong><span>{product.category || 'Selected find'}</span></div><button className="add-bag-icon" title="Add to bag" disabled={product.inStock === false || product.stockQuantity === 0 || addCart.isPending} onClick={() => addCart.mutate()}><ShoppingBag size={17} />{addCart.isSuccess && <Check className="added-check" size={12} />}</button></div></div>
  </motion.article>;
}

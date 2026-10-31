import { useEffect, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ChevronDown, Search, SlidersHorizontal, X } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { api, query } from '../api/client.js';
import ProductCard from '../components/ProductCard.jsx';
import { ErrorState, LoadingCards, PageIntro } from '../components/ui.jsx';

export default function Shop() {
  const [urlParams, setUrlParams] = useSearchParams();
  const [search, setSearch] = useState(urlParams.get('q') || '');
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [filters, setFilters] = useState({ categoryId: urlParams.get('categoryId') || '', brand: urlParams.get('brand') || '', inStock: urlParams.get('inStock') === 'true', minRating: '', minPrice: '', maxPrice: '', sort: urlParams.get('sort') || 'featured' });
  const [page, setPage] = useState(0);
  useEffect(() => { const t = setTimeout(() => { if (search !== (urlParams.get('q') || '')) { setPage(0); setUrlParams(p => { search ? p.set('q', search) : p.delete('q'); return p; }, { replace: true }); } }, 320); return () => clearTimeout(t); }, [search]);
  const sortOptions = { featured: {}, newest: { sort: 'createdAt', dir: 'desc' }, 'price-low': { sort: 'price', dir: 'asc' }, 'price-high': { sort: 'price', dir: 'desc' }, rating: { sort: 'rating', dir: 'desc' } };
  const params = { q: search.trim(), categoryId: filters.categoryId, brand: filters.brand, inStock: filters.inStock ? true : '', minRating: filters.minRating, minPrice: filters.minPrice, maxPrice: filters.maxPrice, page, size: 12, ...sortOptions[filters.sort] };
  const products = useQuery({ queryKey: ['products', params], queryFn: () => api(`/api/products${query(params)}`), placeholderData: keepPreviousData });
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api('/api/categories') });
  const brands = useQuery({ queryKey: ['brands'], queryFn: () => api('/api/brands?limit=100') });
  const list = products.data?.content || products.data || [];
  const totalPages = products.data?.totalPages ?? 1;
  const total = products.data?.totalElements ?? list.length;
  const update = (key, value) => { setFilters(current => ({ ...current, [key]: value })); setPage(0); };
  const reset = () => { setSearch(''); setFilters({ categoryId:'',brand:'',inStock:false,minRating:'',minPrice:'',maxPrice:'',sort:'featured' }); setPage(0); };
  const activeCount = Object.entries(filters).filter(([k,v]) => k !== 'sort' && v && v !== false).length;

  return <div className="page-wrap shop-page">
    <PageIntro eyebrow="THE SHOPSPHERE EDIT" title={<>Find your <em>next favorite.</em></>} description="A little bit of everything, from people who care about what they make." action={<div className="catalog-status"><span>{total.toLocaleString('en-IN')} things to discover</span><span className="live-dot"><i/>LIVE CATALOG</span></div>} />
    <div className="catalog-toolbar"><label className="catalog-search"><Search size={18}/><input placeholder="Search products, brands, little obsessions…" value={search} onChange={e=>setSearch(e.target.value)}/>{search && <button onClick={()=>setSearch('')} aria-label="Clear search"><X size={16}/></button>}<kbd>⌘ K</kbd></label><button className={`filter-toggle ${filtersOpen?'is-active':''}`} onClick={()=>setFiltersOpen(!filtersOpen)}><SlidersHorizontal size={16}/> Filters {activeCount > 0 && <span>{activeCount}</span>}<ChevronDown size={14}/></button><label className="sort-select"><span>Sort:</span><select value={filters.sort} onChange={e=>update('sort',e.target.value)}><option value="featured">Featured</option><option value="newest">Just added</option><option value="price-low">Price: low to high</option><option value="price-high">Price: high to low</option><option value="rating">Top rated</option></select><ChevronDown size={14}/></label><label className="stock-filter"><input type="checkbox" checked={filters.inStock} onChange={e=>update('inStock',e.target.checked)}/> In stock</label></div>
    {filtersOpen && <div className="filter-panel"><label>Category<select value={filters.categoryId} onChange={e=>update('categoryId',e.target.value)}><option value="">All categories</option>{(categories.data||[]).map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label><label>Brand<select value={filters.brand} onChange={e=>update('brand',e.target.value)}><option value="">All makers</option>{(brands.data||[]).map(b=><option key={b.brand} value={b.brand}>{b.brand}</option>)}</select></label><label>Price from<div className="price-input"><span>₹</span><input type="number" min="0" placeholder="0" value={filters.minPrice} onChange={e=>update('minPrice',e.target.value)}/></div></label><label>Price to<div className="price-input"><span>₹</span><input type="number" min="0" placeholder="Any" value={filters.maxPrice} onChange={e=>update('maxPrice',e.target.value)}/></div></label><label>Minimum rating<select value={filters.minRating} onChange={e=>update('minRating',e.target.value)}><option value="">Any rating</option><option value="3">3+ stars</option><option value="4">4+ stars</option><option value="4.5">4.5+ stars</option></select></label><button className="filter-reset" onClick={reset}>Clear all</button></div>}
    <div className="catalog-result-line"><span>{search ? <>Results for <b>“{search}”</b></> : filters.categoryId ? (categories.data||[]).find(c=>String(c.id)===String(filters.categoryId))?.name || 'Browse the edit' : 'A thoughtful mix, just for you'}</span><span>{total.toLocaleString('en-IN')} {total === 1 ? 'find':'finds'}</span></div>
    {products.isPending ? <LoadingCards count={8}/> : products.isError ? <ErrorState error={products.error} onRetry={()=>products.refetch()}/> : list.length ? <><div className="product-grid">{list.map((p,i)=><ProductCard key={p.id} product={p} index={i}/>)}</div>{totalPages > 1 && <div className="pagination"><button disabled={page===0} onClick={()=>{setPage(page-1);window.scrollTo({top:0,behavior:'smooth'});}}>← Previous</button><span>Page <b>{page+1}</b> of {totalPages}</span><button disabled={page+1>=totalPages} onClick={()=>{setPage(page+1);window.scrollTo({top:0,behavior:'smooth'});}}>Next →</button></div>}</> : <div className="catalog-empty"><div className="empty-orbit"><Search size={24}/></div><h3>Nothing on this shelf just yet.</h3><p>Try a broader search or clear a filter. Your next favorite is around here somewhere.</p><button className="button button-secondary" onClick={reset}>Clear the filters</button></div>}
  </div>;
}

import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, ArrowRight, Scale, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { api, assetUrl, query } from '../api/client.js';
import { useAuth } from '../api/auth.jsx';
import ProductCard from '../components/ProductCard.jsx';
import { EmptyState, ErrorState, LoadingCards, PageIntro } from '../components/ui.jsx';
import { money } from '../lib/format.js';

export function Wishlist(){
 const {user}=useAuth();const list=useQuery({queryKey:['wishlist'],queryFn:()=>api('/api/wishlist'),enabled:!!user});
 if(!user)return <div className="page-wrap auth-required"><PageIntro eyebrow="SAVED FOR LATER" title="Keep your favorites close." description="Sign in to collect the things you love."/><Link className="button button-primary" to="/login">Sign in <ArrowRight size={15}/></Link></div>;
 if(list.isPending)return <div className="page-wrap"><LoadingCards/></div>;
 if(list.isError)return <div className="page-wrap"><ErrorState error={list.error} onRetry={()=>list.refetch()}/></div>;
 const items=Array.isArray(list.data)?list.data:list.data?.content||[];
 return <div className="page-wrap collection-page"><PageIntro eyebrow="A LITTLE COLLECTION" title={<>Saved <em>finds.</em></>} description="The things you liked enough to keep close." action={<Link to="/shop" className="button button-secondary">Find something else <ArrowRight size={14}/></Link>}/>{items.length?<div className="product-grid">{items.map((p,i)=><ProductCard key={p.id} product={p} index={i}/>)}</div>:<EmptyState icon={Sparkles} title="Your collection is waiting for its first find." text="Tap the heart on anything that catches your eye." action={<Link className="button button-primary" to="/shop">Explore the edit <ArrowRight size={15}/></Link>}/>}</div>;
}

export function Compare(){
 const ids=JSON.parse(localStorage.getItem('shopsphere.compare')||'[]');const data=useQuery({queryKey:['compare',ids],queryFn:()=>api(`/api/products/compare${query({ids})}`),enabled:ids.length>0});
 const clear=()=>{localStorage.setItem('shopsphere.compare','[]');window.location.reload()};
 if(!ids.length)return <div className="page-wrap compare-page"><PageIntro eyebrow="MAKE A GOOD CALL" title="Compare your <em>shortlist.</em>" description="Add up to four finds and see how they stack up."/><EmptyState icon={Scale} title="A little comparison goes a long way." text="Use the scale icon on product cards to build a shortlist." action={<Link className="button button-primary" to="/shop">Browse the edit <ArrowRight size={15}/></Link>}/></div>;
 if(data.isPending)return <div className="page-wrap"><LoadingCards count={ids.length}/></div>;
 if(data.isError)return <div className="page-wrap"><ErrorState error={data.error} onRetry={()=>data.refetch()}/></div>;
 const products=data.data||[];
 return <div className="page-wrap compare-page"><div className="compare-heading"><PageIntro eyebrow="MAKE A GOOD CALL" title={<>Compare your <em>shortlist.</em></>} description="A clearer view of the things you love."/><button className="text-button" onClick={clear}>Clear comparison</button></div><div className="comparison-table"><div className="comparison-row comparison-product-row"><div className="comparison-label">YOUR SHORTLIST</div>{products.map(p=><div className="compare-product" key={p.id}><Link to={`/product/${p.id}`}>{p.imageUrl?<img src={assetUrl(p.imageUrl)} alt={p.name}/>:<span className="compare-placeholder">{p.brand?.slice(0,1)||'S'}</span>}<small>{p.brand||p.sellerName}</small><b>{p.name}</b></Link><strong>{money(p.price)}</strong><Link className="compare-detail-link" to={`/product/${p.id}`}>See this find <ArrowRight size={13}/></Link></div>)}</div>{[['Brand',p=>p.brand],['Category',p=>p.category],['Maker',p=>p.sellerName],['Rating',p=>p.rating?`${Number(p.rating).toFixed(1)} / 5 · ${p.reviewCount||0} reviews`:'Not rated yet'],['Availability',p=>p.inStock?'In the edit':'Currently unavailable'],['In stock',p=>p.stockQuantity??'—'],['Description',p=>p.description]].map(([label,get])=><div className="comparison-row" key={label}><div className="comparison-label">{label}</div>{products.map(p=><div className="comparison-value" key={p.id}>{get(p)||'—'}</div>)}</div>)}</div></div>;
}

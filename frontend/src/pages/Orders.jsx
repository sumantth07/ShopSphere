import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ArrowRight, Box, CheckCircle2, ChevronRight, Clock3, MapPin, PackageCheck, PackageOpen, Truck } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { api, assetUrl } from '../api/client.js';
import { useAuth } from '../api/auth.jsx';
import { dateShort, money } from '../lib/format.js';
import { Button, EmptyState, ErrorState, PageIntro } from '../components/ui.jsx';

const statusText = (value='') => value.toLowerCase().replaceAll('_',' ').replace(/\b\w/g, s=>s.toUpperCase());
const Status = ({value}) => <span className={`status-pill status-${String(value||'').toLowerCase().replaceAll('_','-')}`}><i/>{statusText(value||'Processing')}</span>;

export function Orders(){
 const {user}=useAuth();const orders=useQuery({queryKey:['orders'],queryFn:()=>api('/api/orders'),enabled:!!user});
 if(!user)return <div className="page-wrap auth-required"><PageIntro eyebrow="YOUR JOURNEY" title="Every good find, in one place." description="Sign in to follow your orders from checkout to doorstep."/><Link className="button button-primary" to="/login">Sign in <ArrowRight size={15}/></Link></div>;
 if(orders.isPending)return <div className="page-wrap"><div className="skeleton-line"/></div>;
 if(orders.isError)return <div className="page-wrap"><ErrorState error={orders.error} onRetry={()=>orders.refetch()}/></div>;
 const list=orders.data||[];
 return <div className="page-wrap orders-page"><PageIntro eyebrow="GOOD THINGS ON THEIR WAY" title="Your orders." description="Every parcel has a little story. Here’s where yours are." action={<Link className="button button-secondary" to="/shop">Find something new <ArrowRight size={15}/></Link>}/>{list.length?<div className="orders-list">{list.map(order=><Link to={`/orders/${order.id}`} className="order-card" key={order.id}><div className="order-thumb">{order.firstImage?<img src={order.firstImage} alt=""/>:<PackageOpen size={22}/>}</div><div className="order-card-main"><div className="order-card-top"><span>ORDER #{String(order.id).padStart(6,'0')}</span><Status value={order.status}/></div><strong>{money(order.total)}</strong><small>{order.itemCount} {order.itemCount===1?'item':'items'} · Placed {dateShort(order.createdAt)}</small></div><ChevronRight size={18}/></Link>)}</div>:<EmptyState icon={PackageCheck} title="Your story starts with a find." text="Orders you place will show up right here." action={<Link to="/shop" className="button button-primary">Explore the edit <ArrowRight size={15}/></Link>}/>}</div>;
}

export function OrderDetail(){
 const {id}=useParams();const {user}=useAuth();const qc=useQueryClient();const order=useQuery({queryKey:['order',id],queryFn:()=>api(`/api/orders/${id}`),enabled:!!user});
 const cancel=useMutation({mutationFn:groupId=>api(`/api/orders/${id}/shipments/${groupId}/cancel`,{method:'POST',body:{reason:'Customer requested cancellation'}}),onSuccess:()=>{qc.invalidateQueries({queryKey:['order',id]});qc.invalidateQueries({queryKey:['orders']});toast.success('Cancellation request sent')},onError:e=>toast.error(e.message)});
 if(!user)return <div className="page-wrap auth-required"><Link to="/login" className="button button-primary">Sign in to view this order</Link></div>;
 if(order.isPending)return <div className="page-wrap"><div className="skeleton-line"/></div>;
 if(order.isError)return <div className="page-wrap"><ErrorState error={order.error} onRetry={()=>order.refetch()}/></div>;
 const o=order.data||{};
 return <div className="page-wrap order-detail-page"><Link to="/orders" className="back-link"><ArrowLeft size={14}/> All orders</Link><div className="order-detail-head"><div><span className="eyebrow">A LITTLE UPDATE</span><h1>Order <em>#{String(o.id).padStart(6,'0')}</em></h1><p>Placed {dateShort(o.createdAt)} · {o.demo?'Demo order':'Marketplace order'}</p></div><Status value={o.status}/></div><div className="order-detail-layout"><main className="order-detail-main"><section className="order-detail-card"><div className="order-section-heading"><div><span className="eyebrow">PARCELS IN THIS ORDER</span><h2>Coming from your makers</h2></div></div>{(o.groups||[]).map(group=><div className="shipment-group" key={group.id}><div className="shipment-head"><span className="seller-avatar"><Truck size={16}/></span><div><b>{group.sellerName||'Independent seller'}</b><small>Estimated arrival {dateShort(group.estimatedDelivery)}</small></div><Status value={group.status}/></div><div className="order-items">{(group.items||[]).map(item=><div className="order-item-row" key={item.id}><div className="order-item-image">{item.imageUrl?<img src={assetUrl(item.imageUrl)} alt=""/>:<Box size={18}/>}</div><div><b>{item.name}</b><small>Qty {item.quantity} · {money(item.unitPrice)} each</small></div><strong>{money(item.unitPrice*item.quantity)}</strong></div>)}</div>{(group.history||[]).length>0&&<div className="shipment-timeline">{group.history.map((h,i)=><div className="timeline-item" key={`${h.status}-${h.at}-${i}`}><span className={i===0?'timeline-dot active':''}/><div><b>{statusText(h.status)}</b><small>{h.note} · {dateShort(h.at)}</small></div></div>)}</div>}{group.cancellable&&<button className="cancel-order-link" onClick={()=>cancel.mutate(group.id)} disabled={cancel.isPending}>{cancel.isPending?'Requesting…':'Cancel this parcel'}</button>}</div>)}</section><section className="order-detail-card delivery-card"><span className="eyebrow">DELIVERING TO</span><h3>{o.shipTo?.name}</h3><p>{o.shipTo?.line1}{o.shipTo?.line2?`, ${o.shipTo.line2}`:''}<br/>{o.shipTo?.city}, {o.shipTo?.state} {o.shipTo?.postalCode}<br/>{o.shipTo?.country}</p><div className="delivery-phone">{o.shipTo?.phone}</div></section></main><aside className="cart-summary"><span className="eyebrow">ORDER TOTAL</span><h3>A quick recap</h3><div><span>Items</span><b>{money(o.subtotal)}</b></div><div><span>Delivery</span><b>{money(o.shippingTotal)}</b></div><div><span>Payment</span><span>{statusText(o.paymentMethod)} · {statusText(o.paymentStatus)}</span></div><div className="summary-total"><span>Total paid</span><b>{money(o.total)}</b></div><div className="order-help"><Clock3 size={15}/><span>Need a hand? <a href="mailto:hello@shopsphere.local">We’re here.</a></span></div></aside></div></div>;
}



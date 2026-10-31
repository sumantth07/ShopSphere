import { lazy, Suspense, useEffect } from 'react';
import { Link, Route, Routes, useLocation } from 'react-router-dom';
import Layout from './components/Layout.jsx';
const Home = lazy(() => import('./pages/Home.jsx'));
const Shop = lazy(() => import('./pages/Shop.jsx'));
const Product = lazy(() => import('./pages/Product.jsx'));
const Cart = lazy(() => import('./pages/Cart.jsx'));
const Checkout = lazy(() => import('./pages/Cart.jsx').then(m => ({ default: m.Checkout })));
const Account = lazy(() => import('./pages/Account.jsx'));
const Orders = lazy(() => import('./pages/Orders.jsx').then(m => ({ default: m.Orders })));
const OrderDetail = lazy(() => import('./pages/Orders.jsx').then(m => ({ default: m.OrderDetail })));
const Wishlist = lazy(() => import('./pages/Collections.jsx').then(m => ({ default: m.Wishlist })));
const Compare = lazy(() => import('./pages/Collections.jsx').then(m => ({ default: m.Compare })));
const Seller = lazy(() => import('./pages/Seller.jsx'));
const Admin = lazy(() => import('./pages/Admin.jsx'));
const Login = lazy(() => import('./pages/Auth.jsx').then(m => ({ default: m.Login })));
const Register = lazy(() => import('./pages/Auth.jsx').then(m => ({ default: m.Register })));
const VerifyEmail = lazy(() => import('./pages/Auth.jsx').then(m => ({ default: m.VerifyEmail })));
const ForgotPassword = lazy(() => import('./pages/Auth.jsx').then(m => ({ default: m.ForgotPassword })));
const ResetPassword = lazy(() => import('./pages/Auth.jsx').then(m => ({ default: m.ResetPassword })));
const AuthCallback = lazy(() => import('./pages/Auth.jsx').then(m => ({ default: m.AuthCallback })));
const GoogleLinkConfirm = lazy(() => import('./pages/Auth.jsx').then(m => ({ default: m.GoogleLinkConfirm })));

function ScrollToTop(){const {pathname}=useLocation();useEffect(()=>{window.scrollTo(0,0)},[pathname]);return null}
function NotFound(){return <div className="page-wrap not-found"><span className="eyebrow">A LITTLE DETOUR</span><h1>We can’t find<br/><em>that shelf.</em></h1><p>It may have moved. Let’s find your way back to good things.</p><Link className="button button-primary" to="/">Back to ShopSphere</Link></div>}

export default function App(){
 return <><ScrollToTop/><Suspense fallback={<div className="page-loading"><span className="loading-mark"/><span>Setting the scene…</span></div>}><Routes>
  <Route element={<Layout/>}>
   <Route index element={<Home/>}/><Route path="shop" element={<Shop/>}/><Route path="product/:id" element={<Product/>}/>
   <Route path="cart" element={<Cart/>}/><Route path="checkout" element={<Checkout/>}/>
   <Route path="account" element={<Account/>}/><Route path="orders" element={<Orders/>}/><Route path="orders/:id" element={<OrderDetail/>}/>
   <Route path="wishlist" element={<Wishlist/>}/><Route path="compare" element={<Compare/>}/>
   <Route path="seller/*" element={<Seller/>}/><Route path="admin/*" element={<Admin/>}/>
   <Route path="login" element={<Login/>}/><Route path="register" element={<Register/>}/><Route path="verify-email" element={<VerifyEmail/>}/><Route path="forgot-password" element={<ForgotPassword/>}/><Route path="reset-password" element={<ResetPassword/>}/>
   <Route path="auth/callback" element={<AuthCallback/>}/>
   <Route path="auth/link" element={<GoogleLinkConfirm/>}/>
   <Route path="*" element={<NotFound/>}/>
  </Route>
 </Routes></Suspense></>;
}


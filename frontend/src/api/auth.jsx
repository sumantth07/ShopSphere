import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, tokenStorage } from './client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  const accept = useCallback((result) => {
    if (result?.accessToken) tokenStorage.set(result.accessToken);
    if (result?.user) setUser(result.user);
    return result;
  }, []);

  const signOut = useCallback(async () => {
    try { await api('/api/auth/logout', { method: 'POST', retryAuth: false }); } catch { /* local sign-out still succeeds */ }
    tokenStorage.set(null);
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    let active = true;
    async function restore() {
      try {
        if (!tokenStorage.get()) return;
        const current = await api('/api/users/me');
        if (active) setUser(current);
      } catch {
        if (active) { tokenStorage.set(null); setUser(null); }
      } finally { if (active) setReady(true); }
    }
    restore();
    return () => { active = false; };
  }, []);

  const value = useMemo(() => ({
    user, ready, accept, signOut,
    async login(email, password) { return accept(await api('/api/auth/login', { method: 'POST', body: { email, password } })); },
    async verifyEmail(email, code) { return accept(await api('/api/auth/verify-email', { method: 'POST', body: { email, code } })); },
    async register(values) { return api('/api/auth/register', { method: 'POST', body: values }); },
  }), [user, ready, accept, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

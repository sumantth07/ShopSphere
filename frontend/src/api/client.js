const configuredBase = import.meta.env.VITE_API_BASE_URL || 'https://shopsphere-backend-a59o.onrender.com';
export const API_BASE = configuredBase.replace(/\/+$/, '');
export function assetUrl(value) {
  if (!value) return '';
  return value.startsWith('/uploads/') ? `${API_BASE}${value}` : value;
}

function readToken() {
  return sessionStorage.getItem('shopsphere.accessToken');
}

function saveToken(token) {
  if (token) sessionStorage.setItem('shopsphere.accessToken', token);
  else sessionStorage.removeItem('shopsphere.accessToken');
}

async function decode(response) {
  const text = await response.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return text; }
}

export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
  }
}

export async function api(path, options = {}) {
  const { method = 'GET', body, headers = {}, retryAuth = true, ...rest } = options;
  const requestHeaders = new Headers(headers);
  const token = readToken();
  if (token && !requestHeaders.has('Authorization')) requestHeaders.set('Authorization', `Bearer ${token}`);
  if (path === '/api/auth/refresh' && !requestHeaders.has('X-Requested-With')) {
    requestHeaders.set('X-Requested-With', 'ShopSphere');
  }
  if (body !== undefined && !(body instanceof FormData) && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json');
  }
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: requestHeaders,
    credentials: ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'].includes(path) ? 'include' : 'omit',
    body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
    ...rest,
  });
  const payload = await decode(response);

  if (response.status === 401 && retryAuth && path !== '/api/auth/refresh' && token) {
    try {
      const refresh = await api('/api/auth/refresh', { method: 'POST', retryAuth: false });
      if (refresh?.accessToken) {
        saveToken(refresh.accessToken);
        return api(path, { ...options, retryAuth: false });
      }
    } catch { saveToken(null); }
  }

  if (!response.ok) {
    const message = typeof payload === 'string' ? payload : payload?.message || payload?.error || `Request failed (${response.status})`;
    throw new ApiError(message, response.status, payload);
  }
  if (payload?.accessToken) saveToken(payload.accessToken);
  return payload;
}

export const tokenStorage = { get: readToken, set: saveToken };

export function query(values = {}) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) value.forEach((item) => params.append(key, item));
    else params.set(key, String(value));
  });
  const result = params.toString();
  return result ? `?${result}` : '';
}

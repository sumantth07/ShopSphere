export const money = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(Number(value || 0));
export const dateShort = (value) => value ? new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value)) : '—';
export const initials = (value = '') => value.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'S';
export const compact = (value) => new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 }).format(Number(value || 0));
export const pageRows = (value) => Array.isArray(value) ? value : value?.content || [];
export const errorText = (error, fallback = 'Something went wrong. Please try again.') => error?.message || fallback;

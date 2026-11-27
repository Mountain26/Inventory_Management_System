const ROLES = ['ADMIN', 'MANAGER', 'STAFF', 'SUPPLIER_CONTACT', 'CUSTOMER'];

// Client-side routing only; the backend verifies the signature and permissions.
export function readSession(token) {
  try {
    const encoded = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(encoded), c => c.charCodeAt(0))));
    const role = (payload.roles?.[0] || payload.role || '').replace('ROLE_', '');
    if (!payload.sub || !Number.isFinite(payload.exp) || payload.exp * 1000 <= Date.now() || !ROLES.includes(role)) return null;
    return { ...payload, role };
  } catch {
    return null;
  }
}

export function homeForRole(role) {
  if (role === 'SUPPLIER_CONTACT') return '/portal/supplier';
  if (role === 'CUSTOMER') return '/portal/customer';
  return '/dashboard';
}

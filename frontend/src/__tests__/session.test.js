import { describe, expect, it } from 'vitest';
import { readSession, homeForRole } from '../utils/session';

describe('session routing', () => {
  it('rejects invalid tokens and unrecognized roles', () => {
    expect(readSession('broken')).toBeNull();
    const payload = { sub: 'test@example.com', roles: ['ROLE_SUPERUSER'], exp: Date.now() / 1000 + 60 };
    expect(readSession(`header.${btoa(JSON.stringify(payload))}.signature`)).toBeNull();
  });
  it('routes external users to their own portals', () => {
    expect(homeForRole('CUSTOMER')).toBe('/portal/customer');
    expect(homeForRole('SUPPLIER_CONTACT')).toBe('/portal/supplier');
    expect(homeForRole('STAFF')).toBe('/dashboard');
  });
});

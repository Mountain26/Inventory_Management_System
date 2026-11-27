import { describe, beforeEach, it, expect } from 'vitest';

import { render, screen, act } from '@testing-library/react';

import { AuthProvider } from '../context/AuthContext';
import { useAuth } from '../context/auth';

const validToken = `header.${btoa(JSON.stringify({ sub: 'admin@test.com', roles: ['ROLE_ADMIN'], exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`;

// A helper component to test AuthContext
function TestComponent() {
  const { isAuthenticated, userRole, userEmail, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="auth">{isAuthenticated ? 'yes' : 'no'}</span>
      <span data-testid="role">{userRole}</span>
      <span data-testid="email">{userEmail}</span>
      <button onClick={() => login(validToken)}>Login</button>
      <button onClick={logout}>Logout</button>
    </div>
  );
}

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('starts unauthenticated when no token', () => {
    render(<AuthProvider><TestComponent /></AuthProvider>);
    expect(screen.getByTestId('auth').textContent).toBe('no');
  });

  it('becomes authenticated after login', () => {
    render(<AuthProvider><TestComponent /></AuthProvider>);
    
    act(() => {
      screen.getByText('Login').click();
    });

    expect(screen.getByTestId('auth').textContent).toBe('yes');
  });

  it('extracts role from JWT payload', () => {
    render(<AuthProvider><TestComponent /></AuthProvider>);
    
    act(() => {
      screen.getByText('Login').click();
    });

    // The mock token has roles: ["ROLE_ADMIN"] in payload
    expect(screen.getByTestId('role').textContent).toBe('ADMIN');
  });

  it('becomes unauthenticated after logout', () => {
    render(<AuthProvider><TestComponent /></AuthProvider>);
    
    act(() => screen.getByText('Login').click());
    expect(screen.getByTestId('auth').textContent).toBe('yes');
    
    act(() => screen.getByText('Logout').click());
    expect(screen.getByTestId('auth').textContent).toBe('no');
  });

  it('ends the session when the API rejects the token', () => {
    render(<AuthProvider><TestComponent /></AuthProvider>);
    act(() => screen.getByText('Login').click());
    act(() => window.dispatchEvent(new Event('mountain:unauthorized')));
    expect(screen.getByTestId('auth').textContent).toBe('no');
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('rejects an expired token from storage', () => {
    localStorage.setItem('token', `header.${btoa(JSON.stringify({ sub: 'admin@test.com', roles: ['ROLE_ADMIN'], exp: 1 }))}.sig`);
    render(<AuthProvider><TestComponent /></AuthProvider>);
    expect(screen.getByTestId('auth').textContent).toBe('no');
  });
});

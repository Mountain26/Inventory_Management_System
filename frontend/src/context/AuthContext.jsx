import { useState, useEffect } from 'react';
import { AuthContext } from './auth';
import { readSession } from '../utils/session';


export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => {
    const stored = localStorage.getItem('token');
    return readSession(stored) ? stored : null;
  });
  const payload = readSession(token);

  const login = (newToken) => {
    if (!readSession(newToken)) throw new Error('Invalid or expired session');
    localStorage.setItem('token', newToken);
    setToken(newToken);
  };

  const logout = () => {
    localStorage.removeItem('token');
    setToken(null);
  };

  useEffect(() => {
    const endSession = () => {
      localStorage.removeItem('token');
      setToken(null);
    };
    window.addEventListener('mountain:unauthorized', endSession);
    const session = readSession(token);
    const timer = session ? setTimeout(endSession, Math.min(session.exp * 1000 - Date.now(), 2147483647)) : null;
    return () => {
      window.removeEventListener('mountain:unauthorized', endSession);
      if (timer !== null) clearTimeout(timer);
    };
  }, [token]);

  const isAuthenticated = !!payload;
  const userEmail = payload?.sub || '';
  const userRole = payload?.role || '';
  const linkedSupplierId = payload?.linkedSupplierId || null;

  return (
    <AuthContext.Provider value={{ token, login, logout, isAuthenticated, userRole, userEmail, linkedSupplierId }}>
      {children}
    </AuthContext.Provider>
  );
}

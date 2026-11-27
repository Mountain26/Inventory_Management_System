import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/auth';
import { homeForRole } from '../utils/session';

export default function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, userRole } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (roles && roles.length > 0 && !roles.includes(userRole)) {
    return <Navigate to={homeForRole(userRole)} replace />;
  }

  return children;
}

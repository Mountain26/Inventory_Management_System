import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import Login from './pages/Login';
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Categories = lazy(() => import('./pages/Categories'));
const Products = lazy(() => import('./pages/Products'));
const StockTransactions = lazy(() => import('./pages/StockTransactions'));
const Warehouses = lazy(() => import('./pages/Warehouses'));
const WarehouseTransfers = lazy(() => import('./pages/WarehouseTransfers'));
const PurchaseOrders = lazy(() => import('./pages/PurchaseOrders'));
const Invoices = lazy(() => import('./pages/Invoices'));
const Suppliers = lazy(() => import('./pages/Suppliers'));
const SupplierReport = lazy(() => import('./pages/SupplierReport'));
const Notifications = lazy(() => import('./pages/Notifications'));
const SupplierPortal = lazy(() => import('./pages/SupplierPortal'));
const CustomerPortal = lazy(() => import('./pages/CustomerPortal'));

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Suspense fallback={<p role="status" className="p-8">Loading page…</p>}>
        <Routes>
          <Route path="/login" element={<Login />} />
          
          <Route path="/dashboard" element={<ProtectedRoute roles={['ADMIN', 'MANAGER', 'STAFF']}><Layout><Dashboard /></Layout></ProtectedRoute>} />
          <Route path="/products" element={<ProtectedRoute roles={['ADMIN', 'MANAGER', 'STAFF']}><Layout><Products /></Layout></ProtectedRoute>} />
          <Route path="/categories" element={<ProtectedRoute roles={['ADMIN', 'MANAGER', 'STAFF']}><Layout><Categories /></Layout></ProtectedRoute>} />
          <Route path="/stock-transactions" element={<ProtectedRoute roles={['ADMIN', 'MANAGER', 'STAFF']}><Layout><StockTransactions /></Layout></ProtectedRoute>} />
          
          <Route path="/warehouses" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Layout><Warehouses /></Layout></ProtectedRoute>} />
          <Route path="/warehouses/transfers" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Layout><WarehouseTransfers /></Layout></ProtectedRoute>} />
          
          <Route path="/purchase-orders" element={<ProtectedRoute roles={['ADMIN', 'MANAGER', 'STAFF']}><Layout><PurchaseOrders /></Layout></ProtectedRoute>} />
          
          <Route path="/invoices" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Layout><Invoices /></Layout></ProtectedRoute>} />
          <Route path="/suppliers" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Layout><Suppliers /></Layout></ProtectedRoute>} />
          <Route path="/reports/suppliers" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Layout><SupplierReport /></Layout></ProtectedRoute>} />
          <Route path="/notifications" element={<ProtectedRoute roles={['ADMIN', 'MANAGER']}><Layout><Notifications /></Layout></ProtectedRoute>} />
          
          <Route path="/portal/supplier" element={<ProtectedRoute roles={['SUPPLIER_CONTACT']}><Layout><SupplierPortal /></Layout></ProtectedRoute>} />
          <Route path="/portal/customer" element={<ProtectedRoute roles={['CUSTOMER']}><Layout><CustomerPortal /></Layout></ProtectedRoute>} />
          
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </Suspense>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;

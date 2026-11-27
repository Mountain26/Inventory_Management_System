import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';
import BarcodeScanner from '../components/BarcodeScanner';
import { useAuth } from '../context/auth';

export default function StockTransactions() {
  const { userRole, userEmail } = useAuth();
  const canReview = ['ADMIN', 'MANAGER'].includes(userRole);
  const [products, setProducts] = useState([]);
  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState('');
  const [type, setType] = useState('STOCK_IN');
  const [reason, setReason] = useState('');
  const [linkedTransactionId, setLinkedTransactionId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reviewing, setReviewing] = useState(null);
  
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [history, setHistory] = useState([]);
  const [showScanner, setShowScanner] = useState(false);
  const [loading, setLoading] = useState(false);
  
  const [pendingAdjustments, setPendingAdjustments] = useState([]);

  useEffect(() => {
    const init = async () => {
      try {
        const prodRes = await axiosClient.get('/products');
        setProducts(prodRes.data);
        
        // Fetch pending adjustments
        const txnRes = await axiosClient.get('/inventory-transactions');
        setPendingAdjustments(txnRes.data.filter(t => t.status === 'PENDING_APPROVAL'));
      } catch {
        setError('Failed to load initial data');
      }
    };
    init();
  }, []);

  const fetchHistory = async (id) => {
    if (!id) {
      setHistory([]);
      return;
    }
    try {
      const res = await axiosClient.get(`/inventory-transactions/product/${id}`);
      setHistory(res.data);
    } catch {
      setHistory([]);
    } finally {
      setLoading(false);
    }
  };

  const handleProductChange = (e) => {
    const id = e.target.value;
    setProductId(id);
    fetchHistory(id);
  };

  const handleTransaction = async (e) => {
    e.preventDefault();
    if (submitting) return;
    setError('');
    setMessage('');
    if (!Number.isInteger(Number(quantity)) || Number(quantity) === 0) {
      setError('Enter a non-zero whole quantity.');
      return;
    }
    setSubmitting(true);
    try {
      let endpoint = '/inventory-transactions/stock-in';
      const payload = { productId: Number(productId), quantity: Number(quantity) };
      
      if (type === 'STOCK_OUT') {
        endpoint = '/inventory-transactions/stock-out';
      } else if (['ADJUSTMENT', 'DAMAGED', 'LOST', 'CORRECTION'].includes(type)) {
        endpoint = '/inventory-transactions/adjustment';
        payload.type = type;
        payload.reason = reason;
        if (type === 'CORRECTION') payload.linkedTransactionId = Number(linkedTransactionId);
      }
      
      await axiosClient.post(endpoint, payload);
      setMessage(needsReason ? 'Request submitted for approval. Stock has not changed.' : 'Stock movement recorded.');
      setQuantity('');
      setReason('');
      fetchHistory(productId);
      
      // refresh pending if it was an adjustment
      const txnRes = await axiosClient.get('/inventory-transactions');
      setPendingAdjustments(txnRes.data.filter(t => t.status === 'PENDING_APPROVAL'));
      const productRes = await axiosClient.get('/products');
      setProducts(productRes.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Transaction failed');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReview = async (id, action) => {
    if (reviewing !== null) return;
    setReviewing(id);
    setError('');
    try {
      await axiosClient.patch(`/inventory-transactions/${id}/${action}`);
      setPendingAdjustments(previous => previous.filter(item => item.id !== id));
      await fetchHistory(productId);
      const res = await axiosClient.get('/products');
      setProducts(res.data);
      setMessage(action === 'approve' ? 'Adjustment approved and stock updated.' : 'Adjustment rejected. Stock has not changed.');
    } catch (err) {
      setError(err.response?.data?.message || 'Review failed. Refresh and try again.');
    } finally {
      setReviewing(null);
    }
  };

  const handleScan = async (code) => {
    setShowScanner(false);
    try {
      const res = await axiosClient.get('/products/by-barcode', { params: { code } });
      if (res.data) {
        setProductId(res.data.id.toString());
        fetchHistory(res.data.id);
      }
    } catch {
      setError('Product not found for barcode: ' + code);
    }
  };

  const needsReason = ['ADJUSTMENT', 'DAMAGED', 'LOST', 'CORRECTION'].includes(type);
  const isSigned = ['ADJUSTMENT', 'CORRECTION'].includes(type);
  const selectedProduct = products.find(p => p.id.toString() === productId);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-800 mb-6">Stock Transactions</h1>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        <div className="xl:col-span-1 min-w-0 space-y-6">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="text-lg font-bold mb-4">Record Transaction</h2>
            <p className="text-sm text-gray-600 mb-4">Movements use the default warehouse. Receive purchase orders from the Purchase Orders page.</p>
            
            {message && <div className="bg-green-50 text-green-700 border border-green-200 rounded-lg p-3 mb-4 text-sm">{message}</div>}
            {error && <div className="bg-red-50 text-red-700 border border-red-200 rounded-lg p-3 mb-4 text-sm">{error}</div>}
            
            <form onSubmit={handleTransaction} className="space-y-4">
              <div>
                <label htmlFor="stock-product" className="block text-sm font-medium text-gray-700 mb-1">Product *</label>
                <div className="flex gap-2">
                  <select id="stock-product" required value={productId} onChange={handleProductChange}
                    className="min-w-0 flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="">Select...</option>
                    {products.map(p => <option key={p.id} value={p.id}>{p.name} (Qty: {p.currentQuantity})</option>)}
                  </select>
                  <button type="button" onClick={() => setShowScanner(true)} className="shrink-0 bg-gray-100 border border-gray-300 rounded-lg px-3 hover:bg-gray-200" aria-label="Scan barcode" title="Scan Barcode">
                    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M4 7h4l2-3h4l2 3h4v13H4Z" /><circle cx="12" cy="13" r="4" /></svg>
                  </button>
                </div>
              </div>

              <div>
                <label htmlFor="stock-type" className="block text-sm font-medium text-gray-700 mb-1">Type *</label>
                <select id="stock-type" required value={type} onChange={e => setType(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="STOCK_IN">Stock In</option>
                  <option value="STOCK_OUT">Stock Out</option>
                  <option value="ADJUSTMENT">Adjustment (Manual)</option>
                  <option value="DAMAGED">Damaged</option>
                  <option value="LOST">Lost</option>
                  <option value="CORRECTION">Correction of a previous movement</option>
                </select>
              </div>

              <div>
                <label htmlFor="stock-quantity" className="block text-sm font-medium text-gray-700 mb-1">Quantity *</label>
                <input id="stock-quantity" aria-label="Quantity" type="number" required min={isSigned ? undefined : 1} step="1" value={quantity} onChange={e => setQuantity(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>

              {isSigned && <p className="text-sm text-gray-600">Use a positive quantity to add stock or a negative quantity to remove stock.</p>}
              {type === 'CORRECTION' && (
                <div>
                  <label htmlFor="original-transaction" className="block text-sm font-medium text-gray-700 mb-1">Original transaction *</label>
                  <select id="original-transaction" required value={linkedTransactionId} onChange={e => setLinkedTransactionId(e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2">
                    <option value="">Select a completed movement</option>
                    {history.filter(t => t.status === 'COMPLETED').map(t => <option key={t.id} value={t.id}>#{t.id} {t.type} — {t.quantity}</option>)}
                  </select>
                </div>
              )}

              {needsReason && (
                <div>
                  <label htmlFor="stock-reason" className="block text-sm font-medium text-gray-700 mb-1">Reason *</label>
                  <input id="stock-reason" aria-label="Reason" type="text" maxLength={255} required={needsReason} value={reason} onChange={e => setReason(e.target.value)}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
              )}

              <button type="submit" disabled={submitting || !productId || !quantity} className="w-full bg-blue-600 text-white py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50 mt-2 font-medium">
                {submitting ? 'Saving…' : needsReason ? 'Request approval' : 'Record Transaction'}
              </button>
            </form>
          </div>

          {pendingAdjustments.length > 0 && (
            <div className="bg-white rounded-xl shadow-sm border border-amber-200 p-6">
              <h2 className="text-sm font-bold text-amber-800 mb-3">Pending Adjustments</h2>
              <ul className="space-y-2">
                {pendingAdjustments.map(a => (
                  <li key={a.id} className="text-xs border border-amber-100 bg-amber-50 rounded p-2">
                    <span className="font-semibold block">{a.product?.name}</span>
                    Type: {a.type} | Qty: {a.quantity}
                    <p className="mt-1">{a.reason}</p>
                    {canReview && a.performedBy?.email !== userEmail && (
                      <div className="flex gap-4 mt-2">
                        <button disabled={reviewing !== null} onClick={() => handleReview(a.id, 'approve')} className="underline disabled:opacity-50">Approve</button>
                        <button disabled={reviewing !== null} onClick={() => handleReview(a.id, 'reject')} className="underline disabled:opacity-50">Reject</button>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        <div className="xl:col-span-2 min-w-0">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden h-full">
            <div className="bg-gray-50 px-6 py-4 border-b border-gray-200">
              <h2 className="text-lg font-bold text-gray-800">
                Transaction History {selectedProduct ? `- ${selectedProduct.name}` : ''}
              </h2>
            </div>
            {!productId ? (
              <div className="p-12 text-center text-gray-500">Select a product to view its history.</div>
            ) : loading ? (
              <div className="flex justify-center py-12"><div className="animate-spin h-6 w-6 border-4 border-blue-500 border-t-transparent rounded-full"/></div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      {['Type', 'Qty', 'Before', 'After', 'Status', 'Date'].map(h => (
                        <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {history.map(t => (
                      <tr key={t.id} className="hover:bg-gray-50">
                        <td className="px-4 py-3 text-sm font-medium text-gray-800">{t.type}</td>
                        <td className="px-4 py-3 text-sm font-bold text-gray-800">
                          {t.status === 'COMPLETED' ? `${t.quantityAfter - t.quantityBefore > 0 ? '+' : ''}${t.quantityAfter - t.quantityBefore}` : `${t.quantity} (requested)`}
                        </td>
                        <td className="px-4 py-3 text-sm text-gray-500">{t.quantityBefore}</td>
                        <td className="px-4 py-3 text-sm font-medium text-gray-800">{t.quantityAfter}</td>
                        <td className="px-4 py-3 text-sm">
                          <span className={`px-2 py-1 rounded-full text-[10px] font-medium ${t.status === 'COMPLETED' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'}`}>
                            {t.status}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-sm text-gray-500">{new Date(t.createdAt).toLocaleString()}</td>
                      </tr>
                    ))}
                    {history.length === 0 && <tr><td colSpan={6} className="px-4 py-6 text-center text-gray-500">No transactions recorded.</td></tr>}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      {showScanner && <BarcodeScanner onScan={handleScan} onClose={() => setShowScanner(false)} />}
    </div>
  );
}

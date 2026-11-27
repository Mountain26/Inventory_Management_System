import { beforeEach, it, expect } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import StockTransactions from '../pages/StockTransactions';
import axiosClient from '../api/axiosClient';

vi.mock('../api/axiosClient', () => ({ default: { get: vi.fn(), post: vi.fn(), patch: vi.fn() } }));
vi.mock('../context/auth', () => ({ useAuth: () => ({ userRole: 'MANAGER', userEmail: 'manager@test.local' }) }));
vi.mock('../components/BarcodeScanner', () => ({ default: () => null }));

const product = { id: 1, name: 'Rice', currentQuantity: 10 };
const request = { id: 7, product, type: 'DAMAGED', quantity: 2, status: 'PENDING_APPROVAL', reason: 'Broken bags', performedBy: { email: 'staff@test.local' } };

beforeEach(() => {
  vi.clearAllMocks();
  axiosClient.get.mockImplementation(url => Promise.resolve({ data: url === '/products' ? [product] : url.includes('/product/') ? [] : [request] }));
  axiosClient.post.mockResolvedValue({ data: {} });
  axiosClient.patch.mockResolvedValue({ data: {} });
});

it('submits damage with the backend type field and explains pending stock impact', async () => {
  render(<StockTransactions />);
  await screen.findByText('Rice (Qty: 10)');
  const selects = screen.getAllByRole('combobox');
  fireEvent.change(selects[0], { target: { value: '1' } });
  fireEvent.change(selects[1], { target: { value: 'DAMAGED' } });
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Quantity' }), { target: { value: '2' } });
  fireEvent.change(screen.getByRole('textbox', { name: 'Reason' }), { target: { value: 'Broken bags' } });
  fireEvent.click(screen.getByRole('button', { name: 'Request approval' }));
  await waitFor(() => expect(axiosClient.post).toHaveBeenCalledWith('/inventory-transactions/adjustment', { productId: 1, quantity: 2, type: 'DAMAGED', reason: 'Broken bags' }));
  expect(await screen.findByText('Request submitted for approval. Stock has not changed.')).toBeInTheDocument();
});

it('shows pending approvals and sends a review request', async () => {
  render(<StockTransactions />);
  fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));
  await waitFor(() => expect(axiosClient.patch).toHaveBeenCalledWith('/inventory-transactions/7/approve'));
  expect(await screen.findByText('Adjustment approved and stock updated.')).toBeInTheDocument();
});

it('allows signed manual adjustments', async () => {
  render(<StockTransactions />);
  await screen.findByText('Rice (Qty: 10)');
  const selects = screen.getAllByRole('combobox');
  fireEvent.change(selects[0], { target: { value: '1' } });
  fireEvent.change(selects[1], { target: { value: 'ADJUSTMENT' } });
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Quantity' }), { target: { value: '-3' } });
  fireEvent.change(screen.getByRole('textbox', { name: 'Reason' }), { target: { value: 'Count shortage' } });
  fireEvent.click(screen.getByRole('button', { name: 'Request approval' }));
  await waitFor(() => expect(axiosClient.post).toHaveBeenCalledWith('/inventory-transactions/adjustment', { productId: 1, quantity: -3, type: 'ADJUSTMENT', reason: 'Count shortage' }));
});

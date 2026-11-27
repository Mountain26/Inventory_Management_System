import { describe, it, expect } from 'vitest';

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';

// Mock the @zxing/browser module
const scanner = vi.hoisted(() => ({ stop: vi.fn() }));
vi.mock('@zxing/browser', () => ({
  BrowserMultiFormatReader: class {
    static listVideoInputDevices = vi.fn().mockResolvedValue([{ deviceId: 'cam-1', label: 'Test Camera' }]);
    decodeFromVideoDevice = vi.fn().mockResolvedValue(scanner);
  },
}));

import BarcodeScanner from '../components/BarcodeScanner';

describe('BarcodeScanner component', () => {
  it('renders the scanner modal', async () => {
    const onScan = vi.fn();
    const onClose = vi.fn();

    render(<BarcodeScanner onScan={onScan} onClose={onClose} />);

    expect(screen.getByText('Scan Barcode')).toBeInTheDocument();
    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(await screen.findByText('Point camera at barcode...')).toBeInTheDocument();
  });

  it('calls onClose when Cancel is clicked', async () => {
    const onScan = vi.fn();
    const onClose = vi.fn();

    render(<BarcodeScanner onScan={onScan} onClose={onClose} />);

    await screen.findByText('Point camera at barcode...');
    fireEvent.click(screen.getByText('Cancel'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls onClose when × button is clicked', async () => {
    const onScan = vi.fn();
    const onClose = vi.fn();

    render(<BarcodeScanner onScan={onScan} onClose={onClose} />);

    await screen.findByText('Point camera at barcode...');
    fireEvent.click(screen.getByRole('button', { name: 'Close scanner' }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('stops the camera when the scanner unmounts', async () => {
    scanner.stop.mockClear();
    const { unmount } = render(<BarcodeScanner onScan={vi.fn()} onClose={vi.fn()} />);
    await screen.findByText('Point camera at barcode...');
    unmount();
    await waitFor(() => expect(scanner.stop).toHaveBeenCalled());
  });
});

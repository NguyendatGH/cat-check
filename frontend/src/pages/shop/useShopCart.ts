import { create } from "zustand";
import {
  MOCK_CART_LINES,
  MOCK_VOUCHER,
  type MockCartLine,
  type MockPaymentMethodId,
} from "./mockData";

/**
 * Giỏ hàng GIẢ LẬP phía client — không gọi API, không lưu server.
 *
 * Module Shop chưa có backend (xem header `mockData.ts`), nhưng thiết kế yêu cầu giỏ hàng
 * tương tác được (đổi số lượng, xoá món, gỡ voucher) nên state giữ bằng zustand để đi qua
 * lại giữa `/shop`, `/cart`, `/checkout` mà không mất. Reload trang là mất — đúng bản chất
 * dữ liệu mock, KHÔNG persist để không ai nhầm là dữ liệu thật.
 */
interface ShopCartStore {
  lines: MockCartLine[];
  voucherApplied: boolean;
  /** Phương thức thanh toán đang chọn ở `/cart`, mang sang `/checkout`. */
  paymentMethodId: MockPaymentMethodId;
  increase: (id: string) => void;
  decrease: (id: string) => void;
  remove: (id: string) => void;
  add: (line: MockCartLine) => void;
  removeVoucher: () => void;
  setPaymentMethod: (id: MockPaymentMethodId) => void;
  clearAll: () => void;
  reset: () => void;
}

export const useShopCart = create<ShopCartStore>((set) => ({
  lines: MOCK_CART_LINES,
  voucherApplied: true,
  paymentMethodId: "momo",
  increase: (id) => {
    set((state) => ({
      lines: state.lines.map((l) => (l.id === id && !l.isGift ? { ...l, quantity: l.quantity + 1 } : l)),
    }));
  },
  decrease: (id) => {
    set((state) => ({
      lines: state.lines.map((l) =>
        l.id === id && !l.isGift ? { ...l, quantity: Math.max(1, l.quantity - 1) } : l,
      ),
    }));
  },
  remove: (id) => {
    set((state) => ({ lines: state.lines.filter((l) => l.id !== id) }));
  },
  add: (line) => {
    set((state) => {
      const existing = state.lines.find((l) => l.id === line.id);
      if (existing) {
        return {
          lines: state.lines.map((l) => (l.id === line.id ? { ...l, quantity: l.quantity + line.quantity } : l)),
        };
      }
      return { lines: [...state.lines, line] };
    });
  },
  removeVoucher: () => {
    set({ voucherApplied: false });
  },
  setPaymentMethod: (id) => {
    set({ paymentMethodId: id });
  },
  clearAll: () => {
    set({ lines: [], voucherApplied: false });
  },
  reset: () => {
    set({ lines: MOCK_CART_LINES, voucherApplied: true, paymentMethodId: "momo" });
  },
}));

export interface CartTotals {
  itemCount: number;
  subtotal: number;
  discount: number;
  /** Tổng tiết kiệm = chênh lệch giá gốc của từng dòng + mã giảm giá (nhãn "Tiết kiệm tổng cộng"). */
  savings: number;
  total: number;
}

export function useCartTotals(): CartTotals {
  const lines = useShopCart((s) => s.lines);
  const voucherApplied = useShopCart((s) => s.voucherApplied);
  const subtotal = lines.reduce((sum, l) => sum + l.unitPrice * l.quantity, 0);
  const discount = voucherApplied ? MOCK_VOUCHER.discountAmount : 0;
  const lineSavings = lines.reduce(
    (sum, l) => sum + Math.max(0, (l.compareAtPrice ?? l.unitPrice) - l.unitPrice) * l.quantity,
    0,
  );
  return {
    itemCount: lines.reduce((sum, l) => sum + l.quantity, 0),
    subtotal,
    discount,
    savings: lineSavings + discount,
    total: Math.max(0, subtotal - discount),
  };
}

import { create } from "zustand";
import { getShopCart, removeShopCartLine, setShopCartLine, type CartApi } from "@/features/shop";
import { type MockCartLine, type MockPaymentMethodId } from "./mockData";

/**
 * State hiển thị của giỏ hàng dùng Zustand và đồng bộ best-effort với Shop API. Khi chưa có
 * phiên đăng nhập hợp lệ, UI vẫn giữ state cục bộ để catalogue không bị hỏng.
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
  hydrate: () => Promise<void>;
}

function mapCart(cart: CartApi): MockCartLine[] {
  return cart.lines.map((line) => ({
    id: line.product.id,
    name: line.product.name,
    subtitle: line.product.sku,
    imageUrl: line.product.imageUrl ?? "",
    unitPrice: line.product.priceVnd,
    compareAtPrice: line.product.compareAtPriceVnd ?? line.product.priceVnd,
    quantity: line.quantity,
  }));
}

export const useShopCart = create<ShopCartStore>((set, get) => ({
  // Giỏ hàng bắt đầu rỗng; dữ liệu thật được hydrate từ `/api/v1/cart` khi vào màn giỏ.
  // Không hiển thị hàng mẫu cho user chưa đăng nhập hoặc khi API lỗi.
  lines: [],
  voucherApplied: false,
  paymentMethodId: "momo",
  increase: (id) => {
    const current = get().lines.find((line) => line.id === id);
    if (!current || current.isGift) return;
    const quantity = current.quantity + 1;
    set((state) => ({
      lines: state.lines.map((line) => (line.id === id ? { ...line, quantity } : line)),
    }));
    void setShopCartLine(id, quantity)
      .then((cart) => {
        set({ lines: mapCart(cart) });
      })
      .catch(() => undefined);
  },
  decrease: (id) => {
    const current = get().lines.find((line) => line.id === id);
    if (!current || current.isGift) return;
    const quantity = Math.max(1, current.quantity - 1);
    set((state) => ({
      lines: state.lines.map((line) => (line.id === id ? { ...line, quantity } : line)),
    }));
    void setShopCartLine(id, quantity)
      .then((cart) => {
        set({ lines: mapCart(cart) });
      })
      .catch(() => undefined);
  },
  remove: (id) => {
    const current = get().lines.find((line) => line.id === id);
    set((state) => ({ lines: state.lines.filter((l) => l.id !== id) }));
    if (current && !current.isGift) void removeShopCartLine(id).catch(() => undefined);
  },
  add: (line) => {
    let quantity = line.quantity;
    set((state) => {
      const existing = state.lines.find((l) => l.id === line.id);
      if (existing) {
        quantity = existing.quantity + line.quantity;
        return {
          lines: state.lines.map((l) => (l.id === line.id ? { ...l, quantity: l.quantity + line.quantity } : l)),
        };
      }
      return { lines: [...state.lines, line] };
    });
    if (!line.isGift)
      void setShopCartLine(line.id, quantity)
        .then((cart) => {
          set({ lines: mapCart(cart) });
        })
        .catch(() => undefined);
  },
  removeVoucher: () => {
    set({ voucherApplied: false });
  },
  setPaymentMethod: (id) => {
    set({ paymentMethodId: id });
  },
  clearAll: () => {
    const lines = get().lines.filter((line) => !line.isGift);
    set({ lines: [], voucherApplied: false });
    for (const line of lines) void removeShopCartLine(line.id).catch(() => undefined);
  },
  reset: () => {
    set({ lines: [], voucherApplied: false, paymentMethodId: "momo" });
  },
  hydrate: async () => {
    try {
      const cart = await getShopCart();
      set({
        lines: mapCart(cart),
        voucherApplied: false,
      });
    } catch {
      // Người dùng chưa có phiên hợp lệ: giữ state local để trang catalogue vẫn xem được.
    }
  },
}));

export interface CartTotals {
  /** Số DÒNG hàng, không phải tổng số lượng — thiết kế đếm "món" theo dòng (web: 2 món / 2 dòng). */
  itemCount: number;
  subtotal: number;
  shippingFee: number;
  discount: number;
  /** Tổng tiết kiệm = chênh lệch giá gốc của từng dòng + mã giảm giá (nhãn "Tiết kiệm tổng cộng"). */
  savings: number;
  total: number;
}

export function useCartTotals(): CartTotals {
  const lines = useShopCart((s) => s.lines);
  const subtotal = lines.reduce((sum, l) => sum + l.unitPrice * l.quantity, 0);
  const discount = 0;
  const lineSavings = lines.reduce(
    (sum, l) => sum + Math.max(0, (l.compareAtPrice ?? l.unitPrice) - l.unitPrice) * l.quantity,
    0,
  );
  return {
    itemCount: lines.length,
    subtotal,
    shippingFee: lines.length === 0 ? 0 : subtotal >= 500000 ? 0 : 30000,
    discount,
    savings: lineSavings + discount,
    total: Math.max(0, subtotal - discount + (lines.length === 0 ? 0 : subtotal >= 500000 ? 0 : 30000)),
  };
}

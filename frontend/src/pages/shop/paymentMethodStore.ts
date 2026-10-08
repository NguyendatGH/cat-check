import { create } from "zustand";
import type { ShopPaymentMethodId } from "./shopFormat";

/**
 * Phương thức thanh toán người dùng chọn ở `/cart`, mang sang `/checkout`. Đây là state UI
 * thuần (chưa gửi lên máy chủ cho tới khi đặt đơn), nên giữ ở Zustand thay vì cache API.
 */
interface PaymentMethodStore {
  paymentMethodId: ShopPaymentMethodId;
  setPaymentMethod: (id: ShopPaymentMethodId) => void;
}

export const usePaymentMethodStore = create<PaymentMethodStore>((set) => ({
  // COD là phương thức duy nhất người mua hoàn tất được ngay (chưa nối cổng thanh toán).
  paymentMethodId: "COD",
  setPaymentMethod: (id) => {
    set({ paymentMethodId: id });
  },
}));

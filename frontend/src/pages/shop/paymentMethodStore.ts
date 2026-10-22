import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { ShopPaymentMethodId } from "./shopFormat";

interface PaymentMethodStore {
  paymentMethodId: ShopPaymentMethodId;
  setPaymentMethod: (id: ShopPaymentMethodId) => void;
}

// sessionStorage: giữ lựa chọn khi tải lại trang checkout, không mang sang phiên mua sau.
export const usePaymentMethodStore = create<PaymentMethodStore>()(
  persist(
    (set) => ({
      paymentMethodId: "COD",
      setPaymentMethod: (id) => {
        set({ paymentMethodId: id });
      },
    }),
    { name: "catcheck.payment-method", storage: createJSONStorage(() => sessionStorage) },
  ),
);

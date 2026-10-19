import { create } from "zustand";
import type { ShopPaymentMethodId } from "./shopFormat";

interface PaymentMethodStore {
  paymentMethodId: ShopPaymentMethodId;
  setPaymentMethod: (id: ShopPaymentMethodId) => void;
}

export const usePaymentMethodStore = create<PaymentMethodStore>((set) => ({
  paymentMethodId: "COD",
  setPaymentMethod: (id) => {
    set({ paymentMethodId: id });
  },
}));

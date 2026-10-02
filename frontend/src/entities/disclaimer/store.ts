import { create } from "zustand";
import type { DisclaimerAcceptance } from "./model";

/** Store tối thiểu trạng thái chấp nhận disclaimer trong onboarding. TODO: nối API thật. */
interface DisclaimerStore {
  acceptance: DisclaimerAcceptance | null;
  setAcceptance: (acceptance: DisclaimerAcceptance | null) => void;
}

export const useDisclaimerStore = create<DisclaimerStore>((set) => ({
  acceptance: null,
  setAcceptance: (acceptance) => {
    set({ acceptance });
  },
}));

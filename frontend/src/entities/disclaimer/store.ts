import { create } from "zustand";
import type { DisclaimerAcceptance } from "./model";

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

import { create } from "zustand";
import { persist } from "zustand/middleware";

interface CatStore {
  activeCatId: string | null;
  setActiveCat: (id: string | null) => void;
}

export const useCatStore = create<CatStore>()(
  persist(
    (set) => ({
      activeCatId: null,
      setActiveCat: (id) => {
        set({ activeCatId: id });
      },
    }),
    { name: "catcheck.active-cat" },
  ),
);

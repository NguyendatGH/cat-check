import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * `activeCat.store` (p9 §9.5.2) — mèo đang được chọn trên toàn app (cat switcher W1/W2,
 * bước 1 luồng quét, redirect `/history` → `/cats/:activeCatId/history`).
 * Persist `localStorage`: user mở lại app phải thấy đúng mèo hôm qua.
 *
 * `activeCatId` giữ tên field cũ (không đổi thành `activeCat`) vì `pages/history/
 * HistoryRedirectPage.tsx` (thuộc A6, ngoài phạm vi sửa của A3) đã đọc
 * `useCatStore((state) => state.activeCatId)` — đổi tên sẽ phá route đó.
 */
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

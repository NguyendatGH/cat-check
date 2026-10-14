import { create } from "zustand";

/** Một lần ăn mừng (pháo hoa toàn màn hình). title/message tuỳ chọn: không có => chỉ pháo hoa. */
export interface CelebrationRequest {
  title?: string;
  message?: string;
  /** Thời lượng hiệu ứng, mặc định DEFAULT_CELEBRATION_MS. */
  durationMs?: number;
}

export interface ActiveCelebration extends CelebrationRequest {
  id: number;
  durationMs: number;
}

export const DEFAULT_CELEBRATION_MS = 3000;

interface CelebrationStore {
  current: ActiveCelebration | null;
  queue: ActiveCelebration[];
  /** Khoá "sắp ăn mừng" (vd. đang tải số dư để biết có quà chào mừng) — chặn product tour chen vào. */
  holds: string[];
  nextId: number;
  celebrate: (request?: CelebrationRequest) => number;
  finish: (id: number) => void;
  hold: (key: string) => void;
  release: (key: string) => void;
  reset: () => void;
}

export const useCelebrationStore = create<CelebrationStore>((set, get) => ({
  current: null,
  queue: [],
  holds: [],
  nextId: 1,
  celebrate: (request = {}) => {
    const id = get().nextId;
    const item: ActiveCelebration = { ...request, id, durationMs: request.durationMs ?? DEFAULT_CELEBRATION_MS };
    set((state) =>
      state.current === null
        ? { current: item, nextId: id + 1 }
        : { queue: [...state.queue, item], nextId: id + 1 },
    );
    return id;
  },
  finish: (id) => {
    set((state) => {
      if (state.current?.id !== id) return state;
      const [next = null, ...rest] = state.queue;
      return { current: next, queue: rest };
    });
  },
  hold: (key) => {
    set((state) => (state.holds.includes(key) ? state : { holds: [...state.holds, key] }));
  },
  release: (key) => {
    set((state) => (state.holds.includes(key) ? { holds: state.holds.filter((k) => k !== key) } : state));
  },
  reset: () => {
    set({ current: null, queue: [], holds: [], nextId: 1 });
  },
}));

/** Kích hoạt pháo hoa từ bất kỳ đâu (host gắn một lần trong AppProviders). */
export function celebrate(request?: CelebrationRequest): number {
  return useCelebrationStore.getState().celebrate(request);
}

/** true khi đang/ sắp ăn mừng — overlay khác (product tour) nên đợi. */
export function useCelebrationBusy(): boolean {
  return useCelebrationStore((s) => s.current !== null || s.queue.length > 0 || s.holds.length > 0);
}

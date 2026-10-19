import { create } from "zustand";

/** boot: session bootstrap · intro: brand reveal after auth · exit: fading out over the next page */
export type SplashPhase = "hidden" | "boot" | "intro" | "exit";

interface SplashStore {
  phase: SplashPhase;
  runId: number;
  /** Resolves to the callback that swaps the UI underneath (set session / navigate) once the task is done. */
  pending: Promise<() => void> | null;
  boot: () => void;
  release: () => void;
  play: <T>(task: Promise<T>, reveal: (value: T) => void) => void;
  exit: () => void;
  hide: () => void;
}

export const useSplashStore = create<SplashStore>((set, get) => ({
  phase: "hidden",
  runId: 0,
  pending: null,
  boot: () => {
    if (get().phase === "hidden") set({ phase: "boot" });
  },
  release: () => {
    if (get().phase === "boot") set({ phase: "exit" });
  },
  play: (task, reveal) => {
    const pending = task.then(
      (value) => () => {
        reveal(value);
      },
      () => () => undefined,
    );
    set((state) => ({ phase: "intro", runId: state.runId + 1, pending }));
  },
  exit: () => {
    set({ phase: "exit", pending: null });
  },
  hide: () => {
    set({ phase: "hidden", pending: null });
  },
}));

export function playSplash<T>(task: Promise<T>, reveal: (value: T) => void) {
  useSplashStore.getState().play(task, reveal);
}

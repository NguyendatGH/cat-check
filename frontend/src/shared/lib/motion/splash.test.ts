import { describe, it, expect, beforeEach, vi } from "vitest";
import { useSplashStore } from "./splash";

describe("useSplashStore", () => {
  beforeEach(() => {
    useSplashStore.setState({ phase: "hidden", runId: 0, pending: null });
  });

  it("boot() transitions from hidden to boot", () => {
    useSplashStore.getState().boot();
    expect(useSplashStore.getState().phase).toBe("boot");
  });

  it("release() transitions from boot to exit", () => {
    useSplashStore.getState().boot();
    useSplashStore.getState().release();
    expect(useSplashStore.getState().phase).toBe("exit");
  });

  it("hide() transitions to hidden", () => {
    useSplashStore.getState().boot();
    useSplashStore.getState().hide();
    expect(useSplashStore.getState().phase).toBe("hidden");
  });

  it("play() sets phase to intro and increments runId", () => {
    const initialRunId = useSplashStore.getState().runId;
    useSplashStore.getState().play(Promise.resolve(5), vi.fn());
    expect(useSplashStore.getState().phase).toBe("intro");
    expect(useSplashStore.getState().runId).toBe(initialRunId + 1);
  });

  it("play() calls reveal callback when pending resolves", async () => {
    const spy = vi.fn();
    const promise = Promise.resolve(5);
    useSplashStore.getState().play(promise, spy);
    const pending = useSplashStore.getState().pending;
    if (pending) {
      const reveal = await pending;
      reveal();
      expect(spy).toHaveBeenCalledWith(5);
    }
  });

  it("play() does not call reveal callback when pending rejects", async () => {
    const spy = vi.fn();
    const promise = Promise.reject(new Error("test"));
    useSplashStore.getState().play(promise, spy);
    const pending = useSplashStore.getState().pending;
    if (pending) {
      const reveal = await pending.catch(() => () => undefined);
      reveal();
      expect(spy).not.toHaveBeenCalled();
    }
  });
});

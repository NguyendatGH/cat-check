import { beforeEach, describe, expect, it } from "vitest";
import { DEFAULT_CELEBRATION_MS, celebrate, useCelebrationStore } from "./celebration";

const busy = () => {
  const s = useCelebrationStore.getState();
  return s.current !== null || s.queue.length > 0 || s.holds.length > 0;
};

describe("useCelebrationStore", () => {
  beforeEach(() => {
    useCelebrationStore.getState().reset();
  });

  it("celebrate() bật ngay khi rảnh, dùng thời lượng mặc định", () => {
    const id = celebrate({ message: "hi" });
    expect(useCelebrationStore.getState().current).toMatchObject({ id, message: "hi", durationMs: DEFAULT_CELEBRATION_MS });
  });

  it("xếp hàng lần ăn mừng thứ hai và chuyển tiếp khi lần đầu xong", () => {
    const first = celebrate();
    const second = celebrate({ durationMs: 1000 });
    expect(useCelebrationStore.getState().queue).toHaveLength(1);

    useCelebrationStore.getState().finish(second);
    expect(useCelebrationStore.getState().current?.id).toBe(first);

    useCelebrationStore.getState().finish(first);
    expect(useCelebrationStore.getState().current).toMatchObject({ id: second, durationMs: 1000 });

    useCelebrationStore.getState().finish(second);
    expect(useCelebrationStore.getState().current).toBeNull();
    expect(busy()).toBe(false);
  });

  it("hold/release đánh dấu 'sắp ăn mừng' để overlay khác đợi", () => {
    const { hold, release } = useCelebrationStore.getState();
    hold("welcome");
    hold("welcome");
    expect(useCelebrationStore.getState().holds).toEqual(["welcome"]);
    expect(busy()).toBe(true);
    release("welcome");
    expect(busy()).toBe(false);
  });
});

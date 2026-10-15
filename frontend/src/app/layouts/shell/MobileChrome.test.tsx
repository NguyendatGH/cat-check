import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useHeaderCollapsed } from "./MobileChrome";

const VIEWPORT = 800;
const ROW = 50;
let fullHeight = 1110;
let collapsedNow = false;
let mounted: Root | undefined;

function Probe() {
  const collapsed = useHeaderCollapsed();
  collapsedNow = collapsed;
  return null;
}

// Trang ngắn đi đúng chiều cao hàng phím tắt khi thu gọn, như layout thật.
function maxScroll(): number {
  return fullHeight - (collapsedNow ? ROW : 0) - VIEWPORT;
}

function scrollTo(y: number) {
  act(() => {
    Object.defineProperty(window, "scrollY", { value: Math.max(0, Math.min(y, maxScroll())), configurable: true });
    window.dispatchEvent(new Event("scroll"));
  });
}

// Trình duyệt kẹp scrollY khi trang ngắn đi; phát sự kiện scroll như thật.
function settleLayout() {
  if (window.scrollY > maxScroll()) scrollTo(maxScroll());
}

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  vi.spyOn(window, "requestAnimationFrame").mockImplementation((cb) => {
    cb(0);
    return 0;
  });
  Object.defineProperty(window, "innerHeight", { value: VIEWPORT, configurable: true });
  Object.defineProperty(document.documentElement, "scrollHeight", {
    configurable: true,
    get: () => fullHeight - (collapsedNow ? ROW : 0),
  });
  Object.defineProperty(window, "scrollY", { value: 0, configurable: true });
  collapsedNow = false;
  const root = createRoot(document.createElement("div"));
  act(() => {
    root.render(<Probe />);
  });
  mounted = root;
});

afterEach(() => {
  act(() => mounted?.unmount());
  vi.restoreAllMocks();
});

describe("useHeaderCollapsed", () => {
  it("không bật/tắt liên tục khi cuộn tới cuối trang", () => {
    fullHeight = 1110;
    const states: boolean[] = [];
    for (let y = 40; y <= 400; y += 40) {
      scrollTo(y);
      settleLayout();
      states.push(collapsedNow);
    }
    for (let i = 0; i < 5; i++) {
      settleLayout();
      states.push(collapsedNow);
    }
    expect(collapsedNow).toBe(true);
    const flips = states.filter((s, i) => i > 0 && s !== states[i - 1]).length;
    expect(flips).toBe(1);
  });

  it("không bật/tắt liên tục khi đang mở mà cuộn tiếp qua đáy trang", () => {
    fullHeight = 1110;
    for (let y = 40; y <= 400; y += 40) {
      scrollTo(y);
      settleLayout();
    }
    // Cuộn lên một đoạn (header mở lại) rồi cuộn xuống quá đáy nhiều lần, như vuốt/nảy ở cuối trang.
    scrollTo(window.scrollY - 60);
    expect(collapsedNow).toBe(false);
    const states: boolean[] = [];
    for (let i = 0; i < 6; i++) {
      scrollTo(window.scrollY + 100);
      settleLayout();
      states.push(collapsedNow);
    }
    expect(states.every(Boolean)).toBe(true);
  });

  it("vẫn mở lại khi người dùng thật sự cuộn lên từ cuối trang", () => {
    fullHeight = 1110;
    for (let y = 40; y <= 400; y += 40) {
      scrollTo(y);
      settleLayout();
    }
    scrollTo(window.scrollY - 40);
    expect(collapsedNow).toBe(false);
  });

  it("không thu gọn trên trang chỉ dài hơn màn hình một chút", () => {
    fullHeight = VIEWPORT + 120;
    for (let y = 20; y <= 120; y += 20) {
      scrollTo(y);
      settleLayout();
    }
    expect(collapsedNow).toBe(false);
  });
});

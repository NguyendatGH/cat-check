import { act, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useSessionStore, type SessionUser } from "@/entities/user";
import { useSplashStore } from "@/shared/lib/motion/splash";
import { ProductTour } from "./ProductTour";
import { readTourStatus } from "./storage";

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key }),
}));

// antd Tour cần layout thật; thay bằng bản tối giản giữ nguyên hợp đồng props mà ProductTour dùng.
vi.mock("antd", async (importOriginal) => {
  const actual = await importOriginal<typeof import("antd")>();
  interface FakeTourProps {
    open?: boolean;
    current?: number;
    steps?: { title: ReactNode; description?: ReactNode }[];
    onClose?: (current: number) => void;
    onFinish?: () => void;
    actionsRender?: (origin: ReactNode, info: { current: number; total: number }) => ReactNode;
  }
  function Tour({ open, current = 0, steps = [], onClose, onFinish, actionsRender }: FakeTourProps) {
    if (!open) return null;
    const step = steps.at(current);
    return (
      <div data-testid="tour">
        <p data-testid="title">{step?.title}</p>
        <div>{step?.description}</div>
        {actionsRender?.(null, { current, total: steps.length })}
        <button type="button" data-testid="close" aria-label="close" onClick={() => onClose?.(current)} />
        <button type="button" data-testid="finish" aria-label="finish" onClick={() => onFinish?.()} />
      </div>
    );
  }
  return { ...actual, Tour };
});

const buyer: SessionUser = {
  id: "buyer-1",
  email: "a@example.test",
  displayName: "A",
  roles: ["USER"],
  onboarding: { completed: true, nextStep: null },
};

let container: HTMLDivElement;
let root: Root;

function renderAt(pathname: string) {
  act(() => {
    root.render(
      <MemoryRouter initialEntries={[pathname]}>
        <nav>
          <a data-tour="tab-dashboard" href="/dashboard" aria-label="home" />
          <a data-tour="tab-scan" href="/scan" aria-label="scan" />
        </nav>
        <ProductTour />
      </MemoryRouter>,
    );
  });
}

function advance(ms = 1000) {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
}

function click(testId: string) {
  const node = container.querySelector(`[data-testid="${testId}"]`);
  if (!(node instanceof HTMLElement)) throw new Error(`missing ${testId}`);
  act(() => {
    node.click();
  });
}

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  vi.useFakeTimers();
  vi.stubGlobal(
    "matchMedia",
    (query: string) =>
      ({
        matches: false,
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList,
  );
  // jsdom không có layout: coi mọi phần tử là đang hiển thị.
  vi.spyOn(HTMLElement.prototype, "getClientRects").mockReturnValue([{}] as unknown as DOMRectList);
  window.localStorage.clear();
  useSessionStore.setState({ user: buyer, status: "authenticated" });
  useSplashStore.setState({ phase: "hidden" });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("ProductTour", () => {
  it("tự hiện cho tài khoản mới ở Trang chủ, bắt đầu bằng bước chào mừng", () => {
    renderAt("/dashboard");
    advance();
    expect(container.querySelector('[data-testid="tour"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="title"]')?.textContent).toBe("tour:welcome.title");
  });

  it("chờ brand splash ẩn hẳn rồi mới hiện", () => {
    useSplashStore.setState({ phase: "intro" });
    renderAt("/dashboard");
    advance();
    expect(container.querySelector('[data-testid="tour"]')).toBeNull();
    act(() => {
      useSplashStore.setState({ phase: "hidden" });
    });
    advance();
    expect(container.querySelector('[data-testid="tour"]')).not.toBeNull();
  });

  it("Bỏ qua => đánh dấu đã xem và không hiện lại", () => {
    renderAt("/dashboard");
    advance();
    const skip = [...container.querySelectorAll("button")].find((b) => b.textContent === "tour:actions.skip");
    if (!skip) throw new Error("missing skip");
    act(() => {
      skip.click();
    });
    expect(container.querySelector('[data-testid="tour"]')).toBeNull();
    expect(readTourStatus(buyer.id)).toBe("seen");
  });

  it("tick Không hiển thị lại rồi đóng => disabled", () => {
    renderAt("/dashboard");
    advance();
    const checkbox = container.querySelector('input[type="checkbox"]');
    if (!(checkbox instanceof HTMLInputElement)) throw new Error("missing checkbox");
    act(() => {
      checkbox.click();
    });
    click("close");
    expect(readTourStatus(buyer.id)).toBe("disabled");
  });

  it("hoàn tất => seen", () => {
    renderAt("/dashboard");
    advance();
    click("finish");
    expect(readTourStatus(buyer.id)).toBe("seen");
  });

  it("không hiện ở trang khác", () => {
    renderAt("/scan");
    advance();
    expect(container.querySelector('[data-testid="tour"]')).toBeNull();
  });

  it("không hiện với staff", () => {
    useSessionStore.setState({ user: { ...buyer, roles: ["ADMIN_SUPPORT"] } });
    renderAt("/dashboard");
    advance();
    expect(container.querySelector('[data-testid="tour"]')).toBeNull();
  });

  it("không hiện khi đã tắt", () => {
    window.localStorage.setItem(`catcheck.productTour.v1:${buyer.id}`, "disabled");
    renderAt("/dashboard");
    advance();
    expect(container.querySelector('[data-testid="tour"]')).toBeNull();
  });
});

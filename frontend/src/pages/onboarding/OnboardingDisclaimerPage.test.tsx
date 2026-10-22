import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router";
import { OnboardingDisclaimerPage } from "./OnboardingDisclaimerPage";

vi.mock("msw/browser", () => ({ setupWorker: () => ({ start: () => undefined, stop: () => undefined }) }));

const nav = vi.hoisted(() => ({
  navigate: vi.fn(),
}));

vi.mock("react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router")>()),
  useNavigate: () => nav.navigate,
}));

const tMock = (key: string, options?: { returnObjects?: boolean }) => {
  if (options?.returnObjects) {
    if (key === "disclaimer.content") {
      return {
        heading: "CatCheck giúp bạn theo dõi",
        intro: "Giới thiệu miễn trừ trách nhiệm y tế",
        points: ["Điểm 1", "Điểm 2"],
        outro: "Kết luận",
      };
    }
  }
  return key;
};

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: tMock,
    i18n: { language: "vi" },
  }),
}));

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  nav.navigate.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
  document.body.innerHTML = "";
});

describe("OnboardingDisclaimerPage - Desktop & Scrolling fix", () => {
  it("tự động mở khóa checkbox khi nội dung hiển thị đầy đủ không cần cuộn trên desktop", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(
        <MemoryRouter>
          <OnboardingDisclaimerPage />
        </MemoryRouter>,
      );
    });

    const checkbox = container.querySelector('input[type="checkbox"]') as HTMLInputElement;
    expect(checkbox).not.toBeNull();
    // Trên môi trường test (jsdom scrollHeight = clientHeight = 0), auto-detection nhận diện là non-scrollable và enable checkbox
    expect(checkbox.disabled).toBe(false);

    // Người dùng có thể click tick vào ô
    act(() => {
      checkbox.click();
    });
    expect(checkbox.checked).toBe(true);

    // Bấm tiếp tục chuyển sang activate
    const continueBtn = Array.from(container.querySelectorAll("button")).find((b) =>
      b.textContent.includes("disclaimer.continue"),
    );
    expect(continueBtn).toBeDefined();

    act(() => {
      continueBtn?.click();
    });
    expect(nav.navigate).toHaveBeenCalledWith("/onboarding/activate");

    act(() => {
      root.unmount();
    });
  });

  it("hiển thị lỗi khi chưa tick mà ấn tiếp tục", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(
        <MemoryRouter>
          <OnboardingDisclaimerPage />
        </MemoryRouter>,
      );
    });

    const continueBtn = Array.from(container.querySelectorAll("button")).find((b) =>
      b.textContent.includes("disclaimer.continue"),
    );

    act(() => {
      continueBtn?.click();
    });

    expect(nav.navigate).not.toHaveBeenCalled();
    expect(container.textContent).toContain("disclaimer.ackRequired");

    act(() => {
      root.unmount();
    });
  });
});

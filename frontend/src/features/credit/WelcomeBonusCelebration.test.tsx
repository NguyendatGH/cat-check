import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useSessionStore, type SessionUser } from "@/entities/user";
import { useCelebrationStore } from "@/shared/lib/motion/celebration";
import { useSplashStore } from "@/shared/lib/motion/splash";
import type { CreditBalance } from "./types";
import { WelcomeBonusCelebration } from "./WelcomeBonusCelebration";
import { hasCelebratedWelcome } from "./welcome";

const balanceState: { data: CreditBalance | undefined; isSuccess: boolean; isError: boolean } = {
  data: undefined,
  isSuccess: false,
  isError: false,
};

vi.mock("./hooks", () => ({
  useCreditBalance: () => balanceState,
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}:${JSON.stringify(options)}` : key),
  }),
}));

const buyer: SessionUser = {
  id: "u-wbc",
  email: "a@example.test",
  displayName: "A",
  roles: ["USER"],
  onboarding: { completed: true, nextStep: null },
};

describe("WelcomeBonusCelebration", () => {
  let container: HTMLDivElement;
  let root: Root;

  const render = () => {
    act(() => {
      root.render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <WelcomeBonusCelebration />
        </MemoryRouter>,
      );
    });
  };

  beforeEach(() => {
    window.localStorage.clear();
    useCelebrationStore.getState().reset();
    useSplashStore.setState({ phase: "hidden", runId: 0, pending: null });
    useSessionStore.setState({ user: buyer });
    Object.assign(balanceState, { data: undefined, isSuccess: false, isError: false });
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("giữ khoá trong lúc tải, rồi ăn mừng đúng một lần với số liệu từ API", () => {
    render();
    expect(useCelebrationStore.getState().holds).toEqual(["welcome-bonus"]);

    Object.assign(balanceState, {
      isSuccess: true,
      data: {
        availableBalance: 10,
        trialScansUsed: 0,
        trialScansRemaining: 3,
        batches: [
          {
            batchId: "b",
            packageCode: "WELCOME",
            initialAmount: 10,
            remainingAmount: 10,
            activatedAt: "2026-05-01T00:00:00Z",
            expiresAt: "2026-05-06T00:00:00Z",
            remainingSeconds: 1,
          },
        ],
      },
    });
    render();

    const state = useCelebrationStore.getState();
    expect(state.current?.message).toBe('welcome.message:{"credits":10,"days":5}');
    expect(state.holds).toEqual([]);
    expect(hasCelebratedWelcome(buyer.id)).toBe(true);

    render();
    expect(useCelebrationStore.getState().queue).toHaveLength(0);
  });

  it("không có lô WELCOME => không ăn mừng, nhả khoá", () => {
    Object.assign(balanceState, {
      isSuccess: true,
      data: { availableBalance: 0, trialScansUsed: 0, trialScansRemaining: 3, batches: [] },
    });
    render();
    expect(useCelebrationStore.getState().current).toBeNull();
    expect(useCelebrationStore.getState().holds).toEqual([]);
  });
});

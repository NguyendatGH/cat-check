import { describe, expect, it } from "vitest";
import type { SessionUser } from "@/entities/user";
import { findTourTarget, resolveTourSteps, shouldAutoStartTour, statusAfterClose } from "./logic";
import { TOUR_STEPS } from "./steps";

const buyer: SessionUser = {
  id: "u-1",
  email: "a@example.test",
  displayName: "A",
  roles: ["USER"],
  onboarding: { completed: true, nextStep: null },
};

const base = { user: buyer, pathname: "/dashboard", splashPhase: "hidden", status: "pending" as const };
const allFlags = { community: true, map: true, shop: true };

describe("shouldAutoStartTour", () => {
  it("chạy cho người mua mới ở Trang chủ khi splash đã ẩn", () => {
    expect(shouldAutoStartTour(base)).toBe(true);
    expect(shouldAutoStartTour({ ...base, pathname: "/dashboard/" })).toBe(true);
  });

  it("không chạy khi đã xem hoặc đã tắt", () => {
    expect(shouldAutoStartTour({ ...base, status: "seen" })).toBe(false);
    expect(shouldAutoStartTour({ ...base, status: "disabled" })).toBe(false);
  });

  it("không chạy khi splash còn hiện", () => {
    for (const phase of ["boot", "intro", "exit"]) {
      expect(shouldAutoStartTour({ ...base, splashPhase: phase })).toBe(false);
    }
  });

  it("không chạy cho staff, khách, onboarding chưa xong hoặc trang khác", () => {
    expect(shouldAutoStartTour({ ...base, user: { ...buyer, roles: ["USER", "ADMIN_SUPER"] } })).toBe(false);
    expect(shouldAutoStartTour({ ...base, user: { ...buyer, roles: ["MODERATOR"] } })).toBe(false);
    expect(shouldAutoStartTour({ ...base, user: null })).toBe(false);
    expect(
      shouldAutoStartTour({ ...base, user: { ...buyer, onboarding: { completed: false, nextStep: "cat" } } }),
    ).toBe(false);
    expect(shouldAutoStartTour({ ...base, pathname: "/scan" })).toBe(false);
  });
});

describe("statusAfterClose", () => {
  it("đóng/hoàn tất => seen; tick Không hiển thị lại => disabled", () => {
    expect(statusAfterClose(false)).toBe("seen");
    expect(statusAfterClose(true)).toBe("disabled");
  });
});

describe("resolveTourSteps", () => {
  it("mobile: bước chào mừng + tab đáy + lối tắt header, không có bước chỉ dành cho desktop", () => {
    const steps = resolveTourSteps(TOUR_STEPS, { isDesktop: false, flags: allFlags, hasTarget: () => true });
    expect(steps[0]).toMatchObject({ id: "welcome", target: null });
    const targets = steps.slice(1).map((s) => s.target);
    expect(targets).toContain("tab-scan");
    expect(targets).toContain("shortcut-map");
    expect(targets).not.toContain("nav-cats");
    expect(steps.find((s) => s.id === "cats")).toBeUndefined();
  });

  it("desktop: dùng mục DesktopAside và nhãn desktop", () => {
    const steps = resolveTourSteps(TOUR_STEPS, { isDesktop: true, flags: allFlags, hasTarget: () => true });
    expect(steps.find((s) => s.id === "scan")).toMatchObject({ target: "nav-scan", titleKey: "common:nav.scanColor" });
    expect(steps.find((s) => s.id === "cats")?.target).toBe("nav-cats");
  });

  it("bỏ bước có feature flag tắt", () => {
    const steps = resolveTourSteps(TOUR_STEPS, {
      isDesktop: false,
      flags: { community: false, map: true, shop: false },
      hasTarget: () => true,
    });
    const ids = steps.map((s) => s.id);
    expect(ids).not.toContain("community");
    expect(ids).not.toContain("shop");
    expect(ids).toContain("map");
  });

  it("bỏ bước có phần tử không render; không còn phần tử nào => không chạy", () => {
    const partial = resolveTourSteps(TOUR_STEPS, {
      isDesktop: false,
      flags: allFlags,
      hasTarget: (target) => target.startsWith("tab-"),
    });
    expect(partial.slice(1).every((s) => s.target?.startsWith("tab-"))).toBe(true);
    expect(resolveTourSteps(TOUR_STEPS, { isDesktop: false, flags: allFlags, hasTarget: () => false })).toEqual([]);
  });
});

describe("findTourTarget", () => {
  it("chọn phần tử đang hiển thị, bỏ bản bị ẩn", () => {
    document.body.innerHTML = '<button data-tour="notifications" id="hidden"></button><button data-tour="notifications" id="shown"></button>';
    const shown = document.getElementById("shown");
    if (!shown) throw new Error("missing");
    shown.getClientRects = () => [{}] as unknown as DOMRectList;
    expect(findTourTarget("notifications")?.id).toBe("shown");
    expect(findTourTarget("tab-scan")).toBeNull();
    document.body.innerHTML = "";
  });
});

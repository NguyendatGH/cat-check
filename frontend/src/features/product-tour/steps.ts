import type { FeatureFlagKey } from "@/shared/config/featureFlags";

export type TourPlacement = "top" | "bottom" | "left" | "right" | "bottomLeft" | "bottomRight" | "topLeft" | "topRight";

interface BreakpointTarget {
  /** Giá trị thuộc tính `data-tour` của phần tử cần highlight. */
  target: string;
  /** Khoá i18n tiêu đề (tái dùng nhãn điều hướng hiện có). */
  titleKey: string;
  placement: TourPlacement;
}

export interface TourStepDef {
  id: string;
  /** Bỏ bước khi feature flag tắt. */
  flag?: FeatureFlagKey;
  bodyKey: string;
  /** Không khai báo => bước không có trên breakpoint đó. */
  mobile?: BreakpointTarget;
  desktop?: BreakpointTarget;
}

/** Bước chào mừng đầu tiên, không gắn phần tử (hiện giữa màn hình). */
export const WELCOME_STEP = {
  id: "welcome",
  titleKey: "tour:welcome.title",
  bodyKey: "tour:welcome.body",
} as const;

export const TOUR_STEPS: readonly TourStepDef[] = [
  {
    id: "dashboard",
    bodyKey: "tour:steps.dashboard",
    mobile: { target: "tab-dashboard", titleKey: "common:nav.dashboard", placement: "top" },
    desktop: { target: "nav-dashboard", titleKey: "common:nav.overview", placement: "right" },
  },
  {
    id: "scan",
    bodyKey: "tour:steps.scan",
    mobile: { target: "tab-scan", titleKey: "common:nav.scan", placement: "top" },
    desktop: { target: "nav-scan", titleKey: "common:nav.scanColor", placement: "right" },
  },
  {
    id: "history",
    bodyKey: "tour:steps.history",
    mobile: { target: "tab-history", titleKey: "common:nav.history", placement: "top" },
    desktop: { target: "nav-history", titleKey: "common:nav.historyAnalytics", placement: "right" },
  },
  {
    id: "cats",
    bodyKey: "tour:steps.cats",
    desktop: { target: "nav-cats", titleKey: "common:nav.catProfile", placement: "right" },
  },
  {
    id: "community",
    flag: "community",
    bodyKey: "tour:steps.community",
    mobile: { target: "tab-community", titleKey: "common:nav.community", placement: "top" },
    desktop: { target: "nav-community", titleKey: "common:nav.community", placement: "right" },
  },
  {
    id: "map",
    flag: "map",
    bodyKey: "tour:steps.map",
    mobile: { target: "shortcut-map", titleKey: "common:nav.vetMap", placement: "bottom" },
    desktop: { target: "nav-map", titleKey: "common:nav.vetMap", placement: "right" },
  },
  {
    id: "shop",
    flag: "shop",
    bodyKey: "tour:steps.shop",
    mobile: { target: "shortcut-shop", titleKey: "common:nav.shop", placement: "bottom" },
    desktop: { target: "nav-shop", titleKey: "common:nav.shop", placement: "right" },
  },
  {
    id: "credits",
    bodyKey: "tour:steps.credits",
    mobile: { target: "shortcut-credits", titleKey: "common:nav.credits", placement: "bottom" },
    desktop: { target: "credits", titleKey: "common:nav.credits", placement: "right" },
  },
  {
    id: "assistant",
    bodyKey: "tour:steps.assistant",
    mobile: { target: "shortcut-assistant", titleKey: "common:nav.assistant", placement: "bottom" },
    desktop: { target: "nav-assistant", titleKey: "common:nav.assistant", placement: "right" },
  },
  {
    id: "notifications",
    bodyKey: "tour:steps.notifications",
    mobile: { target: "notifications", titleKey: "common:nav.notifications", placement: "bottomRight" },
    desktop: { target: "notifications", titleKey: "common:nav.notifications", placement: "bottomRight" },
  },
  {
    id: "profile",
    bodyKey: "tour:steps.profile",
    mobile: { target: "tab-profile", titleKey: "common:nav.profile", placement: "topRight" },
    desktop: { target: "nav-settings", titleKey: "common:nav.settings", placement: "right" },
  },
];

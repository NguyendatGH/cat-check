import type { ThemeConfig } from "antd";

/** Màu thương hiệu cho Tour (antd cần giá trị màu thật để sinh token). */
export function tourTheme(reducedMotion: boolean): ThemeConfig {
  return {
    token: {
      colorPrimary: "#0d369a",
      fontFamily: '"Nunito", "Be Vietnam Pro", system-ui, sans-serif',
      borderRadiusLG: 16,
      motion: !reducedMotion,
    },
  };
}

/** Khớp --z-modal (500): trên header (200), bottom nav (300) và overlay (400). */
export const TOUR_Z_INDEX = 500;

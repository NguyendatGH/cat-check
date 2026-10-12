import type { ThemeConfig } from "antd";

export const adminTheme: ThemeConfig = {
  token: {
    colorPrimary: "#0d369a",
    colorText: "#161b2c",
    colorTextSecondary: "#444652",
    colorBgLayout: "#f5f7fb",
    colorBgContainer: "#ffffff",
    colorBorder: "#d7deeb",
    fontFamily: '"Nunito", "Be Vietnam Pro", system-ui, sans-serif',
    borderRadius: 12,
  },
  components: {
    Menu: {
      itemHeight: 44,
      itemBorderRadius: 10,
      itemMarginBlock: 4,
      itemSelectedBg: "#e4e7ff",
      itemSelectedColor: "#0d369a",
      darkItemBg: "#14213d",
      darkSubMenuItemBg: "#14213d",
      darkItemColor: "#d0d9ed",
      darkItemHoverColor: "#ffffff",
      darkItemHoverBg: "#23365b",
      darkItemSelectedBg: "#314c9b",
      darkItemSelectedColor: "#ffffff",
    },
    Card: {
      borderRadiusLG: 16,
    },
  },
};

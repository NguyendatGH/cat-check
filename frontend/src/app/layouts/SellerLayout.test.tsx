import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useSessionStore, type SessionUser } from "@/entities/user";
import { useGroup2Can } from "@/pages/admin/group2Access";
import { SellerLayout } from "./SellerLayout";

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: "vi" } }),
}));

vi.mock("./shell/StaffLogoutButton", () => ({ StaffLogoutButton: () => null }));

function Probe() {
  const can = useGroup2Can();
  return <p data-testid="probe">{String(can("colorChart"))}</p>;
}

let mounted: Root | undefined;

function renderAs(roles: string[]): HTMLElement {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  useSessionStore.getState().setSession({
    id: "u1",
    email: "seller@example.com",
    displayName: "Seller",
    roles,
  } as unknown as SessionUser);
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={["/seller/color-chart"]}>
        <Routes>
          <Route element={<SellerLayout />}>
            <Route path="/seller/color-chart" element={<Probe />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
  });
  mounted = root;
  return container;
}

afterEach(() => {
  act(() => mounted?.unmount());
  mounted = undefined;
  document.body.innerHTML = "";
  useSessionStore.getState().setSession(null);
});

describe("SellerLayout", () => {
  it("cấp quyền admin dùng chung cho trang bảng màu theo vai trò phiên", () => {
    const container = renderAs(["ADMIN_CATALOG"]);
    expect(container.querySelector("[data-testid=probe]")?.textContent).toBe("true");
  });

  it("không cấp quyền bảng màu khi vai trò không đủ", () => {
    const container = renderAs(["ADMIN_SUPPORT"]);
    expect(container.querySelector("[data-testid=probe]")?.textContent).toBe("false");
  });
});

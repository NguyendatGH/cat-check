import { afterEach, describe, expect, it, vi } from "vitest";
import type { RouteObject } from "react-router";
import { isValidElement } from "react";

// `routes` kéo theo layout → feature → `features/*/mocks.ts`, nơi gọi `setupWorker` của MSW.
// `setupWorker` ném ngay khi không chạy trong trình duyệt (jsdom không tính là browser), nên
// phải thay bằng stub trước khi import cây router.
vi.mock("msw/browser", () => ({ setupWorker: () => ({ start: () => undefined, stop: () => undefined }) }));

/**
 * p9 §9.4.3 mục E + p10 §6.7c: route Phase 2/3 vẫn ĐƯỢC ĐĂNG KÝ, nhưng khi cờ tính năng tắt
 * thì element phải là `ComingSoonPage` — không được dẫn vào trang thật với catalogue giả bấm
 * được. Trước W1-E `featureFlags` khai `{community:false, map:false, shop:false}` mà
 * `router.tsx` không hề import nó (handoff H15.13).
 *
 * Test đọc thẳng cây `routes` thay vì render: nó khẳng định đúng điều cần khẳng định (route
 * nào trỏ vào đâu) mà không phải dựng session/guard/i18n giả.
 */

/** 10 route nằm sau cờ, kèm cờ chi phối. */
const GATED_ROUTES: [path: string, flag: "community" | "map" | "shop"][] = [
  ["/community", "community"],
  ["/community/posts/:postId", "community"],
  ["/community/new", "community"],
  ["/map", "map"],
  ["/map/clinics/:clinicId", "map"],
  ["/shop", "shop"],
  ["/shop/products/:productId", "shop"],
  ["/cart", "shop"],
  ["/checkout", "shop"],
  ["/orders/:orderId", "shop"],
];

/** Route Phase 1 — phải KHÔNG bị cờ chạm tới dù cờ bật hay tắt. */
const ALWAYS_REAL = ["/dashboard", "/credits", "/cats", "/export", "/account/privacy"];

function findRoute(routes: RouteObject[], path: string): RouteObject | undefined {
  for (const route of routes) {
    if (route.path === path) return route;
    const found = route.children ? findRoute(route.children, path) : undefined;
    if (found) return found;
  }
  return undefined;
}

function elementName(element: unknown): string {
  if (!isValidElement(element)) return "(not an element)";
  const { type } = element;
  return typeof type === "function" ? type.name : JSON.stringify(type);
}

afterEach(() => {
  vi.resetModules();
  vi.doUnmock("@/shared/config/featureFlags");
});

describe("router — cổng featureFlags cho route Phase 2/3", () => {
  it("mọi route sau cờ đều render ComingSoonPage khi cờ TẮT", async () => {
    vi.resetModules();
    vi.doMock("@/shared/config/featureFlags", () => ({
      featureFlags: { community: false, map: false, shop: false },
    }));
    const { routes } = await import("./router");

    for (const [path] of GATED_ROUTES) {
      const route = findRoute(routes, path);
      expect(route, `route ${path} phải được đăng ký (p9 §9.4.3)`).toBeDefined();
      expect(elementName(route?.element), `route ${path} khi cờ tắt`).toBe("ComingSoonPage");
    }
  });

  it("vẫn trỏ về trang thật khi cờ BẬT (bật lại chỉ cần đổi cờ, không sửa bảng route)", async () => {
    vi.resetModules();
    vi.doMock("@/shared/config/featureFlags", () => ({
      featureFlags: { community: true, map: true, shop: true },
    }));
    const { routes } = await import("./router");

    for (const [path] of GATED_ROUTES) {
      const route = findRoute(routes, path);
      expect(elementName(route?.element), `route ${path} khi cờ bật`).not.toBe("ComingSoonPage");
    }
  });

  it("route Phase 1 không bị cờ chạm vào", async () => {
    vi.resetModules();
    const { routes } = await import("./router");

    for (const path of ALWAYS_REAL) {
      const route = findRoute(routes, path);
      expect(route, `route ${path} phải tồn tại`).toBeDefined();
      expect(elementName(route?.element), `route ${path}`).not.toBe("ComingSoonPage");
    }
  });

  it("featureGated trả ComingSoonPage đúng theo cờ", async () => {
    vi.resetModules();
    vi.doMock("@/shared/config/featureFlags", () => ({
      featureFlags: { community: false, map: true, shop: false },
    }));
    const { featureGated } = await import("./router");
    const real = <div data-testid="real" />;

    expect(elementName(featureGated("community", real))).toBe("ComingSoonPage");
    expect(elementName(featureGated("shop", real))).toBe("ComingSoonPage");
    expect(featureGated("map", real)).toBe(real);
  });
});

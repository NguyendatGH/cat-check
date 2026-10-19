import { afterEach, describe, expect, it, vi } from "vitest";
import type { RouteObject } from "react-router";
import { isValidElement } from "react";

vi.mock("msw/browser", () => ({ setupWorker: () => ({ start: () => undefined, stop: () => undefined }) }));


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
  ["/orders", "shop"],
  ["/orders/:orderId", "shop"],
];

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

describe("router — cổng featureFlags cho route Phase 2/3", { timeout: 30_000 }, () => {
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

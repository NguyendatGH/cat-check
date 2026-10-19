import { expect, test } from "@playwright/test";

test("admin navigation expands, follows the active page, and works in the mobile sheet", async ({ page }) => {
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));

  await page.route("**/api/v1/auth/session", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        authenticated: true,
        user: {
          id: "admin-test",
          email: "admin@example.test",
          fullName: "Admin hệ thống",
          onboardingStatus: "COMPLETED",
        },
        roles: ["USER", "ADMIN_SUPER"],
      }),
    }),
  );
  await page.route("**/api/v1/admin/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith("/metrics/dashboard")
      ? { users: 1280, activeCats: 842, scans: 5067, failedJobs: 2, pendingOutbox: 18 }
      : path.endsWith("/system-status")
        ? { databaseReachable: true, checkedAt: "2026-10-12T08:00:00+07:00" }
        : { items: [], number: 0, size: 20, totalElements: 0, totalPages: 0, hasMore: false };
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/admin");
  const desktopNav = page.locator(".admin-sidebar .admin-nav-menu");
  await expect(desktopNav).toBeVisible();
  await expect(desktopNav.getByRole("link", { name: "Tổng quan" })).toHaveAttribute("aria-current", "page");
  await page.screenshot({ path: "/tmp/catcheck-admin-redesign-desktop.png", fullPage: true });
  await desktopNav.locator(".ant-menu-submenu-title").filter({ hasText: "Người dùng" }).click();
  await expect(desktopNav.getByRole("link", { name: "Nhân sự" })).toBeVisible();
  await desktopNav.getByRole("link", { name: "Người dùng" }).click();
  await expect(page).toHaveURL(/\/admin\/users$/);
  await expect(desktopNav.getByRole("link", { name: "Người dùng" })).toHaveAttribute("aria-current", "page");
  await expect(page.locator("#admin-main")).toContainText("Danh sách người dùng");

  await page.setViewportSize({ width: 375, height: 812 });
  await page.getByRole("button", { name: "Mở menu quản trị" }).click();
  const mobileNav = page.getByRole("dialog").locator(".admin-nav-menu");
  await expect(mobileNav).toBeVisible();
  await page.screenshot({ path: "/tmp/catcheck-admin-redesign-mobile.png", fullPage: true });
  await expect(mobileNav.getByRole("link", { name: "Người dùng" })).toHaveAttribute("aria-current", "page");
  await mobileNav.locator(".ant-menu-submenu-title").filter({ hasText: "Danh mục" }).click();
  await expect(mobileNav.getByRole("link", { name: "Sản phẩm" })).toBeVisible();
  await mobileNav.getByRole("link", { name: "Sản phẩm" }).click();
  await expect(page).toHaveURL(/\/admin\/products$/);
  await expect(page.getByRole("dialog")).toHaveCount(0);

  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.getByRole("button", { name: "Mở menu quản trị" }).click();
  await expect(page.getByRole("dialog").locator(".admin-nav-menu")).toBeVisible();
  expect(pageErrors).toEqual([]);
});

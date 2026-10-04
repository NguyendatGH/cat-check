import { expect, test } from "@playwright/test";

test("visitor can open the app and reach login", async ({ page }) => {
  await page.route("**/api/v1/auth/session", (route) =>
    route.fulfill({ status: 401, contentType: "application/problem+json", body: "{}" }),
  );

  await page.goto("/");
  await expect(page).toHaveTitle(/CatCheck/);

  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL(/\/auth\/login$/);

  // Khẳng định theo Ý ĐỊNH ("khách tới được màn đăng nhập"), không pin câu chữ của một
  // breakpoint. Bản trước pin "Chào mừng trở lại!" — đó là tiêu đề của cây mobile, nên test
  // đỏ ở project `chromium` (viewport desktop) ngay khi màn đăng nhập có bố cục desktop
  // riêng với tiêu đề "Đăng nhập vào CATCHECK Web". Cả hai cây đều có đúng một <h1> hiển
  // thị, nên kiểm sự tồn tại của h1 vừa đúng ở cả hai project, vừa giữ được giá trị thật
  // của phép kiểm: thiếu h1 là lỗi trợ năng.
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.getByLabel(/email/i).first()).toBeVisible();
  await expect(page.getByRole("button", { name: /đăng nhập/i }).first()).toBeVisible();
});

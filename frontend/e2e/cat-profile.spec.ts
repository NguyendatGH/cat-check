import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test.afterEach(async ({ page }) => {
  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  if (csrf) await page.request.post("/api/v1/auth/logout", { headers: { "X-XSRF-TOKEN": csrf } }).catch(() => undefined);
});

test("create a cat profile and soft-delete it using the real API", async ({ page }) => {
  const name = `E2E mèo ${String(Date.now())}`;
  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page.getByLabel(/mật khẩu/i).first().fill(DEMO.password);
  await page.getByRole("button", { name: /đăng nhập/i }).first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });

  await page.goto("/cats/new");
  await page.getByLabel("Tên bé mèo").fill(name);
  await page.getByText("Chưa rõ", { exact: true }).click();
  await page.getByText("Chỉ biết tuổi ước lượng", { exact: true }).click();
  await page.getByLabel("Tuổi ước lượng (tháng)").fill("12");

  const createPromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/v1/cats") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Lưu hồ sơ" }).click();
  const createResponse = await createPromise;
  expect(createResponse.status()).toBe(201);
  const created = (await createResponse.json()) as { id: string; name: string };
  expect(created.name).toBe(name);
  await expect(page).toHaveURL(new RegExp(`/cats/${created.id}$`));
  await expect(page.getByText(name).first()).toBeVisible();

  const updatedName = `${name} đã sửa`;
  await page.getByRole("button", { name: "Thao tác khác" }).click();
  await page.getByRole("button", { name: "Sửa hồ sơ" }).click();
  await expect(page).toHaveURL(new RegExp(`/cats/${created.id}/edit$`));
  await page.getByLabel("Tên bé mèo").fill(updatedName);
  await page.getByText("Chỉ biết tuổi ước lượng", { exact: true }).click();
  await page.getByLabel("Tuổi ước lượng (tháng)").fill("12");
  await page.getByLabel("Cân nặng (kg)").fill("3.2");
  const patchPromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/v1/cats/${created.id}`) && response.request().method() === "PATCH",
  );
  await page.getByRole("button", { name: "Lưu hồ sơ" }).click();
  const patchResponse = await patchPromise;
  expect(patchResponse.status()).toBe(200);
  expect(((await patchResponse.json()) as { name: string }).name).toBe(updatedName);
  await expect(page).toHaveURL(new RegExp(`/cats/${created.id}$`));
  await expect(page.getByText(updatedName).first()).toBeVisible();

  const detailResponse = await page.request.get(`/api/v1/cats/${created.id}`);
  expect(detailResponse.ok()).toBeTruthy();
  expect(((await detailResponse.json()) as { name: string }).name).toBe(updatedName);

  await page.getByRole("button", { name: "Thao tác khác" }).click();
  await page.getByRole("button", { name: "Xoá hồ sơ" }).click();
  const deletePromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/v1/cats/${created.id}`) && response.request().method() === "DELETE",
  );
  await page.getByRole("dialog").getByRole("button", { name: "Xoá hồ sơ" }).click();
  const deleteResponse = await deletePromise;
  expect(deleteResponse.status()).toBe(204);
  await expect(page).toHaveURL(/\/cats$/);

  const activeCatsResponse = await page.request.get("/api/v1/cats?status=ACTIVE");
  expect(activeCatsResponse.ok()).toBeTruthy();
  const activeCats = (await activeCatsResponse.json()) as { items: Array<{ id: string }> };
  expect(activeCats.items.some((cat) => cat.id === created.id)).toBe(false);
});

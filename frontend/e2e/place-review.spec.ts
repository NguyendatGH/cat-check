import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test.afterEach(async ({ page }) => {
  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  if (csrf) await page.request.post("/api/v1/auth/logout", { headers: { "X-XSRF-TOKEN": csrf } }).catch(() => undefined);
});

test("clinic review form persists a real review", async ({ page }) => {
  const review = `E2E review ${String(Date.now())}`;
  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page.getByLabel(/mật khẩu/i).first().fill(DEMO.password);
  await page.getByRole("button", { name: /đăng nhập/i }).first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });

  const placesResponse = await page.request.get("/api/v1/places?kind=CLINIC");
  expect(placesResponse.ok()).toBeTruthy();
  const places = (await placesResponse.json()) as Array<{ id: string }>;
  expect(places.length).toBeGreaterThan(0);
  await page.goto(`/map/clinics/${places[0].id}`);
  await page.locator("textarea:visible").first().fill(review);

  const responsePromise = page.waitForResponse(
    (response) => response.url().includes(`/api/v1/places/${places[0].id}/reviews`) && response.request().method() === "POST",
  );
  await page.locator("form:visible").getByRole("button", { name: "Gửi đánh giá" }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(204);
  await expect(page.getByRole("status").first()).toContainText("Đã lưu đánh giá");
  const reviewsResponse = await page.request.get(`/api/v1/places/${places[0].id}/reviews`);
  expect(reviewsResponse.ok()).toBeTruthy();
  const reviews = (await reviewsResponse.json()) as Array<{ rating: number; body: string | null; id: string }>;
  expect(reviews.some((item) => item.rating === 5 && item.body === review)).toBeTruthy();
  expect(Object.keys(reviews[0] ?? {})).not.toContain("userId");
  await expect(page.locator("li:visible").getByText(review)).toBeVisible();

});

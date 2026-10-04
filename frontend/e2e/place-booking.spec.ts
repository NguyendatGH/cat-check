import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test.use({ viewport: { width: 390, height: 844 } });

test.afterEach(async ({ page }) => {
  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  if (csrf) await page.request.post("/api/v1/auth/logout", { headers: { "X-XSRF-TOKEN": csrf } }).catch(() => undefined);
});

test("clinic booking form submits a real booking request", async ({ page }) => {
  const date = new Date();
  date.setDate(date.getDate() + 180);
  const dateValue = `${String(date.getFullYear())}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

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

  const bookingForm = page.locator("#clinic-booking-form-mobile");
  await bookingForm.getByLabel(/ngày mong muốn/i).fill(dateValue);
  await bookingForm.getByLabel(/giờ mong muốn/i).fill("09:47");
  await bookingForm.getByLabel(/ghi chú triệu chứng/i).fill("E2E booking request");
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes(`/api/v1/places/${places[0].id}/bookings`) && response.request().method() === "POST",
  );
  await bookingForm.getByRole("button", { name: /xác nhận đặt lịch/i }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(202);
  await expect(page.getByRole("status")).toContainText(/đã gửi yêu cầu/i);

});

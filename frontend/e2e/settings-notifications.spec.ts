import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };
type Preferences = {
  attentionAlertChannel: "PUSH_AND_INAPP" | "INAPP_ONLY";
  creditAlertsEnabled: boolean;
  reportReadyEnabled: boolean;
  normalResultEnabled: boolean;
  imageRetentionWarningEnabled: boolean;
  quietHoursEnabled: boolean;
  quietHoursStart: string;
  quietHoursEnd: string;
};

test.afterEach(async ({ page }) => {
  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  if (csrf) await page.request.post("/api/v1/auth/logout", { headers: { "X-XSRF-TOKEN": csrf } }).catch(() => undefined);
});

test("notification preferences save and reload from the real account API", async ({ page }) => {
  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page.getByLabel(/mật khẩu/i).first().fill(DEMO.password);
  await page.getByRole("button", { name: /đăng nhập/i }).first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });

  const originalResponse = await page.request.get("/api/v1/account/notification-preferences");
  expect(originalResponse.ok()).toBeTruthy();
  const original = (await originalResponse.json()) as Preferences;
  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  expect(csrf).not.toBe("");

  try {
    await page.goto("/settings/notifications");
    const toggle = page.getByRole("switch", { name: "Lượt quét sắp hết hạn" });
    await expect(toggle).toHaveAttribute("aria-checked", String(original.creditAlertsEnabled));
    const updated = { ...original, creditAlertsEnabled: !original.creditAlertsEnabled };
    const savePromise = page.waitForResponse(
      (response) => response.url().endsWith("/api/v1/account/notification-preferences") && response.request().method() === "PUT",
    );
    await toggle.click();
    const saveResponse = await savePromise;
    expect(saveResponse.ok()).toBeTruthy();
    await expect(toggle).toHaveAttribute("aria-checked", String(updated.creditAlertsEnabled));

    await page.reload();
    await expect(page.getByRole("switch", { name: "Lượt quét sắp hết hạn" })).toHaveAttribute(
      "aria-checked",
      String(updated.creditAlertsEnabled),
    );
    const persisted = (await (await page.request.get("/api/v1/account/notification-preferences")).json()) as Preferences;
    expect(persisted).toEqual(updated);
  } finally {
    const restored = await page.request.put("/api/v1/account/notification-preferences", {
      headers: { "X-XSRF-TOKEN": csrf },
      data: original,
    });
    expect(restored.ok()).toBeTruthy();
  }
});

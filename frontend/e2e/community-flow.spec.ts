import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test("create community post, open its real detail and persist a comment", async ({ page }) => {
  const suffix = String(Date.now());
  const title = `E2E cộng đồng ${suffix}`;
  const body = "Bài chia sẻ kiểm thử luồng cộng đồng với backend thật.";
  const comment = `Bình luận kiểm thử ${suffix}`;

  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page.getByLabel(/mật khẩu/i).first().fill(DEMO.password);
  await page.getByRole("button", { name: /đăng nhập/i }).first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });
  await page.goto("/community/new");
  await page.getByLabel("Tiêu đề thảo luận").fill(title);
  await page.getByLabel("Nội dung chia sẻ").fill(body);

  const createResponsePromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/v1/community/posts") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Đăng thảo luận" }).click();
  const createResponse = await createResponsePromise;
  expect(createResponse.status()).toBe(201);
  const created = (await createResponse.json()) as { id: string; title: string };
  expect(created.title).toBe(title);

  await expect(page).toHaveURL(/\/community$/);
  await page.goto(`/community/posts/${created.id}`);
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  await expect(page.getByText(body)).toBeVisible();

  await page.getByPlaceholder("Chia sẻ kinh nghiệm hoặc đặt câu hỏi với BS. Lan Phương và các sen khác…").fill(comment);
  const commentResponsePromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/v1/community/posts/${created.id}/comments`)
      && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Gửi", exact: true }).click();
  const commentResponse = await commentResponsePromise;
  expect(commentResponse.status()).toBe(200);
  await expect(page.getByText(comment)).toBeVisible();

  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  await page.request.post("/api/v1/auth/logout", { headers: { "X-XSRF-TOKEN": csrf } });
});

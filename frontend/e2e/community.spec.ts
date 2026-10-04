import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test.afterEach(async ({ page }) => {
  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  if (csrf) await page.request.post("/api/v1/auth/logout", { headers: { "X-XSRF-TOKEN": csrf } }).catch(() => undefined);
});

test("create a community discussion, comment, and react through real APIs", async ({ page }) => {
  const suffix = String(Date.now());
  const title = `E2E thảo luận ${suffix}`;
  const body = `Nội dung kiểm thử cộng đồng ${suffix}`;
  const comment = `Bình luận kiểm thử ${suffix}`;

  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page.getByLabel(/mật khẩu/i).first().fill(DEMO.password);
  await page.getByRole("button", { name: /đăng nhập/i }).first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });

  await page.goto("/community/new");
  await page.getByLabel("Tiêu đề thảo luận").fill(title);
  await page.getByLabel("Nội dung chia sẻ").fill(body);
  const createPromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/v1/community/posts") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Đăng thảo luận" }).click();
  const createResponse = await createPromise;
  expect(createResponse.status()).toBe(201);
  const created = (await createResponse.json()) as { id: string; title: string };
  expect(created.title).toBe(title);
  await expect(page).toHaveURL(new RegExp(`/community/posts/${created.id}$`));
  await expect(page.getByRole("heading", { name: title })).toBeVisible();

  await page.getByPlaceholder(/chia sẻ kinh nghiệm/i).fill(comment);
  const commentPromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/v1/community/posts/${created.id}/comments`) && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Gửi" }).click();
  const commentResponse = await commentPromise;
  expect(commentResponse.ok()).toBeTruthy();
  await expect(page.getByText(comment)).toBeVisible();

  const reactionPromise = page.waitForResponse(
    (response) => response.url().endsWith(`/api/v1/community/posts/${created.id}/reactions`) && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Thích" }).click();
  const reactionResponse = await reactionPromise;
  expect(reactionResponse.ok()).toBeTruthy();
  const detailResponse = await page.request.get(`/api/v1/community/posts/${created.id}`);
  expect(detailResponse.ok()).toBeTruthy();
  const detail = (await detailResponse.json()) as {
    post: { liked: boolean };
    comments: Array<{ body: string }>;
  };
  expect(detail.post.liked).toBe(true);
  expect(detail.comments.some((item) => item.body === comment)).toBe(true);

  await page.goto("/community");
  await page.getByRole("button", { name: "Hỏi đáp Bác sĩ Thú y" }).click();
  await expect(page.getByText(title).first()).toBeVisible();
  await page.getByRole("button", { name: "Kinh nghiệm đọc màu cát" }).click();
  await expect(page.getByText(title)).toHaveCount(0);
});

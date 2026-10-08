import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test.afterEach(async ({ page }) => {
  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  if (csrf)
    await page.request.post("/api/v1/auth/logout", { headers: { "X-XSRF-TOKEN": csrf } }).catch(() => undefined);
});

test("create a community discussion, comment, and react through real APIs", async ({ page }) => {
  const suffix = String(Date.now());
  const title = `E2E thảo luận ${suffix}`;
  const body = `Nội dung kiểm thử cộng đồng ${suffix}`;
  const comment = `Bình luận kiểm thử ${suffix}`;

  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page
    .getByLabel(/mật khẩu/i)
    .first()
    .fill(DEMO.password);
  await page
    .getByRole("button", { name: /đăng nhập/i })
    .first()
    .click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });

  // Form rỗng ⇒ báo lỗi tại chỗ, KHÔNG gửi request.
  await page.goto("/community/new");
  await page.getByRole("button", { name: "Đăng bài", exact: true }).click();
  await expect(page.getByText("Vui lòng nhập tiêu đề.")).toBeVisible();
  await expect(page.getByText("Vui lòng nhập nội dung.")).toBeVisible();

  // Chuyên mục mặc định là "Hỏi & đáp" (QA).
  await page.getByLabel("Tiêu đề").fill(title);
  await page.getByLabel("Nội dung").fill(body);
  const createPromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/v1/community/posts") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Đăng bài", exact: true }).click();
  const createResponse = await createPromise;
  expect(createResponse.status()).toBe(201);
  const created = (await createResponse.json()) as { id: string; title: string; category: string };
  expect(created.title).toBe(title);
  expect(created.category).toBe("QA");
  await expect(page).toHaveURL(new RegExp(`/community/posts/${created.id}$`));
  await expect(page.getByRole("heading", { name: title })).toBeVisible();

  await page.getByLabel("Bình luận của bạn").fill(comment);
  const commentPromise = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/api/v1/community/posts/${created.id}/comments`) &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Gửi bình luận" }).click();
  const commentResponse = await commentPromise;
  expect(commentResponse.ok()).toBeTruthy();
  await expect(page.getByText(comment)).toBeVisible();

  const reactionPromise = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/api/v1/community/posts/${created.id}/reactions`) &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Thích", exact: true }).click();
  const reactionResponse = await reactionPromise;
  expect(reactionResponse.ok()).toBeTruthy();
  await expect(page.getByRole("button", { name: "Thích", exact: true })).toHaveAttribute("aria-pressed", "true");
  const detailResponse = await page.request.get(`/api/v1/community/posts/${created.id}`);
  expect(detailResponse.ok()).toBeTruthy();
  const detail = (await detailResponse.json()) as {
    post: { liked: boolean };
    comments: Array<{ body: string }>;
  };
  expect(detail.post.liked).toBe(true);
  expect(detail.comments.some((item) => item.body === comment)).toBe(true);

  // Lọc chuyên mục chạy ở server (`?category=`).
  await page.goto("/community");
  const qaPromise = page.waitForResponse((response) => response.url().includes("/api/v1/community/posts?category=QA"));
  await page.getByRole("button", { name: "Hỏi & đáp" }).click();
  await qaPromise;
  await expect(page.getByRole("link", { name: title })).toBeVisible();
  const experiencePromise = page.waitForResponse((response) =>
    response.url().includes("/api/v1/community/posts?category=EXPERIENCE"),
  );
  await page.getByRole("button", { name: "Kinh nghiệm" }).click();
  await experiencePromise;
  await expect(page.getByText(title)).toHaveCount(0);
});

test("report a community post through the ⋯ menu", async ({ page }) => {
  const suffix = String(Date.now());
  const title = `E2E báo cáo ${suffix}`;

  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page
    .getByLabel(/mật khẩu/i)
    .first()
    .fill(DEMO.password);
  await page
    .getByRole("button", { name: /đăng nhập/i })
    .first()
    .click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });

  await page.goto("/community/new");
  await page.getByText("Mẹo chăm sóc", { exact: true }).click();
  await page.getByLabel("Tiêu đề").fill(title);
  await page.getByLabel("Nội dung").fill("Bài kiểm thử luồng báo cáo vi phạm.");
  await page.getByRole("button", { name: "Đăng bài", exact: true }).click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();

  await page.locator("article").getByRole("button", { name: "Tuỳ chọn khác" }).click();
  await page.getByRole("button", { name: "Báo cáo vi phạm" }).click();
  const dialog = page.getByRole("dialog", { name: "Báo cáo bài viết" });
  await expect(dialog).toBeVisible();

  // Chưa chọn lý do ⇒ lỗi tại chỗ; "Lý do khác" bắt buộc mô tả.
  await dialog.getByRole("button", { name: "Gửi báo cáo" }).click();
  await expect(dialog.getByText("Vui lòng chọn một lý do.")).toBeVisible();
  await dialog.getByLabel("Lý do khác").check();
  await dialog.getByRole("button", { name: "Gửi báo cáo" }).click();
  await expect(dialog.getByText("Vui lòng mô tả ngắn lý do.")).toBeVisible();

  await dialog.getByLabel("Spam hoặc quảng cáo").check();
  const reportPromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/v1/community/reports") && response.request().method() === "POST",
  );
  await dialog.getByRole("button", { name: "Gửi báo cáo" }).click();
  const reportResponse = await reportPromise;
  expect(reportResponse.status()).toBe(204);
  expect(JSON.parse(reportResponse.request().postData() ?? "{}")).toMatchObject({ reason: "SPAM" });
  await expect(dialog).toBeHidden();
  await expect(page.getByText("Đã gửi báo cáo tới đội kiểm duyệt.", { exact: false })).toBeVisible();
});

import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test("create community post, open its real detail and persist a comment", async ({ page }) => {
  const suffix = String(Date.now());
  const title = `E2E cộng đồng ${suffix}`;
  const body = "Bài chia sẻ kiểm thử luồng cộng đồng với backend thật.";
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
  await page.goto("/community/new");
  await page.getByText("Kinh nghiệm", { exact: true }).click();
  await page.getByLabel("Tiêu đề").fill(title);
  await page.getByLabel("Nội dung").fill(body);
  await page.getByLabel("Thẻ").fill("khay cát, #thói quen");

  const createResponsePromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/v1/community/posts") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Đăng bài", exact: true }).click();
  const createResponse = await createResponsePromise;
  expect(createResponse.status()).toBe(201);
  const created = (await createResponse.json()) as { id: string; title: string; category: string; tags: string[] };
  expect(created.title).toBe(title);
  expect(created.category).toBe("EXPERIENCE");
  expect(created.tags).toEqual(["khay cát", "thói quen"]);

  // Đăng xong chuyển thẳng sang trang chi tiết bài vừa tạo.
  await expect(page).toHaveURL(new RegExp(`/community/posts/${created.id}$`));
  await page.reload();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  await expect(page.getByText(body)).toBeVisible();
  await expect(page.getByText("Chưa có bình luận nào.", { exact: false })).toBeVisible();

  await page.getByLabel("Bình luận của bạn").fill(comment);
  const commentResponsePromise = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/api/v1/community/posts/${created.id}/comments`) &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Gửi bình luận" }).click();
  const commentResponse = await commentResponsePromise;
  expect(commentResponse.status()).toBe(200);
  await expect(page.getByText(comment)).toBeVisible();
  await expect(page.getByText("1 bình luận")).toBeVisible();

  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  await page.request.post("/api/v1/auth/logout", { headers: { "X-XSRF-TOKEN": csrf } });
});

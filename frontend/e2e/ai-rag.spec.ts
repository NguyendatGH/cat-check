import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test("RAG trả câu trả lời có nguồn từ care tips trên backend thật", async ({ page }) => {
  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page.getByLabel(/mật khẩu/i).first().fill(DEMO.password);
  await page.getByRole("button", { name: /đăng nhập/i }).first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });

  await page.goto("/assistant");
  await page.getByLabel("Câu hỏi cho trợ lý").fill("Làm thế nào chụp ảnh khay cát để đọc màu rõ hơn?");
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes("/api/v1/ai/chat") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Gửi câu hỏi" }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const result = (await response.json()) as {
    provider: string;
    answer: string;
    citations: Array<{ title: string; sourceUrl: string }>;
  };

  expect(result.provider).toBe("retrieval-fallback");
  expect(result.answer).toContain("Theo tài liệu CatCheck");
  expect(result.citations.length).toBeGreaterThan(0);
  expect(result.citations.some((citation) => citation.sourceUrl.includes("phuong-phap-chup-anh-cat"))).toBe(true);
  await expect(page.getByText("Cách chụp ảnh khay cát cho dễ đọc màu").first()).toBeVisible();
});

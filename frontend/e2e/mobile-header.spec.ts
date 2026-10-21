import { expect, test } from "@playwright/test";

// Hàng phím tắt (Bản đồ Thú y…) từng nhấp nháy không dứt trên điện thoại: thu gọn làm header thấp đi,
// Chrome neo cuộn (scroll anchoring) dời scrollY đúng bằng chiều cao đó, handler đọc thành cuộn ngược
// chiều rồi mở lại, lặp mãi. jsdom không có layout nên chỉ bắt được lỗi này trên trình duyệt thật.
test("mobile header shortcut row settles after scrolling stops", async ({ page }) => {
  await page.route("**/api/v1/auth/session", (route) =>
    route.fulfill({ status: 401, contentType: "application/problem+json", body: "{}" }),
  );
  await page.setViewportSize({ width: 412, height: 839 });
  await page.goto("/dashboard");
  await expect(page.locator("header[data-collapsed]")).toBeVisible();

  const result = await page.evaluate(async () => {
    const main = document.querySelector("main");
    const header = document.querySelector<HTMLElement>("header[data-collapsed]");
    if (!main || !header) throw new Error("app shell not rendered");
    // Trang khách không có dữ liệu: độn nội dung để trang cuộn được như dashboard thật.
    for (let i = 0; i < 30; i++) {
      const row = document.createElement("p");
      row.style.height = "80px";
      main.append(row);
    }
    const frame = () => new Promise((resolve) => requestAnimationFrame(resolve));
    for (let i = 0; i < 40; i++) {
      window.scrollBy({ top: 4, behavior: "instant" });
      await frame();
    }
    let flips = 0;
    let last = header.dataset.collapsed;
    for (let i = 0; i < 90; i++) {
      await frame();
      if (header.dataset.collapsed !== last) flips++;
      last = header.dataset.collapsed;
    }
    return { flips, collapsed: last };
  });

  expect(result).toEqual({ flips: 0, collapsed: "true" });
});

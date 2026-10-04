import { expect, test } from "@playwright/test";

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

test("cart, checkout and order detail persist against native Shop API", async ({ page }) => {
  await page.goto("/auth/login");
  await page.getByLabel(/email/i).first().fill(DEMO.email);
  await page.getByLabel(/mật khẩu/i).first().fill(DEMO.password);
  await page.getByRole("button", { name: /đăng nhập/i }).first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth/login"), { timeout: 20_000 });
  await page.goto("/shop");

  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match?.[1] ? decodeURIComponent(match[1]) : "";
  });
  expect(csrf).not.toBe("");
  const productsResponse = await page.request.get("/api/v1/shop/products");
  expect(productsResponse.ok()).toBe(true);
  const products = (await productsResponse.json()) as Array<{ id: string; sku: string; priceVnd: number }>;
  const product = products.find((item) => item.sku === "SMARTSAND-BIO-6L");
  expect(product).toBeDefined();
  if (!product) throw new Error("Seed product SMARTSAND-BIO-6L missing");

  const headers = { "X-XSRF-TOKEN": csrf };
  const cartResponse = await page.request.put(`/api/v1/cart/${product.id}`, {
    headers,
    data: { quantity: 1 },
  });
  expect(cartResponse.status()).toBe(200);
  await page.goto("/cart");
  await expect(page.getByText("Cát thông minh CATCHECK SmartSand Bio 6L").first()).toBeVisible();
  await page.getByLabel(/Thanh toán khi nhận hàng/).check({ force: true });
  await page.getByRole("button", { name: /Tiến hành Thanh toán/i }).click();
  await page.getByLabel("Tên người nhận").fill("CatCheck E2E");
  await page.getByLabel("Số điện thoại").fill("0900000000");
  await page.getByLabel("Địa chỉ nhận hàng").fill("Địa chỉ kiểm thử CatCheck, Đà Nẵng");
  const orderResponsePromise = page.waitForResponse(
    (response) => response.url().endsWith("/api/v1/orders") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Đặt hàng" }).click();
  const createdResponse = await orderResponsePromise;
  expect(createdResponse.status()).toBe(201);
  const order = (await createdResponse.json()) as {
    id: string;
    orderCode: string;
    status: string;
    totalVnd: number;
    lines: Array<{ product: { sku: string }; quantity: number }>;
  };
  expect(order.status).toBe("PENDING_PAYMENT");
  expect(order.lines).toHaveLength(1);
  expect(order.lines[0]).toMatchObject({ product: { sku: product.sku }, quantity: 1 });
  expect(order.totalVnd).toBe(product.priceVnd + (product.priceVnd >= 500_000 ? 0 : 30_000));

  const detailResponse = await page.request.get(`/api/v1/orders/${order.id}`);
  expect(detailResponse.status()).toBe(200);
  const detail = (await detailResponse.json()) as typeof order;
  expect(detail.orderCode).toBe(order.orderCode);

  await page.goto(`/orders/${order.id}`);
  await expect(page.getByText(`#${order.orderCode}`)).toBeVisible();
  await expect(page.getByText("CatCheck E2E")).toBeVisible();
  await expect(page.getByRole("definition").filter({ hasText: /Chờ thanh toán|order\.status\.PENDING_PAYMENT/ })).toBeVisible();

  await page.request.post("/api/v1/auth/logout", { headers });
});

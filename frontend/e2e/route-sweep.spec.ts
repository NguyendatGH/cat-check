import { expect, test, type Page } from "@playwright/test";

/**
 * Quét MỌI route với backend THẬT và bắt bốn loại lỗi mà unit test không chạm tới:
 * điều hướng sai/404, lỗi JS lúc chạy, API trả 4xx/5xx, và chuỗi lọt ra UI (khoá i18n thô
 * hoặc placeholder "TODO" còn sót).
 *
 * Vì sao là e2e chứ không phải unit: ba trong bốn loại trên chỉ xuất hiện khi router, guard
 * phiên, i18n và backend chạy cùng nhau. Mọi lần trước đều phải dựng script tạm ngoài repo
 * rồi mất khi reboot — nên nó nằm hẳn ở đây.
 *
 * CẦN backend chạy ở :8080 (`backend/run-local.sh`) và tài khoản demo đã seed. Không có thì
 * test SKIP chứ không fail đỏ — CI chưa dựng backend, và một test đỏ vì thiếu môi trường sẽ
 * nhanh chóng bị ai đó tắt đi.
 *
 * ponytail: dùng một `page` tuần tự cho cả bộ thay vì mỗi route một context. Trần của nó là
 * thời gian chạy tuyến tính (~25 route × ~1.5s). Lý do không song song hoá: mỗi context mới
 * = một phiên thiết bị mới trên server, và `POST /auth/login` trả 409 SESSION_LIMIT_REACHED
 * ở phiên thứ 6. Muốn nhanh hơn thì phải nâng trần phiên cho tài khoản test, không phải thêm
 * worker.
 */

const DEMO = { email: "demo@catcheck.vn", password: "DemoCat2025!" };

/** Route không cần đăng nhập. Phải quét TRƯỚC khi login: có phiên thì guard đá về /dashboard. */
const ANON = ["/", "/auth/login", "/auth/register", "/auth/verify-otp"];

/** Route cần đăng nhập. `:id` được thay bằng id thật lấy từ API. */
const AUTHED = [
  "/dashboard",
  "/onboarding/cat",
  "/onboarding/health-survey",
  "/scan",
  "/scan/select-cat",
  "/scans/:scanId",
  "/scan/result/:scanId",
  "/cats",
  "/cats/:catId",
  "/cats/:catId/history",
  "/cats/:catId/trends",
  "/reminders",
  "/reminders/new",
  "/export",
  "/community",
  "/community/new",
  "/map",
  "/shop",
  "/cart",
  "/checkout",
  "/credits",
  "/settings",
  "/settings/profile",
  "/settings/security",
  "/settings/notifications",
  "/settings/language",
  "/account/privacy",
  "/notifications",
  "/install",
  "/legal/terms",
  "/legal/privacy",
];

interface Ids {
  catId: string;
  /** Scan đầu tiên — thường là bản đầy đủ field. */
  scanId: string;
  /**
   * Một scan mà backend BỎ BỚT field (classification `INCONCLUSIVE` ⇒ không có `phValue`,
   * `labL`… trong JSON vì `default-property-inclusion: non_null`). Bản quét trước chỉ lấy
   * `scanId` đầu tiên nên bỏ lọt một màn hình trắng thật: `analysis.labL.toFixed(...)` trên
   * field vắng mặt. Null khi dữ liệu demo không có scan nào như vậy.
   */
  sparseScanId: string | null;
}

async function backendIds(page: Page): Promise<Ids | null> {
  const cats = await page.request.get("/api/v1/cats");
  const scans = await page.request.get("/api/v1/scans");
  if (!cats.ok() || !scans.ok()) return null;
  const catItems = ((await cats.json()) as { items?: { id: string }[] }).items ?? [];
  const scanItems = ((await scans.json()) as { items?: { scanId: string; phValue?: number }[] }).items ?? [];
  if (!catItems[0] || !scanItems[0]) return null;
  const sparse = scanItems.find((s) => s.phValue === undefined);
  return {
    catId: catItems[0].id,
    scanId: scanItems[0].scanId,
    sparseScanId: sparse?.scanId ?? null,
  };
}

test.describe("quét toàn bộ route với backend thật", () => {
  // Tuần tự: xem ghi chú ponytail ở đầu file.
  test.describe.configure({ mode: "serial" });

  test("mọi route render sạch", async ({ page }) => {
    test.slow();

    const jsErrors: string[] = [];
    const apiErrors: string[] = [];
    page.on("pageerror", (e) => jsErrors.push(String(e).slice(0, 200)));
    page.on("response", (r) => {
      if (r.url().includes("/api/v1") && r.status() >= 400) {
        const pathname = new URL(r.url()).pathname;
        // A cat with no submitted survey legitimately returns 404; useHealthSurvey converts
        // this into the empty state instead of surfacing an error to the user.
        if (r.status() === 404 && /\/cats\/[^/]+\/health-survey$/.test(pathname)) return;
        apiErrors.push(`${String(r.status())} ${pathname}`);
      }
    });

    const failures: string[] = [];

    const visit = async (route: string) => {
      jsErrors.length = 0;
      apiErrors.length = 0;
      await page.goto(route, { waitUntil: "networkidle" });
      const landed = new URL(page.url()).pathname;
      const body = (await page.locator("body").textContent()) ?? "";

      if (landed !== route) failures.push(`${route} → điều hướng sang ${landed}`);
      if (body.includes("Không tìm thấy trang")) failures.push(`${route} → 404`);
      if (body.includes("TODO: nội dung thật")) failures.push(`${route} → còn placeholder TODO`);
      // Khoá i18n thô lọt ra UI, ví dụ "settings.notifications.title" hiện nguyên văn.
      const rawKey = /\b[a-z][a-zA-Z]*\.[a-z][a-zA-Z]*\.[a-zA-Z.]+\b/.exec(body);
      if (rawKey && !rawKey[0].includes("catcheck.vn")) {
        failures.push(`${route} → khoá i18n thô: ${rawKey[0]}`);
      }
      if (jsErrors.length) failures.push(`${route} → lỗi JS: ${jsErrors.join(" | ")}`);
      if (apiErrors.length) failures.push(`${route} → API lỗi: ${[...new Set(apiErrors)].join(", ")}`);
    };

    for (const route of ANON) await visit(route);

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
    await page.waitForURL((u) => !u.pathname.startsWith("/auth/login"), { timeout: 20_000 });

    const ids = await backendIds(page);
    if (ids === null) {
      test.skip(
        true,
        "Thiếu backend ở :8080 hoặc dữ liệu demo (cần >=1 mèo và >=1 lần quét). Chạy backend/run-local.sh rồi thử lại.",
      );
      return;
    }

    for (const route of AUTHED) {
      await visit(route.replace(":catId", ids.catId).replace(":scanId", ids.scanId));
    }

    // Lặp lại hai màn phụ thuộc dữ liệu scan với bản THIẾU FIELD — xem ghi chú `sparseScanId`.
    if (ids.sparseScanId !== null) {
      await visit(`/scans/${ids.sparseScanId}`);
      await visit(`/scan/result/${ids.sparseScanId}`);
    }

    expect(failures, `\n${failures.join("\n")}\n`).toEqual([]);
  });
});

/**
 * MSW handlers + worker cho auth — chế độ degraded khi backend Java chưa chạy cùng máy
 * (cùng vai trò với `features/onboarding/mocks.ts`). Backend thật (A1) đã có sẵn với
 * đúng hợp đồng này — xem `docs/handovers/A1-fe.md` để nối thật khi có docker-compose.
 *
 * Endpoint mock (khớp `AuthController`/`AccountController` thật):
 *  - GET  /api/v1/auth/csrf
 *  - POST /api/v1/auth/register                          (A3)
 *  - POST /api/v1/auth/otp/request                        (A4)
 *  - POST /api/v1/auth/otp/verify                         (A5)
 *  - POST /api/v1/auth/login                               (A6)
 *  - POST /api/v1/auth/logout                              (A7)
 *  - POST /api/v1/auth/password-reset/request              (A8)
 *  - POST /api/v1/auth/password-reset/confirm              (A9)
 *  - POST /api/v1/auth/totp/verify                         (A10)
 *  - POST /api/v1/auth/totp/recovery                       (A11)
 *  - GET  /api/v1/auth/sessions                            (A13)
 *  - DELETE /api/v1/auth/sessions/:id                      (A14)
 *  - POST /api/v1/auth/sessions/revoke-all                 (A15)
 *  - GET  /api/v1/privacy/purposes                         (C1, dùng cho consent ở đăng ký)
 *  - GET/PATCH /api/v1/users/me                            (B1/B2)
 *  - GET  /api/v1/account/mfa/totp                         (B14)
 *  - POST /api/v1/account/mfa/totp/init                    (B15)
 *  - POST /api/v1/account/mfa/totp/confirm                 (B16)
 */

import { http, HttpResponse, delay } from "msw";
import { setupWorker } from "msw/browser";
import type { ConsentPurposeOption, SessionItem } from "./types";

function problem(status: number, code: string, detail: string, extra?: Record<string, unknown>) {
  return HttpResponse.json(
    {
      type: `https://catcheck.vn/problems/${code.toLowerCase().replace(/_/g, "-")}`,
      title: code,
      status,
      detail,
      instance: "",
      // Tên field là `errorCode`, KHÔNG phải `code`: `GlobalExceptionHandler` của backend gọi
      // `problemDetail.setProperty("errorCode", ...)`. Mock từng phát `code` nên khớp với
      // wrapper client cũng đang đọc sai — hai cái sai che nhau, chỉ lộ ra khi chạy server thật.
      errorCode: code,
      ...extra,
    },
    { status, headers: { "Content-Type": "application/problem+json; charset=utf-8" } },
  );
}

const PURPOSES: ConsentPurposeOption[] = [
  {
    code: "SERVICE_CORE",
    label: "Xử lý dữ liệu tài khoản và hồ sơ mèo để cung cấp dịch vụ CatCheck",
    description: "Bắt buộc để tạo và vận hành tài khoản của bạn.",
    mandatory: true,
    sensitive: false,
    defaultState: true,
    withdrawEffect: "Không tạo được tài khoản.",
    phase: 1,
  },
  {
    code: "HEALTH_REMINDER_EMAIL",
    label: "Gửi email nhắc theo dõi khay cát",
    description: "CatCheck gửi email nhắc bạn theo lịch quét đã đặt.",
    mandatory: false,
    sensitive: false,
    defaultState: false,
    withdrawEffect: "Bạn vẫn có nhắc trong app.",
    phase: 1,
  },
  {
    code: "MARKETING_EMAIL",
    label: "Nhận email giới thiệu sản phẩm, khuyến mãi, kiến thức chăm mèo",
    description: "Tối đa 4 email/tháng. Có thể huỷ đăng ký bất cứ lúc nào.",
    mandatory: false,
    sensitive: false,
    defaultState: false,
    withdrawEffect: "Không ảnh hưởng gì.",
    phase: 1,
  },
  {
    code: "PRODUCT_ANALYTICS",
    label: "Ghi nhận hành vi sử dụng để cải thiện sản phẩm",
    description: "Có thể được coi là dữ liệu cá nhân nhạy cảm theo pháp luật Việt Nam.",
    mandatory: false,
    sensitive: true,
    defaultState: false,
    withdrawEffect: "Không ảnh hưởng gì.",
    phase: 1,
  },
  {
    code: "ALGO_IMPROVEMENT",
    label: "Dùng ảnh đã khử nhận dạng để cải thiện thuật toán nhận diện màu",
    description: "Ảnh được khử nhận dạng trước khi dùng, không dùng để nhận diện lại bạn.",
    mandatory: false,
    sensitive: false,
    defaultState: false,
    withdrawEffect: "Không ảnh hưởng gì tới dịch vụ.",
    phase: 1,
  },
];

interface MockUser {
  id: string;
  email: string;
  password: string;
  fullName: string;
  totpEnabled: boolean;
}

const users = new Map<string, MockUser>();
const otpCode = "000000";

function findUserByEmail(email: string): MockUser | undefined {
  return [...users.values()].find((u) => u.email.toLowerCase() === email.toLowerCase());
}

const MOCK_SESSIONS: SessionItem[] = [
  {
    id: "11111111-1111-4111-8111-111111111111",
    deviceLabel: "Chrome trên Windows",
    ipMasked: "14.169.xxx.*",
    lastSeenAt: new Date().toISOString(),
    createdAt: new Date(Date.now() - 86_400_000).toISOString(),
    current: true,
  },
  {
    id: "22222222-2222-4222-8222-222222222222",
    deviceLabel: "CatCheck trên iPhone",
    ipMasked: "27.72.xxx.*",
    lastSeenAt: new Date(Date.now() - 3 * 86_400_000).toISOString(),
    createdAt: new Date(Date.now() - 10 * 86_400_000).toISOString(),
    current: false,
  },
];

export const handlers = [
  http.get("/api/v1/auth/csrf", () => new HttpResponse(null, { status: 204 })),

  http.get("/api/v1/privacy/purposes", async () => {
    await delay(100);
    return HttpResponse.json({ items: PURPOSES, limit: PURPOSES.length, nextCursor: null, hasMore: false });
  }),

  http.post("/api/v1/auth/register", async ({ request }) => {
    await delay(300);
    const body = (await request.json()) as { email: string; password: string; fullName: string; otpTicket?: string };
    if (body.otpTicket) {
      const existing = findUserByEmail(body.email);
      if (existing) {
        return HttpResponse.json(
          { userId: existing.id, email: existing.email, emailVerified: true, authenticated: true },
          { status: 200 },
        );
      }
      return problem(400, "OTP_TICKET_INVALID", "Mã xác thực không hợp lệ hoặc đã hết hạn.");
    }
    if (findUserByEmail(body.email)) {
      return problem(409, "EMAIL_ALREADY_REGISTERED", "Email này đã được đăng ký.");
    }
    const id = crypto.randomUUID();
    users.set(id, { id, email: body.email, password: body.password, fullName: body.fullName, totpEnabled: false });
    return HttpResponse.json(
      { userId: id, email: body.email, emailVerified: false, authenticated: false },
      { status: 202 },
    );
  }),

  http.post("/api/v1/auth/otp/request", async ({ request }) => {
    await delay(200);
    const body = (await request.json()) as { email: string; purpose: string };
    return HttpResponse.json({
      otpExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
      canResendInSeconds: 60,
      maskedEmail: body.email.replace(/^(.).*(@.*)$/, "$1***$2"),
    });
  }),

  http.post("/api/v1/auth/otp/verify", async ({ request }) => {
    await delay(300);
    const body = (await request.json()) as { code: string; purpose: string };
    if (body.code !== otpCode) {
      return problem(400, "OTP_INVALID", "Mã xác thực không đúng.", { attemptsLeft: 4 });
    }
    return HttpResponse.json({
      otpTicket: `mock-ticket-${crypto.randomUUID()}`,
      ticketExpiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
      purpose: body.purpose,
    });
  }),

  http.post("/api/v1/auth/login", async ({ request }) => {
    await delay(350);
    const body = (await request.json()) as { email: string; password: string };
    const user = findUserByEmail(body.email);
    if (!user || user.password !== body.password) {
      return problem(401, "INVALID_CREDENTIALS", "Sai email hoặc mật khẩu.");
    }
    if (user.totpEnabled) {
      return HttpResponse.json({ mfaRequired: true, mfaMethods: ["TOTP"] });
    }
    return HttpResponse.json({ authenticated: true, userId: user.id, roles: ["USER"] });
  }),

  http.post("/api/v1/auth/logout", () => new HttpResponse(null, { status: 204 })),

  http.get("/api/v1/auth/session", async () => {
    await delay(150);
    return HttpResponse.json({
      authenticated: users.size > 0,
      user: null,
      roles: users.size > 0 ? ["USER"] : [],
      mfa: null,
      serverTime: new Date().toISOString(),
    });
  }),

  http.post("/api/v1/auth/password-reset/request", async () => {
    await delay(250);
    return new HttpResponse(null, { status: 202 });
  }),

  http.post("/api/v1/auth/password-reset/confirm", async ({ request }) => {
    await delay(250);
    const body = (await request.json()) as { token: string };
    if (!body.token.startsWith("mock-ticket-")) {
      return problem(400, "OTP_TICKET_INVALID", "Yêu cầu đặt lại mật khẩu đã hết hạn.");
    }
    return new HttpResponse(null, { status: 204 });
  }),

  http.post("/api/v1/auth/totp/verify", async ({ request }) => {
    await delay(250);
    const body = (await request.json()) as { code: string };
    if (body.code !== "123456") {
      return problem(401, "TOTP_INVALID", "Mã xác thực TOTP không đúng.", { attemptsLeft: 4 });
    }
    return HttpResponse.json({ mfaLevel: "TOTP" });
  }),

  http.post("/api/v1/auth/totp/recovery", async () => {
    await delay(250);
    return HttpResponse.json({ mfaLevel: "TOTP", enrollmentRequired: false, recoveryCodesRemaining: 9 });
  }),

  http.get("/api/v1/auth/sessions", async () => {
    await delay(150);
    return HttpResponse.json({ items: MOCK_SESSIONS });
  }),

  http.delete("/api/v1/auth/sessions/:id", () => new HttpResponse(null, { status: 204 })),
  http.post("/api/v1/auth/sessions/revoke-all", () => new HttpResponse(null, { status: 204 })),

  http.get("/api/v1/users/me", async () => {
    await delay(150);
    return HttpResponse.json({
      id: "mock-user",
      email: "mock.user@catcheck.vn",
      fullName: "Người dùng mẫu",
      phone: null,
      locale: "vi",
      timezone: "Asia/Ho_Chi_Minh",
      status: "ACTIVE",
      onboardingStatus: "COMPLETED",
      emailVerified: true,
      identities: ["LOCAL"],
      createdAt: new Date().toISOString(),
    });
  }),

  http.get("/api/v1/account/mfa/totp", async () => {
    await delay(150);
    return HttpResponse.json({ status: "NONE", activatedAt: null, recoveryCodesRemaining: null, lockedUntil: null });
  }),

  http.post("/api/v1/account/mfa/totp/init", async () => {
    await delay(200);
    return HttpResponse.json({
      secretBase32: "JBSWY3DPEHPK3PXP",
      otpauthUri:
        "otpauth://totp/CatCheck:mock.user@catcheck.vn?secret=JBSWY3DPEHPK3PXP&issuer=CatCheck&algorithm=SHA1&digits=6&period=30",
      expiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
      digits: 6,
      periodSeconds: 30,
      algorithm: "SHA1",
    });
  }),

  http.post("/api/v1/account/mfa/totp/confirm", async () => {
    await delay(200);
    return HttpResponse.json({
      recoveryCodes: Array.from({ length: 10 }, (_, i) => `MOCK-${String(i + 1).padStart(2, "0")}-CODE`),
      activatedAt: new Date().toISOString(),
      mfaLevel: "TOTP",
    });
  }),
];

export const worker = setupWorker(...handlers);

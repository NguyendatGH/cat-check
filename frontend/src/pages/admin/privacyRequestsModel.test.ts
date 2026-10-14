import { describe, expect, it } from "vitest";
import type { AdminDsarRequest, AdminOffsetPage } from "@/features/admin";
import { allowedActionsFor, countdownOf, currentPageOf, isOverdue } from "./privacyRequestsModel";

const NOW = Date.parse("2026-10-14T00:00:00Z");

function request(overrides: Partial<AdminDsarRequest> = {}): AdminDsarRequest {
  return {
    id: "d1",
    publicRef: "DSAR-1",
    userId: "u1",
    contactEmailMasked: "a***@example.com",
    requestType: "RECTIFY",
    channel: "EMAIL",
    status: "IN_PROGRESS",
    receivedAt: "2026-10-10T00:00:00Z",
    ackDueAt: "2026-10-12T00:00:00Z",
    ackSentAt: null,
    fulfilDueAt: "2026-10-20T00:00:00Z",
    extendedTo: null,
    extensionReason: null,
    thirdPartyInvolved: false,
    completedAt: null,
    rejectionReason: null,
    handledBy: null,
    createdAt: "2026-10-10T00:00:00Z",
    ...overrides,
  };
}

describe("currentPageOf", () => {
  it("đọc `page` khi backend không trả `number` (nguyên nhân Trang NaN)", () => {
    const data = { items: [], page: 2, size: 20, totalElements: 41, totalPages: 3, hasMore: false };
    expect(currentPageOf(data as unknown as AdminOffsetPage<unknown>, 0)).toBe(2);
  });

  it("ưu tiên `number` khi có, rơi về trang đang yêu cầu khi thiếu cả hai", () => {
    expect(currentPageOf({ items: [], number: 1, size: 20, totalElements: 0, totalPages: 0, hasMore: false }, 0)).toBe(
      1,
    );
    expect(currentPageOf({ items: [] } as unknown as AdminOffsetPage<unknown>, 4)).toBe(4);
  });
});

describe("countdownOf", () => {
  it("còn ngày / quá hạn ngày / giờ", () => {
    expect(countdownOf(request(), NOW)).toEqual({ kind: "daysLeft", count: 6 });
    expect(countdownOf(request({ fulfilDueAt: "2026-10-11T00:00:00Z" }), NOW)).toEqual({
      kind: "daysOverdue",
      count: 3,
    });
    expect(countdownOf(request({ fulfilDueAt: "2026-10-14T05:30:00Z" }), NOW)).toEqual({ kind: "hoursLeft", count: 5 });
    expect(countdownOf(request({ fulfilDueAt: "2026-10-13T22:00:00Z" }), NOW)).toEqual({
      kind: "hoursOverdue",
      count: 2,
    });
  });

  it("dùng hạn gia hạn và bỏ qua yêu cầu đã đóng", () => {
    const extended = request({ fulfilDueAt: "2026-10-11T00:00:00Z", extendedTo: "2026-10-17T00:00:00Z" });
    expect(countdownOf(extended, NOW)).toEqual({ kind: "daysLeft", count: 3 });
    expect(isOverdue(extended, NOW)).toBe(false);
    expect(countdownOf(request({ status: "COMPLETED", fulfilDueAt: "2026-10-01T00:00:00Z" }), NOW)).toEqual({
      kind: "none",
    });
  });
});

describe("allowedActionsFor", () => {
  it("Admin hỗ trợ chỉ có Xác nhận đã nhận / Nhận xử lý", () => {
    expect(allowedActionsFor(request(), false)).toEqual(["ACK", "ASSIGN"]);
  });

  it("ẩn Gia hạn khi đã gia hạn và Hoàn tất cho Xoá/Xuất dữ liệu", () => {
    expect(allowedActionsFor(request(), true)).toEqual(["ACK", "ASSIGN", "EXTEND", "COMPLETE", "REJECT"]);
    expect(allowedActionsFor(request({ extendedTo: "2026-10-25T00:00:00Z" }), true)).not.toContain("EXTEND");
    expect(allowedActionsFor(request({ requestType: "ERASE" }), true)).not.toContain("COMPLETE");
    expect(allowedActionsFor(request({ requestType: "ACCESS_EXPORT" }), true)).not.toContain("COMPLETE");
  });
});

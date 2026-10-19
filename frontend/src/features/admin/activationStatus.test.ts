import { describe, expect, it } from "vitest";
import { ACTIVATION_CODE_STATUSES, activationEffectiveStatus } from "./activationStatus";

const NOW = Date.parse("2026-10-14T08:00:00Z");

describe("activationEffectiveStatus", () => {
  it("dùng effectiveStatus backend trả nếu có", () => {
    expect(
      activationEffectiveStatus(
        { status: "ISSUED", validUntil: "2027-01-01T00:00:00Z", effectiveStatus: "EXPIRED" },
        NOW,
      ),
    ).toBe("EXPIRED");
  });

  it("suy ra EXPIRED khi ISSUED đã quá validUntil", () => {
    expect(activationEffectiveStatus({ status: "ISSUED", validUntil: "2026-10-14T07:59:59Z" }, NOW)).toBe("EXPIRED");
    expect(activationEffectiveStatus({ status: "ISSUED", validUntil: "2026-10-14T08:00:00Z" }, NOW)).toBe("EXPIRED");
  });

  it("ISSUED còn hạn giữ ISSUED; REDEEMED/VOID không bao giờ thành EXPIRED", () => {
    expect(activationEffectiveStatus({ status: "ISSUED", validUntil: "2027-10-14T08:00:00Z" }, NOW)).toBe("ISSUED");
    expect(activationEffectiveStatus({ status: "REDEEMED", validUntil: "2020-01-01T00:00:00Z" }, NOW)).toBe("REDEEMED");
    expect(activationEffectiveStatus({ status: "VOID", validUntil: "2020-01-01T00:00:00Z" }, NOW)).toBe("VOID");
  });

  it("liệt kê đủ 4 trạng thái cho bộ lọc", () => {
    expect([...ACTIVATION_CODE_STATUSES]).toEqual(["ISSUED", "EXPIRED", "REDEEMED", "VOID"]);
  });
});

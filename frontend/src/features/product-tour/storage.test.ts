import { afterEach, describe, expect, it, vi } from "vitest";
import { readTourStatus, writeTourStatus } from "./storage";

afterEach(() => {
  window.localStorage.clear();
  vi.restoreAllMocks();
});

describe("tour storage", () => {
  it("tài khoản mới (chưa có khoá) => pending; lưu riêng theo user id", () => {
    expect(readTourStatus("u-1")).toBe("pending");
    writeTourStatus("u-1", "disabled");
    expect(readTourStatus("u-1")).toBe("disabled");
    expect(readTourStatus("u-2")).toBe("pending");
  });

  it("không có user => không hiện; giá trị lạ => pending", () => {
    expect(readTourStatus(null)).toBe("seen");
    window.localStorage.setItem("catcheck.productTour.v1:u-1", "garbage");
    expect(readTourStatus("u-1")).toBe("pending");
  });

  it("storage bị chặn => không ném lỗi, coi như đã xem", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(readTourStatus("u-1")).toBe("seen");
    expect(() => {
      writeTourStatus("u-1", "seen");
    }).not.toThrow();
  });
});

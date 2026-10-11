import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/shared/api/errors";
import {
  ariaSortOf,
  compareSortValues,
  matchesQuery,
  nextSort,
  normalizeSearchText,
  pruneSelection,
  runSequential,
  selectionState,
  sortRows,
  summarizeBulk,
  toggleAllVisible,
  toggleSelection,
} from "./tableTools";

describe("sắp xếp", () => {
  it("giá trị rỗng luôn nằm cuối, dù tăng hay giảm", () => {
    expect(compareSortValues(null, 1, "asc")).toBeGreaterThan(0);
    expect(compareSortValues(null, 1, "desc")).toBeGreaterThan(0);
    expect(compareSortValues("", "a", "asc")).toBeGreaterThan(0);
    expect(compareSortValues(undefined, null, "asc")).toBe(0);
  });

  it("so số theo giá trị, chuỗi theo locale vi có số tự nhiên", () => {
    expect(compareSortValues(2, 10, "asc")).toBeLessThan(0);
    expect(compareSortValues("1.10.0", "1.9.0", "asc")).toBeGreaterThan(0);
    expect(compareSortValues("b", "a", "desc")).toBeLessThan(0);
  });

  it("sortRows ổn định và không đổi mảng gốc", () => {
    const rows = [
      { id: "a", v: 2 },
      { id: "b", v: null },
      { id: "c", v: 1 },
      { id: "d", v: 2 },
    ];
    const copy = [...rows];
    const sorted = sortRows(rows, { key: "v", direction: "asc" }, (row) => row.v);
    expect(sorted.map((row) => row.id)).toEqual(["c", "a", "d", "b"]);
    expect(sortRows(rows, { key: "v", direction: "desc" }, (row) => row.v).map((row) => row.id)).toEqual([
      "a",
      "d",
      "c",
      "b",
    ]);
    expect(rows).toEqual(copy);
    expect(sortRows(rows, null, (row) => row.v)).toEqual(rows);
  });

  it("nextSort: cột mới tăng dần, bấm lại đảo chiều; aria-sort theo đó", () => {
    const first = nextSort(null, "name");
    expect(first).toEqual({ key: "name", direction: "asc" });
    const second = nextSort(first, "name");
    expect(second).toEqual({ key: "name", direction: "desc" });
    expect(nextSort(second, "date")).toEqual({ key: "date", direction: "asc" });
    expect(ariaSortOf(second, "name")).toBe("descending");
    expect(ariaSortOf(first, "name")).toBe("ascending");
    expect(ariaSortOf(first, "date")).toBe("none");
  });
});

describe("tìm kiếm", () => {
  it("bỏ dấu tiếng Việt và chữ đ", () => {
    expect(normalizeSearchText("  Đồng Ý  ")).toBe("dong y");
  });

  it("mọi từ phải có mặt ở một trong các trường; query rỗng khớp tất cả", () => {
    expect(matchesQuery(["Rút lại", "MARKETING_EMAIL"], "rut marketing")).toBe(true);
    expect(matchesQuery(["Rút lại", null, undefined, 42], "42")).toBe(true);
    expect(matchesQuery(["Rút lại"], "rut khac")).toBe(false);
    expect(matchesQuery([], "   ")).toBe(true);
  });
});

describe("chọn nhiều", () => {
  it("toggleSelection bật/tắt một dòng, không đổi tập gốc", () => {
    const start = new Set(["a"]);
    expect([...toggleSelection(start, "b")].sort()).toEqual(["a", "b"]);
    expect([...toggleSelection(start, "a")]).toEqual([]);
    expect([...start]).toEqual(["a"]);
  });

  it("toggleAllVisible chọn hết dòng đang hiện, bấm lại thì bỏ — giữ dòng ẩn đã chọn", () => {
    const all = toggleAllVisible(new Set(["hidden"]), ["a", "b"]);
    expect([...all].sort()).toEqual(["a", "b", "hidden"]);
    expect([...toggleAllVisible(all, ["a", "b"])]).toEqual(["hidden"]);
  });

  it("selectionState trả false / mixed / true", () => {
    expect(selectionState(new Set(), ["a", "b"])).toBe(false);
    expect(selectionState(new Set(["a"]), ["a", "b"])).toBe("mixed");
    expect(selectionState(new Set(["a", "b"]), ["a", "b"])).toBe(true);
    expect(selectionState(new Set(["a"]), [])).toBe(false);
  });

  it("pruneSelection bỏ id không còn hiện", () => {
    expect([...pruneSelection(new Set(["a", "gone"]), ["a", "b"])]).toEqual(["a"]);
  });
});

describe("runSequential", () => {
  it("chạy lần lượt, lỗi một việc không dừng việc khác, gom kết quả theo loại lỗi", async () => {
    const order: string[] = [];
    const progress = vi.fn();
    const outcome = await runSequential(
      ["r1", "r2", "r3", "r4"],
      (id) => id,
      async (id) => {
        order.push(id);
        await Promise.resolve();
        if (id === "r2") throw new ApiError("modified", 412, "RESOURCE_MODIFIED");
        if (id === "r3") throw new TypeError("Failed to fetch");
      },
      progress,
    );
    expect(order).toEqual(["r1", "r2", "r3", "r4"]);
    expect(outcome.succeeded).toEqual(["r1", "r4"]);
    expect(outcome.failed).toEqual([
      { id: "r2", kind: "stale", message: "modified" },
      { id: "r3", kind: "network", message: "Failed to fetch" },
    ]);
    expect(progress).toHaveBeenLastCalledWith(4, 4);
    expect(summarizeBulk(outcome)).toEqual({ ok: 2, failed: 2, stale: 1 });
  });

  it("danh sách rỗng thì không gọi worker", async () => {
    const worker = vi.fn();
    expect(await runSequential([], String, worker)).toEqual({ succeeded: [], failed: [] });
    expect(worker).not.toHaveBeenCalled();
  });
});

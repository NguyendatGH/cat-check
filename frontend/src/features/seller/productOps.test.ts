import { describe, expect, it, vi } from "vitest";
import {
  LOW_STOCK_THRESHOLD,
  countProducts,
  filterProducts,
  lowStockProducts,
  planBulkStatus,
  runBulk,
  sortProducts,
  type SellerProductLike,
} from "./productOps";

function p(id: string, status: SellerProductLike["status"], stock: number, extra: Partial<SellerProductLike> = {}) {
  return {
    id,
    sku: `SKU-${id}`,
    name: `Sản phẩm ${id}`,
    stockQuantity: stock,
    status,
    updatedAt: "2026-01-01T00:00:00Z",
    priceVnd: 100,
    ...extra,
  };
}

const data = [
  p("a", "PUBLISHED", 10),
  p("b", "PUBLISHED", 5),
  p("c", "PUBLISHED", 0),
  p("d", "DRAFT", 1),
  p("e", "ARCHIVED", 2),
];

describe("countProducts", () => {
  it("đếm theo trạng thái và sắp hết hàng", () => {
    expect(countProducts(data)).toEqual({ published: 3, draft: 1, archived: 1, lowStock: 2, total: 5 });
  });
  it("danh sách rỗng", () => {
    expect(countProducts([])).toEqual({ published: 0, draft: 0, archived: 0, lowStock: 0, total: 0 });
  });
});

describe("lowStockProducts", () => {
  it("ngưỡng bao gồm đúng bằng, chỉ PUBLISHED, tăng dần theo tồn", () => {
    expect(LOW_STOCK_THRESHOLD).toBe(5);
    expect(lowStockProducts(data).map((x) => x.id)).toEqual(["c", "b"]);
  });
});

describe("filter/sort", () => {
  it("lọc theo tab và từ khoá tên hoặc SKU", () => {
    expect(filterProducts(data, "DRAFT", "").map((x) => x.id)).toEqual(["d"]);
    expect(filterProducts(data, "ALL", "sku-c").map((x) => x.id)).toEqual(["c"]);
    expect(filterProducts(data, "PUBLISHED", "sản phẩm a").map((x) => x.id)).toEqual(["a"]);
  });
  it("sắp xếp theo tồn kho giảm dần", () => {
    expect(sortProducts(data, "stockQuantity", "desc").map((x) => x.id)).toEqual(["a", "b", "e", "d", "c"]);
  });
});

describe("planBulkStatus", () => {
  it("giữ thứ tự chọn, bỏ qua đã đúng trạng thái, trùng và id lạ", () => {
    expect(planBulkStatus(data, ["d", "a", "d", "zzz", "e"], "PUBLISHED")).toEqual([
      { id: "d", status: "PUBLISHED" },
      { id: "e", status: "PUBLISHED" },
    ]);
  });
});

describe("runBulk", () => {
  const steps = [
    { id: "1", status: "PUBLISHED" as const },
    { id: "2", status: "PUBLISHED" as const },
    { id: "3", status: "PUBLISHED" as const },
  ];
  it("chạy tuần tự đúng thứ tự và báo tiến độ", async () => {
    const order: string[] = [];
    const progress = vi.fn();
    const r = await runBulk(
      steps,
      (s) => {
        order.push(s.id);
        return Promise.resolve();
      },
      progress,
    );
    expect(order).toEqual(["1", "2", "3"]);
    expect(r).toEqual({ done: 3, total: 3, failed: null });
    expect(progress).toHaveBeenLastCalledWith(3, 3);
  });
  it("dừng ở lệnh lỗi đầu tiên", async () => {
    const run = vi.fn((s: { id: string }) => (s.id === "2" ? Promise.reject(new Error("x")) : Promise.resolve()));
    const r = await runBulk(steps, run);
    expect(run).toHaveBeenCalledTimes(2);
    expect(r.done).toBe(1);
    expect(r.failed?.step.id).toBe("2");
  });
});

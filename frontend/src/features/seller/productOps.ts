
export type SellerProductStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface SellerProductLike {
  id: string;
  sku: string;
  name: string;
  stockQuantity: number;
  status: SellerProductStatus;
  updatedAt: string;
}

export const LOW_STOCK_THRESHOLD = 5;

export interface ProductCounts {
  published: number;
  draft: number;
  archived: number;
  lowStock: number;
  total: number;
}

export function lowStockProducts<T extends SellerProductLike>(
  products: readonly T[],
  threshold: number = LOW_STOCK_THRESHOLD,
): T[] {
  return products
    .filter((p) => p.status === "PUBLISHED" && p.stockQuantity <= threshold)
    .sort((a, b) => a.stockQuantity - b.stockQuantity || a.name.localeCompare(b.name, "vi"));
}

export function countProducts(
  products: readonly SellerProductLike[],
  threshold: number = LOW_STOCK_THRESHOLD,
): ProductCounts {
  const counts: ProductCounts = {
    published: 0,
    draft: 0,
    archived: 0,
    lowStock: lowStockProducts(products, threshold).length,
    total: products.length,
  };
  for (const p of products) {
    if (p.status === "PUBLISHED") counts.published += 1;
    else if (p.status === "DRAFT") counts.draft += 1;
    else counts.archived += 1;
  }
  return counts;
}

export type StatusFilter = "ALL" | SellerProductStatus;
export type SortKey = "name" | "sku" | "priceVnd" | "stockQuantity" | "updatedAt";
export type SortDir = "asc" | "desc";

export function filterProducts<T extends SellerProductLike>(
  products: readonly T[],
  status: StatusFilter,
  query: string,
): T[] {
  const q = query.trim().toLowerCase();
  return products.filter(
    (p) =>
      (status === "ALL" || p.status === status) &&
      (q === "" || p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q)),
  );
}

export function sortProducts<T extends SellerProductLike & { priceVnd: number }>(
  products: readonly T[],
  key: SortKey,
  dir: SortDir,
): T[] {
  const sign = dir === "asc" ? 1 : -1;
  return [...products].sort((a, b) => {
    switch (key) {
      case "name":
        return sign * a.name.localeCompare(b.name, "vi");
      case "sku":
        return sign * a.sku.localeCompare(b.sku);
      case "priceVnd":
        return sign * (a.priceVnd - b.priceVnd);
      case "stockQuantity":
        return sign * (a.stockQuantity - b.stockQuantity);
      case "updatedAt":
        return sign * (Date.parse(a.updatedAt) - Date.parse(b.updatedAt));
    }
  });
}

export interface BulkStep {
  id: string;
  status: SellerProductStatus;
}

export function planBulkStatus(
  products: readonly SellerProductLike[],
  selectedIds: readonly string[],
  target: SellerProductStatus,
): BulkStep[] {
  const byId = new Map(products.map((p) => [p.id, p]));
  const seen = new Set<string>();
  const steps: BulkStep[] = [];
  for (const id of selectedIds) {
    const p = byId.get(id);
    if (!p || seen.has(id) || p.status === target) continue;
    seen.add(id);
    steps.push({ id, status: target });
  }
  return steps;
}

export interface BulkResult {
  done: number;
  total: number;
  failed: { step: BulkStep; error: unknown } | null;
}

export async function runBulk(
  steps: readonly BulkStep[],
  run: (step: BulkStep) => Promise<unknown>,
  onProgress?: (done: number, total: number) => void,
): Promise<BulkResult> {
  let done = 0;
  onProgress?.(0, steps.length);
  for (const step of steps) {
    try {
      await run(step);
    } catch (error) {
      return { done, total: steps.length, failed: { step, error } };
    }
    done += 1;
    onProgress?.(done, steps.length);
  }
  return { done, total: steps.length, failed: null };
}

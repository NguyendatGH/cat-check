import { describe, expect, it } from "vitest";
import { normalizeOffsetPage } from "./api";

describe("normalizeOffsetPage", () => {
  it("maps a `page`-style envelope to AdminOffsetPage fields", () => {
    expect(normalizeOffsetPage({ items: [1, 2], page: 1, size: 2, totalElements: 5 })).toMatchObject({
      number: 1,
      totalPages: 3,
      hasMore: true,
    });
  });

  it("keeps a complete envelope as is", () => {
    const data = { items: [], number: 0, size: 20, totalElements: 0, totalPages: 1, hasMore: false };
    expect(normalizeOffsetPage(data)).toEqual(data);
  });

  it("ignores non-page payloads", () => {
    expect(normalizeOffsetPage({ id: "x" })).toEqual({ id: "x" });
    expect(normalizeOffsetPage(null)).toBeNull();
  });
});

import { describe, expect, it } from "vitest";
import { formatPh } from "./formatPh";

describe("formatPh", () => {
  it("formats pH for Vietnamese readers with two decimals", () => {
    expect(formatPh(6.45)).toBe("6,45");
    expect(formatPh(7)).toBe("7,00");
  });

  it.each([null, undefined, Number.NaN])("shows an em dash for %s", (value) => {
    expect(formatPh(value)).toBe("—");
  });
});

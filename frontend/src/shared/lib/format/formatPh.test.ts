import i18next from "i18next";
import { afterEach, describe, expect, it } from "vitest";
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

describe("formatPh in English", () => {
  afterEach(async () => {
    await i18next.changeLanguage("vi");
  });

  it("uses a decimal point", async () => {
    await i18next.init({ lng: "en", resources: {}, supportedLngs: ["vi", "en"] });
    expect(formatPh(6.45)).toBe("6.45");
    expect(formatPh(7)).toBe("7.00");
  });
});

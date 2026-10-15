import i18next from "i18next";
import { afterEach, describe, expect, it } from "vitest";
import { formatNumber } from "./formatNumber";
import { intlLocale, localIsoDate } from "./locale";

describe("intlLocale", () => {
  afterEach(async () => {
    await i18next.changeLanguage("vi");
  });

  it("follows the active i18n language", async () => {
    await i18next.init({ lng: "vi", resources: {}, supportedLngs: ["vi", "en"] });
    expect(intlLocale()).toBe("vi-VN");
    expect(formatNumber(1234.5)).toBe("1.234,5");
    await i18next.changeLanguage("en");
    expect(intlLocale()).toBe("en-US");
    expect(formatNumber(1234.5)).toBe("1,234.5");
  });
});

describe("localIsoDate", () => {
  it("uses the local calendar date, not the UTC one", () => {
    const lateEvening = new Date(2026, 0, 31, 23, 30);
    expect(localIsoDate(lateEvening)).toBe("2026-01-31");
    const earlyMorning = new Date(2026, 9, 15, 0, 5);
    expect(localIsoDate(earlyMorning)).toBe("2026-10-15");
  });

  it("shifts by whole local days across month boundaries", () => {
    expect(localIsoDate(new Date(2026, 2, 1, 6), -29)).toBe("2026-01-31");
  });
});

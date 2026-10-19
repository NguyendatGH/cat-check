import i18next from "i18next";
import { afterEach, describe, expect, it, vi } from "vitest";
import { emptyDraft, formatPhShort, hexToLab, patchDraft } from "./phChartModel";

// Barrel @/features/admin kéo theo i18n của app (HttpBackend): instance toàn cục khi đó đang tải
// locale qua mạng (thất bại + thử lại trong jsdom) và ghi đè ngôn ngữ test vừa đổi.
vi.mock("@/features/admin", () => ({ isHexColor: (value: string) => /^#[0-9A-Fa-f]{6}$/.test(value) }));

describe("formatPhShort", () => {
  afterEach(async () => {
    await i18next.changeLanguage("vi");
  });

  it("đổi dấu thập phân theo ngôn ngữ", async () => {
    expect(formatPhShort(6.3)).toBe("6,3");
    await i18next.init({ lng: "en", resources: {}, supportedLngs: ["vi", "en"] });
    expect(formatPhShort(6.3)).toBe("6.3");
  });
});

describe("hexToLab", () => {
  // Giá trị Lab của bảng placeholder V9, do backend ColorSpace.hexToLab tính.
  it.each([
    ["#E8C832", 81.124, -3.229, 72.989],
    ["#4CAF50", 63.978, -48.542, 39.735],
    ["#1F3FA8", 31.051, 28.499, -59.481],
  ])("%s khớp backend", (hex, l, a, b) => {
    const lab = hexToLab(hex);
    expect(lab.l).toBeCloseTo(l, 2);
    expect(lab.a).toBeCloseTo(a, 2);
    expect(lab.b).toBeCloseTo(b, 2);
  });

  it("trắng là L=100, a=b=0", () => {
    const lab = hexToLab("#FFFFFF");
    expect(lab.l).toBeCloseTo(100, 6);
    expect(lab.a).toBeCloseTo(0, 6);
    expect(lab.b).toBeCloseTo(0, 6);
  });
});

describe("patchDraft", () => {
  it("điểm mới: nhập Hex hiển thị thì Lab tự điền", () => {
    const draft = patchDraft(emptyDraft(), "displayHex", "#E8C832");
    expect([draft.labL, draft.labA, draft.labB]).toEqual(["81.124", "-3.229", "72.989"]);
    expect(draft.labAuto).toBe(true);
  });

  it("Hex sRGB hợp lệ được ưu tiên hơn Hex hiển thị", () => {
    let draft = patchDraft(emptyDraft(), "displayHex", "#E8C832");
    draft = patchDraft(draft, "hexSrgb", "#1F3FA8");
    expect(draft.labL).toBe("31.051");
  });

  it("gõ dở hex không làm hỏng lần tính lại kế tiếp", () => {
    let draft = patchDraft(emptyDraft(), "displayHex", "#E8C832");
    for (const partial of ["#", "#4", "#4CAF5", "#4CAF50"]) draft = patchDraft(draft, "displayHex", partial);
    expect(draft.labL).toBe("63.978");
  });

  it("không ghi đè Lab nhập tay hoặc Lab đã lưu", () => {
    let typed = patchDraft(emptyDraft(), "displayHex", "#E8C832");
    typed = patchDraft(typed, "labL", "80");
    expect(patchDraft(typed, "displayHex", "#1F3FA8").labL).toBe("80");

    const saved = { ...emptyDraft(), labL: "70", labA: "1", labB: "2", labAuto: false };
    expect(patchDraft(saved, "displayHex", "#1F3FA8").labL).toBe("70");
  });
});

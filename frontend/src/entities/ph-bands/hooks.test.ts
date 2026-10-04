import { describe, expect, it } from "vitest";
import type { PhBand } from "./model";
import { findBandForPh } from "./hooks";

const bands: PhBand[] = [
  {
    code: "LOW",
    phMin: null,
    phMax: 6.3,
    minInclusive: false,
    maxInclusive: false,
    severity: "WATCH",
    label: "Thấp",
    description: "",
    colorToken: "color-ph-abnormal",
    iconName: "alert-triangle",
    sortOrder: 1,
    triggersAlert: true,
  },
  {
    code: "IN_RANGE",
    phMin: 6.3,
    phMax: 6.6,
    minInclusive: true,
    maxInclusive: true,
    severity: "NORMAL",
    label: "Trong khoảng",
    description: "",
    colorToken: "color-ph-normal",
    iconName: "check-circle",
    sortOrder: 2,
    triggersAlert: false,
  },
  {
    code: "HIGH",
    phMin: 6.6,
    phMax: null,
    minInclusive: false,
    maxInclusive: false,
    severity: "WATCH",
    label: "Cao",
    description: "",
    colorToken: "color-ph-abnormal",
    iconName: "alert-triangle",
    sortOrder: 3,
    triggersAlert: true,
  },
];

describe("findBandForPh", () => {
  it.each([
    [6.29, "LOW"],
    [6.3, "IN_RANGE"],
    [6.6, "IN_RANGE"],
    [6.61, "HIGH"],
  ])("maps %s to %s using API boundary flags", (ph, code) => {
    expect(findBandForPh(bands, ph)?.code).toBe(code);
  });

  it.each([null, undefined, Number.NaN])("does not classify %s", (ph) => {
    expect(findBandForPh(bands, ph)).toBeUndefined();
  });
});

/**
 * Fixture dựng ĐÚNG CÁCH SERVER SERIALIZE: `default-property-inclusion: non_null` nên dải mở
 * KHÔNG có key `phMin`/`phMax` — khác hẳn fixture ở trên vốn ghi `phMin: null` tường minh.
 *
 * Chính sự khác biệt đó đã để lọt một bug thật: `findBandForPh` so `=== null`, nên với dữ liệu
 * thật thì `LOW`/`HIGH` không bao giờ khớp và màn xu hướng in ra enum thô `SLIGHTLY_HIGH` thay
 * cho nhãn đã dịch. Bộ test cũ vẫn xanh vì fixture của nó "lịch sự" hơn server.
 */
const wireBands = [
  // LOW: chỉ có phMax (vắng phMin)
  {
    code: "LOW",
    phMax: 6.3,
    minInclusive: false,
    maxInclusive: false,
    severity: "WATCH",
    label: "Thấp",
    description: "",
    colorToken: "color-ph-abnormal",
    iconName: "alert-triangle",
    sortOrder: 1,
    triggersAlert: true,
  },
  {
    code: "IN_RANGE",
    phMin: 6.3,
    phMax: 6.6,
    minInclusive: true,
    maxInclusive: true,
    severity: "NORMAL",
    label: "Trong khoảng",
    description: "",
    colorToken: "color-ph-normal",
    iconName: "check-circle",
    sortOrder: 2,
    triggersAlert: false,
  },
  // HIGH: chỉ có phMin (vắng phMax)
  {
    code: "HIGH",
    phMin: 6.6,
    minInclusive: false,
    maxInclusive: false,
    severity: "WATCH",
    label: "Cao",
    description: "",
    colorToken: "color-ph-abnormal",
    iconName: "alert-triangle",
    sortOrder: 3,
    triggersAlert: true,
  },
  // INCONCLUSIVE: KHÔNG có cả hai — là một trạng thái, không phải một khoảng.
  {
    code: "INCONCLUSIVE",
    minInclusive: false,
    maxInclusive: false,
    severity: "NEUTRAL",
    label: "Không kết luận",
    description: "",
    colorToken: "color-ph-unknown",
    iconName: "help-circle",
    sortOrder: 4,
    triggersAlert: false,
  },
] as unknown as PhBand[];

describe("findBandForPh với JSON đúng như server gửi (key bị bỏ, không phải null)", () => {
  it.each([
    [5.0, "LOW"],
    [6.29, "LOW"],
    [6.3, "IN_RANGE"],
    [6.6, "IN_RANGE"],
    [6.61, "HIGH"],
    [9.0, "HIGH"],
  ])("tra %s ra %s dù dải biên vắng key", (ph, code) => {
    expect(findBandForPh(wireBands, ph)?.code).toBe(code);
  });

  it("KHÔNG bao giờ trả INCONCLUSIVE: vắng cả hai biên nghĩa là không phải khoảng", () => {
    for (const ph of [0, 5, 6.45, 7, 14]) {
      expect(findBandForPh(wireBands, ph)?.code).not.toBe("INCONCLUSIVE");
    }
  });
});

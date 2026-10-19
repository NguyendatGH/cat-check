import i18next from "i18next";
import { describe, expect, it } from "vitest";
import { resolveFlagExplanation } from "./components";

async function makeI18n(lng: string) {
  const instance = i18next.createInstance();
  await instance.init({
    lng,
    fallbackLng: "vi",
    supportedLngs: ["vi", "en"],
    resources: {
      en: {
        common: {
          healthFlags: {
            message: { rule: { LOW_QUALITY_STREAK: { message: "{{n}} recent scans had low confidence." } } },
          },
        },
      },
      vi: { common: { healthFlags: {} } },
    },
  });
  return instance;
}

const flag = {
  messageKey: "rule.LOW_QUALITY_STREAK.message",
  messageParams: { n: 2 },
  explanationVi: "2 lần quét gần đây có độ tin cậy thấp.",
};

describe("resolveFlagExplanation", () => {
  it("renders messageKey with messageParams when the active language has it", async () => {
    const i18n = await makeI18n("en");
    expect(resolveFlagExplanation(flag, i18n.getFixedT(null, "common"), i18n)).toBe(
      "2 recent scans had low confidence.",
    );
  });

  it("falls back to the Vietnamese snapshot when the key is missing", async () => {
    const i18n = await makeI18n("vi");
    expect(resolveFlagExplanation(flag, i18n.getFixedT(null, "common"), i18n)).toBe(flag.explanationVi);
    const en = await makeI18n("en");
    expect(
      resolveFlagExplanation({ ...flag, messageKey: "rule.UNKNOWN.message" }, en.getFixedT(null, "common"), en),
    ).toBe(flag.explanationVi);
  });
});

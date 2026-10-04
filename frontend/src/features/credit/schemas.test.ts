import { describe, expect, it } from "vitest";
import { formatActivationCode, isValidActivationCode, normalizeActivationCode } from "./schemas";

describe("activation code helpers", () => {
  it("normalizes and formats a valid activation code", () => {
    const raw = "cc plus 7k3m9qx2rt";

    expect(normalizeActivationCode(raw)).toBe("CCPLUS7K3M9QX2RT");
    expect(formatActivationCode(raw)).toBe("CC-PLUS-7K3M9QX2RT");
    expect(isValidActivationCode(raw)).toBe(true);
  });

  it.each(["CC-7K3M9QX2R", "CC-PLUS-7K3M9QX2RI", "not-a-code"])("rejects malformed code %s", (code) => {
    expect(isValidActivationCode(code)).toBe(false);
  });
});

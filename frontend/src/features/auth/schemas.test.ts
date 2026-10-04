import { describe, expect, it } from "vitest";
import { estimatePasswordStrength, otpCodeSchema, registerSchema, resetPasswordSchema } from "./schemas";

describe("authentication schemas", () => {
  it("accepts a valid registration and trims identity fields", () => {
    const result = registerSchema.safeParse({
      fullName: "  Nguyễn An  ",
      email: "an@example.com",
      password: "CatCheck9!",
      referralCodeRaw: "",
    });

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.fullName).toBe("Nguyễn An");
      expect(result.data.email).toBe("an@example.com");
    }
  });

  it.each(["12345", "1234567", "abcdef"])("rejects invalid OTP %s", (code) => {
    expect(otpCodeSchema.safeParse(code).success).toBe(false);
  });

  it("rejects mismatched reset passwords", () => {
    expect(resetPasswordSchema.safeParse({ newPassword: "CatCheck9!", confirmPassword: "different" }).success).toBe(
      false,
    );
  });

  it.each([
    ["", "empty"],
    ["password", "weak"],
    ["CatCheck2026", "strong"],
    ["CatCheck2026!", "strong"],
  ] as const)("rates %s as %s", (password, strength) => {
    expect(estimatePasswordStrength(password)).toBe(strength);
  });
});

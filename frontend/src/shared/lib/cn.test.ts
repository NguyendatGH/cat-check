import { describe, expect, it } from "vitest";
import { cn } from "./cn";

describe("cn", () => {
  it("giữ cỡ chữ token dự án khi ghép với màu chữ", () => {
    expect(cn("text-caption", "text-text-secondary")).toBe("text-caption text-text-secondary");
  });
  it("cỡ chữ sau ghi đè cỡ chữ trước", () => {
    expect(cn("text-body", "text-small")).toBe("text-small");
  });
});

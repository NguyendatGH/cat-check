import { z } from "zod";

/**
 * Zod schema cho wizard export. Message là KEY i18n (namespace `export`) — dịch lúc render,
 * cùng quy ước `features/onboarding/schemas.ts`. Ràng buộc khớp `ExportRequestService`
 * (BE): `from <= to`, `to <= hôm nay`, ít nhất 1 mục nội dung.
 */

function isValidDateString(value: string): boolean {
  return !Number.isNaN(new Date(`${value}T00:00:00`).getTime());
}

export const customRangeSchema = z
  .object({
    from: z.string().refine(isValidDateString, "range.custom.invalid"),
    to: z.string().refine(isValidDateString, "range.custom.invalid"),
  })
  .refine((v) => new Date(`${v.from}T00:00:00`) <= new Date(`${v.to}T00:00:00`), {
    message: "range.custom.fromAfterTo",
    path: ["from"],
  })
  .refine((v) => new Date(`${v.to}T00:00:00`).getTime() <= Date.now(), {
    message: "range.custom.toInFuture",
    path: ["to"],
  });

export const sectionsSchema = z.array(z.string()).min(1, "sections.required");

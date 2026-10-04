import { z } from "zod";

/**
 * Zod schemas cho form onboarding. Message truyền vào là KEY i18n (namespace
 * `onboarding`) — dịch lúc render qua `t(message)`. Lý do: `zodErrorMap` toàn cục
 * (shared/i18n/zodErrorMap.ts) chỉ có 4 message generic, nên field cần message riêng
 * phải tự mang key i18n (zod v4 giữ per-schema message khi có customError toàn cục —
 * message đã dịch sẵn thì `t()` trả về nguyên văn, nên dịch lúc render an toàn).
 */

export const CAT_NAME_MAX = 50;
export const WEIGHT_MIN_KG = 0.5;
export const WEIGHT_MAX_KG = 15;

function isValidDateString(value: string): boolean {
  return !Number.isNaN(new Date(`${value}T00:00:00`).getTime());
}

export const catProfileSchema = z.object({
  name: z.string().trim().min(1, "cat.name.required").max(CAT_NAME_MAX, "cat.name.tooLong"),
  breedCode: z.string().min(1, "cat.breed.required"),
  // string (không phải z.enum của CatSex) có chủ đích: `OptionCard` dùng chung cho cả field
  // này lẫn các field lựa chọn dạng text khác trong luồng onboarding, `onChange` của nó nhận
  // `string`. `values.sex` được ép kiểu `CatSex` an toàn ở nơi dùng (page component) — bất
  // biến đã được `.min(1, ...)` đảm bảo non-empty lúc submit.
  sex: z.string().min(1, "cat.sex.required"),
  birthDate: z
    .string()
    .refine((value) => !value || isValidDateString(value), "cat.birthDate.invalid")
    .refine((value) => !value || new Date(`${value}T00:00:00`).getTime() <= Date.now(), "cat.birthDate.future"),
  weightKg: z.string().refine((value) => {
    if (!value) return true;
    const n = Number(value);
    return !Number.isNaN(n) && n >= WEIGHT_MIN_KG && n <= WEIGHT_MAX_KG;
  }, "cat.weight.invalid"),
});

export type CatProfileFormValues = z.infer<typeof catProfileSchema>;

export const surveySchema = z.object({
  litterType: z.string().min(1, "survey.questions.litterType.required"),
  urinaryHistory: z.string().min(1, "survey.questions.urinaryHistory.required"),
  dietType: z.string().min(1, "survey.questions.dietType.required"),
  urinationFrequency: z.string().min(1, "survey.questions.urinationFrequency.required"),
  symptoms: z.array(z.string()).min(1, "survey.questions.symptoms.required"),
});

export type SurveyFormValues = z.infer<typeof surveySchema>;

/**
 * Mã kích hoạt: `CC-<PKG>-<10 ký tự Crockford Base32>` (p5 §5.9).
 * Crockford Base32 loại bỏ I, L, O, U (dễ nhầm với 1, 1, 0). Input tự bỏ dấu
 * cách/gạch ngang nên chuỗi canonical là dạng không dạch: `CCPLUS7K3M9QX2RT`.
 * Parse: 10 ký tự cuối phải là Crockford hợp lệ, phần giữa là mã gói.
 */
const CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
const ACTIVATION_CODE_REGEX = new RegExp(`^CC[A-Z0-9]+[${CROCKFORD_ALPHABET}]{10}$`);

export function normalizeActivationCode(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9]/g, "");
}

export function isValidActivationCode(raw: string): boolean {
  return ACTIVATION_CODE_REGEX.test(normalizeActivationCode(raw));
}

/** Hiển thị có dạch cho dễ đọc: CC-PLUS-7K3M9QX2RT. */
export function formatActivationCode(raw: string): string {
  const clean = normalizeActivationCode(raw);
  if (clean.length < 12) return clean;
  const tail = clean.slice(-10);
  const pkg = clean.slice(2, -10);
  return pkg ? `CC-${pkg}-${tail}` : `CC-${tail}`;
}

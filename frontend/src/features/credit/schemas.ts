/**
 * Validate định dạng mã kích hoạt (p5 §5.9): `CC-<PKG>-<10 ký tự Crockford Base32>`,
 * ví dụ `CC-PLUS-7K3M9QX2RT`. Bản sao cục bộ của cùng logic ở
 * `features/onboarding/schemas.ts` — không import chéo feature được (boundaries), và màn
 * `/credits/activate` (C05, p9 §9.4.6) là một trang độc lập, không phải bước của onboarding.
 * Regex chỉ kiểm ĐỘ DÀI/KÝ TỰ — checksum ký tự cuối kiểm ở server (§5.9).
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


const CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
const ACTIVATION_CODE_REGEX = new RegExp(`^CC[A-Z0-9_]+[${CROCKFORD_ALPHABET}]{10}$`);

export function normalizeActivationCode(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9_]/g, "");
}

export function isValidActivationCode(raw: string): boolean {
  return ACTIVATION_CODE_REGEX.test(normalizeActivationCode(raw));
}

export function formatActivationCode(raw: string): string {
  const clean = normalizeActivationCode(raw);
  if (clean.length < 12) return clean;
  const tail = clean.slice(-10);
  const pkg = clean.slice(2, -10);
  return pkg ? `CC-${pkg}-${tail}` : `CC-${tail}`;
}

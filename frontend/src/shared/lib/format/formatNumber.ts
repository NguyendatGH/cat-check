/** Format số theo locale vi-VN. */
export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat("vi-VN", options).format(value);
}

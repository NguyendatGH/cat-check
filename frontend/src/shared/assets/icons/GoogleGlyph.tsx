/**
 * Glyph "G" 4 màu chính thức của Google — dùng hex cố định vì đây là brand mark bên thứ ba,
 * KHÔNG phải màu trong hệ design token của CatCheck (không đi qua `@theme`, không được phép
 * đổi theo theme sáng/tối). ESLint bỏ qua rule `no-restricted-syntax` cấm hex cho riêng file
 * này (xem `eslint.config.js`) vì lý do tương tự.
 */
export function GoogleGlyph({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true">
      <path
        fill="#4285F4"
        d="M23.52 12.273c0-.851-.076-1.67-.218-2.455H12v4.645h6.458a5.52 5.52 0 0 1-2.395 3.622v3.011h3.878c2.269-2.09 3.578-5.167 3.578-8.823Z"
      />
      <path
        fill="#34A853"
        d="M12 24c3.24 0 5.956-1.075 7.941-2.905l-3.878-3.01c-1.075.72-2.45 1.145-4.063 1.145-3.127 0-5.773-2.112-6.72-4.948H1.27v3.11A11.998 11.998 0 0 0 12 24Z"
      />
      <path
        fill="#FBBC05"
        d="M5.28 14.282A7.193 7.193 0 0 1 4.909 12c0-.792.136-1.562.371-2.282v-3.11H1.27A11.998 11.998 0 0 0 0 12c0 1.936.463 3.768 1.27 5.392l4.01-3.11Z"
      />
      <path
        fill="#EA4335"
        d="M12 4.77c1.762 0 3.344.606 4.59 1.795l3.442-3.442C17.951 1.19 15.235 0 12 0 7.31 0 3.256 2.69 1.27 6.608l4.01 3.11C6.227 6.882 8.873 4.77 12 4.77Z"
      />
    </svg>
  );
}

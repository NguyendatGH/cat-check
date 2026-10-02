/**
 * Brand mark ví MoMo — dùng ở danh sách phương thức thanh toán (`/cart`, `/checkout`).
 *
 * Màu `#A50064` là màu thương hiệu chính thức của bên thứ ba, KHÔNG thuộc hệ token màu của
 * CatCheck nên không token hoá được (giống `GoogleGlyph`). File này có ngoại lệ ESLint
 * riêng trong `eslint.config.js` — đừng copy pattern này sang component thường.
 */
export function MoMoGlyph({ size = 28 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 28 28"
      fill="none"
      role="img"
      aria-label="MoMo"
      focusable="false"
    >
      <circle cx="14" cy="14" r="14" fill="#A50064" />
      <path
        d="M7.2 19.2V11.4c0-1.77 1.4-3.2 3.12-3.2 1.03 0 1.95.52 2.52 1.32.57-.8 1.49-1.32 2.52-1.32 1.72 0 3.12 1.43 3.12 3.2v7.8h-2.3v-7.7c0-.5-.38-.9-.86-.9s-.86.4-.86.9v7.7h-2.3v-7.7c0-.5-.38-.9-.86-.9s-.86.4-.86.9v7.7H7.2Z"
        fill="#FFFFFF"
      />
    </svg>
  );
}

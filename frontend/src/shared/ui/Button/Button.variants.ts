import { cva, type VariantProps } from "class-variance-authority";

// NOTE: file .variants.ts được phép chứa hex literal theo rule ESLint (không cần ở đây vì
// mọi màu đều tham chiếu token @theme), giữ tách biệt variant logic khỏi Button.tsx.
export const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-xl font-semibold whitespace-nowrap " +
    "transition-colors duration-[var(--duration-base)] ease-[var(--ease-standard)] " +
    "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)] " +
    "disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      // Màu/bo góc/shadow khớp Figma (CatCheck-Demo 26mOVF2zdu4cI1EPz2Syxw): CTA chính dùng
      // primary-dark (#0D369A) chứ không phải primary (#2F4FB2) — primary chỉ dùng cho mảng
      // lớn (hero card, header). Secondary luôn có chữ tối trên nền vàng theo p10.
      variant: {
        // `shadow-brand-lg` = đúng bộ shadow brand blue của p10 §2.5, thay cho chuỗi
        // `shadow-[0px_4px_6px_-1px_rgba(13,54,154,.2),…]` viết cứng trước đây.
        primary: "bg-primary-dark !text-white shadow-brand-lg hover:bg-primary",
        secondary: "bg-secondary text-secondary-text-on shadow-sm hover:bg-secondary-light",
        tertiary: "bg-transparent text-primary border border-border hover:bg-background-alt",
      },
      // p10 §10.2: MỌI phần tử bấm được tối thiểu 44×44px. `sm` trước đây là `h-9` (36px)
      // nên mọi nút "Thử lại" của `ErrorState` và các nút phụ đều dưới ngưỡng chạm.
      // Dùng `min-h-[var(--touch-target-min)]` thay cho chiều cao cố định để nút vẫn co giãn
      // được khi nội dung xuống dòng.
      size: {
        sm: "min-h-[var(--touch-target-min)] px-3 text-caption gap-1.5",
        md: "min-h-[var(--touch-target-min)] px-4 text-body",
        lg: "min-h-13 px-6 text-body",
      },
      loading: {
        true: "relative text-transparent",
        false: "",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
      loading: false,
    },
  },
);

export type ButtonVariantProps = VariantProps<typeof buttonVariants>;

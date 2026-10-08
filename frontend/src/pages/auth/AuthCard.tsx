import type { ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

/**
 * Khung thẻ cho các màn auth 1 cột (quên/đặt lại mật khẩu, bước MFA, trạng thái thiếu nháp).
 *
 * `AuthLayout` bản web mở rộng `main` tới 1280px cho các màn 2 cột (Đăng nhập, Đăng ký,
 * OTP); màn 1 cột đặt thẳng vào đó thì ô nhập/nút kéo dài hết 1184px. Ở `lg` thẻ này thu về
 * 480px, nền trắng, căn giữa — mobile giữ nguyên bố cục cột hẹp của layout.
 */
export function AuthCard({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "flex flex-col gap-6 lg:mx-auto lg:mt-6 lg:w-full lg:max-w-[480px] lg:rounded-3xl lg:bg-surface lg:p-10 lg:shadow-[0px_25px_50px_-12px_rgba(0,0,0,0.12)]",
        className,
      )}
    >
      {children}
    </div>
  );
}

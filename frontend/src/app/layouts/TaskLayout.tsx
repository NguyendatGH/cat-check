import { Outlet, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { ArrowLeft } from "lucide-react";
import { DesktopAside } from "./shell/DesktopAside";
import { DesktopHeader } from "./shell/DesktopHeader";
import { OfflineBar } from "./shell/OfflineBar";

/**
 * Layout tác vụ đơn (TL routes: form, chi tiết, giỏ hàng, cài đặt con…).
 *
 * - `< lg`: header chỉ có nút back, không bottom nav (đang làm một việc).
 * - `>= lg`: CÙNG khung desktop với AppLayout — sidebar 288px + header 64px (kèm nút back).
 *   Các frame web của nhóm này (`Web - 09` Nhắc nhở, `Web - 14 & Giỏ hàng`, `Web - Đặt hàng
 *   thành công`, `Web - 08 & 10` Xuất hồ sơ…) đều nằm trong khung đó; trước đây ở desktop
 *   layout này chỉ có một thanh trắng với mũi tên quay lại, mất toàn bộ điều hướng.
 *
 * Hộp nội dung GIỮ NGUYÊN hợp đồng cũ mà các trang dựa vào: `px-4 py-6`, ở `lg` là hộp
 * 992px có `px-6` (nội dung 944px), canh giữa — trang KHÔNG tự thêm padding ngang.
 */
export function TaskLayout() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();

  return (
    <div className="flex min-h-dvh bg-background">
      <DesktopAside />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-[var(--z-sticky-header)] flex h-[68px] items-center gap-2 border-b border-border bg-surface px-4 lg:hidden">
          <button
            type="button"
            onClick={() => {
              void navigate(-1);
            }}
            aria-label={t("actions.back")}
            className="flex size-[var(--touch-target-min)] items-center justify-center rounded-md hover:bg-background-alt"
          >
            <ArrowLeft size={20} aria-hidden="true" />
          </button>
        </header>
        <DesktopHeader showBack />
        <OfflineBar className="top-[68px] lg:top-16" />
        <main className="w-full flex-1 px-4 py-6 lg:mx-auto lg:max-w-[992px] lg:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

import { Outlet, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { ArrowLeft } from "lucide-react";

/** Layout tác vụ đơn — header có nút back, dùng cho form/wizard 1 việc (TL routes). */
export function TaskLayout() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="sticky top-0 z-[var(--z-sticky-header)] flex items-center gap-2 border-b border-border bg-surface px-4 py-3">
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
      {/* Cùng hộp nội dung desktop với AppLayout (992px - px-6 = 944px) để trang nằm trong
          TaskLayout không bị trải hết 1280px — padding ngang do layout cấp, trang KHÔNG tự thêm. */}
      <main className="flex-1 px-4 py-6 lg:mx-auto lg:w-full lg:max-w-[992px] lg:px-6">
        <Outlet />
      </main>
    </div>
  );
}

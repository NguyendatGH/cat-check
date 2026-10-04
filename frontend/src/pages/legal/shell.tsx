import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";

/**
 * Khung bố cục DESKTOP (>= lg) dùng chung cho mọi trang `/legal/*`.
 *
 * ⚠ Khác với `AppLayout`/`TaskLayout`, `PublicLayout` (layout của nhóm route này) KHÔNG cấp
 * hộp nội dung desktop — `<main>` của nó chỉ là `flex-1`. Nếu trang không tự khai báo thì ở
 * 1280px nội dung hoặc trải hết bề ngang, hoặc (như trước bản sửa này) đứng yên ở cột
 * `max-w-2xl` của mobile. `LEGAL_SHELL` vì vậy tái lập ĐÚNG hộp 944px của app shell
 * (992px trừ `px-6`) để trang pháp lý khớp với phần còn lại của bản web.
 *
 * Bố cục desktop theo đúng idiom đã chốt ở `settings/SettingsProfilePage` và
 * `SettingsSecurityPage`: cột nội dung co giãn + cột phụ cố định 360px, cách nhau `gap-6`.
 * Cột nội dung vì thế rộng 560px — độ dài dòng dễ đọc cho văn bản pháp lý dài, thay vì
 * kéo hết 944px.
 */
export const LEGAL_SHELL = "mx-auto flex w-full max-w-2xl flex-col px-4 py-8 lg:max-w-[992px] lg:px-6 lg:py-10";

/** Hàng chia hai cột ở `lg` — dưới `lg` vẫn là một cột xếp dọc như cũ. */
export const LEGAL_SPLIT = "flex flex-col gap-6 lg:flex-row lg:items-start lg:gap-6";

/** Cột nội dung chính (560px ở desktop). `min-w-0` để bảng/đoạn dài không phá lưới flex. */
export const LEGAL_COLUMN = "flex min-w-0 flex-1 flex-col";

/**
 * Cột phụ 360px — CHỈ tồn tại từ `lg`. Dính theo cuộn, trừ hao chiều cao header dính của
 * `PublicLayout` (px-4 py-3 + text-h3 ≈ 53px) rồi cộng khoảng thở.
 */
export const LEGAL_ASIDE =
  "hidden lg:sticky lg:top-[72px] lg:flex lg:w-[360px] lg:shrink-0 lg:flex-col lg:gap-4 lg:print:hidden";

/** Thẻ trong cột phụ — cùng chất liệu thẻ với `settings` (bo 2xl, nền surface, shadow nhẹ). */
export const LEGAL_ASIDE_CARD = "flex flex-col gap-2 rounded-2xl bg-surface p-5 shadow-brand-md";

export interface TocItem {
  id: string;
  label: string;
  /** 1–3 như cấp heading Markdown; từ cấp 3 trở lên thụt vào một bậc. */
  level?: number;
}

/**
 * Mục lục trong trang cho cột phụ desktop. Dùng neo `#id` thuần (không JS, không router)
 * nên hoạt động cả khi JS chậm và in ra PDF vẫn đúng. Heading đích đã có `scroll-mt-24`
 * để không bị header dính che mất.
 */
export function DocumentToc({ items }: { items: TocItem[] }) {
  const { t } = useTranslation("legal");
  if (items.length === 0) return null;
  return (
    <nav aria-label={t("toc.title")} className={cn(LEGAL_ASIDE_CARD, "gap-3")}>
      <p className="text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">{t("toc.title")}</p>
      <ul className="flex flex-col gap-1 overflow-y-auto">
        {items.map((item) => (
          <li key={item.id}>
            <a
              href={`#${item.id}`}
              className={cn(
                "block rounded-md py-1 text-caption text-text-secondary hover:text-primary hover:underline",
                (item.level ?? 2) >= 3 && "pl-3",
              )}
            >
              {item.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

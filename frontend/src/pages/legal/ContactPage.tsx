import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { cn } from "@/shared/lib/cn";
import { LEGAL_ASIDE, LEGAL_COLUMN, LEGAL_SHELL, LEGAL_SPLIT } from "./shell";

// Dữ liệu liên hệ thật (business-context.md §9) — hằng số, KHÔNG phải copy UI cần dịch,
// nên không đưa vào i18n. Đặt thành biến để tránh literal string trực tiếp trong JSX
// (i18next/no-literal-string mode jsx-text-only vẫn áp cho literal, không áp cho biến).
const HOTLINE_DISPLAY = "(+84) 842 551 312";
const HOTLINE_HREF = "tel:+84842551312";
const EMAIL_DISPLAY = "catcheck.petcare@gmail.com";
const EMAIL_HREF = "mailto:catcheck.petcare@gmail.com";

/**
 * `/legal/contact` (p9 §9.4.3 #13, p15 §15.8.1 G6). Theo p15 §15.4.1 (H11.5), trang này
 * PHẢI chứa đủ 4 mục để thực hiện quyền khiếu nại (Điều 4.1.đ Luật BVDLCN) ngay ở Phase 1,
 * vì `/legal/complaints` (quy trình bán hàng, Điều 11.1.d Luật TMĐT) thuộc Phase 3:
 * (a) kênh nhận khiếu nại, (b) email+hotline DPO, (c) tên & thông tin cơ quan tiếp nhận
 * (A05), (d) mốc thời gian phản hồi. Thông tin doanh nghiệp lấy nguyên văn
 * `spec/reference/business-context.md` §9 — KHÔNG bịa số liệu.
 *
 * DESKTOP (>= lg): ba thẻ bạn CẦN để liên hệ (doanh nghiệp · DPO · kênh khiếu nại) ở cột
 * chính 560px; thẻ cơ quan chuyên trách — thông tin tham chiếu, không phải kênh liên hệ của
 * CatCheck — và lối sang trang Yêu cầu dữ liệu chuyển sang cột phụ 360px. Dưới `lg` vẫn là
 * đúng bốn thẻ xếp dọc như cũ.
 */
export function ContactPage() {
  const { t } = useTranslation(["legal", "common"]);

  return (
    <div className={LEGAL_SHELL}>
      <header className="flex flex-col gap-2 pb-8 lg:max-w-[680px]">
        <h1 className="text-h1 font-bold text-text-primary">{t("pages.contact.title")}</h1>
        <p className="text-body text-text-secondary">{t("contact.intro")}</p>
      </header>

      <div className={LEGAL_SPLIT}>
        <div className={cn(LEGAL_COLUMN, "gap-8")}>
          <section className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-4">
            <h2 className="text-h3 font-bold text-text-primary">{t("contact.companyTitle")}</h2>
            <dl className="flex flex-col gap-1 text-body text-text-secondary">
              <div>
                <dt className="inline font-semibold text-text-primary">{t("contact.fields.name")}: </dt>
                <dd className="inline">{t("contact.companyName")}</dd>
              </div>
              <div>
                <dt className="inline font-semibold text-text-primary">{t("contact.fields.address")}: </dt>
                <dd className="inline">{t("contact.companyAddress")}</dd>
              </div>
              <div>
                <dt className="inline font-semibold text-text-primary">{t("contact.fields.hotline")}: </dt>
                <dd className="inline">
                  <a href={HOTLINE_HREF} className="text-primary hover:underline">
                    {HOTLINE_DISPLAY}
                  </a>
                </dd>
              </div>
              <div>
                <dt className="inline font-semibold text-text-primary">{t("contact.fields.email")}: </dt>
                <dd className="inline">
                  <a href={EMAIL_HREF} className="text-primary hover:underline">
                    {EMAIL_DISPLAY}
                  </a>
                </dd>
              </div>
              <div>
                <dt className="inline font-semibold text-text-primary">{t("contact.fields.workingHours")}: </dt>
                <dd className="inline">{t("contact.workingHoursValue")}</dd>
              </div>
            </dl>
          </section>

          <section className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-4">
            <h2 className="text-h3 font-bold text-text-primary">{t("contact.dpoTitle")}</h2>
            <p className="text-body text-text-secondary">{t("contact.dpoDescription")}</p>
            <p className="text-body text-text-secondary">
              {t("contact.fields.email")}:{" "}
              <a href={EMAIL_HREF} className="text-primary hover:underline">
                {EMAIL_DISPLAY}
              </a>{" "}
              · {t("contact.fields.hotline")}:{" "}
              <a href={HOTLINE_HREF} className="text-primary hover:underline">
                {HOTLINE_DISPLAY}
              </a>
            </p>
          </section>

          <section className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-4">
            <h2 className="text-h3 font-bold text-text-primary">{t("contact.complaintChannelTitle")}</h2>
            <p className="text-body text-text-secondary">{t("contact.complaintChannelDescription")}</p>
            <p className="text-caption text-text-secondary">{t("contact.responseTimeCommitment")}</p>
          </section>

          {/* Từ `lg` hai khối dưới đây sống ở cột phụ — bản này chỉ còn phục vụ mobile. */}
          <section className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-4 lg:hidden">
            <h2 className="text-h3 font-bold text-text-primary">{t("contact.authorityTitle")}</h2>
            <p className="text-body text-text-secondary">{t("contact.authorityName")}</p>
            <p className="text-caption text-text-secondary">{t("contact.authorityBasis")}</p>
          </section>

          <p className="text-caption text-text-tertiary lg:hidden">
            {t("contact.seeAlso")}{" "}
            <Link to="/legal/data-requests" className="font-semibold text-primary hover:underline">
              {t("contact.dataRequestsLink")}
            </Link>
          </p>
        </div>

        <aside className={LEGAL_ASIDE}>
          <section className="flex flex-col gap-2 rounded-2xl bg-surface p-5 shadow-brand-md">
            <h2 className="text-h3 font-bold text-text-primary">{t("contact.authorityTitle")}</h2>
            <p className="text-body text-text-secondary">{t("contact.authorityName")}</p>
            <p className="text-caption text-text-secondary">{t("contact.authorityBasis")}</p>
          </section>

          <p className="text-caption text-text-tertiary">
            {t("contact.seeAlso")}{" "}
            <Link to="/legal/data-requests" className="font-semibold text-primary hover:underline">
              {t("contact.dataRequestsLink")}
            </Link>
          </p>
        </aside>
      </div>
    </div>
  );
}

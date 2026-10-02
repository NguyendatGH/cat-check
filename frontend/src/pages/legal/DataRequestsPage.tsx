import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { useSessionStore } from "@/entities/user";
import { Button } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { DocumentToc, LEGAL_ASIDE, LEGAL_COLUMN, LEGAL_SHELL, LEGAL_SPLIT } from "./shell";

/** Id neo của từng mục — dùng chung cho `<section>` và mục lục cột phải ở desktop. */
const SECTION_IDS = {
  rights: "dsar-rights",
  howToSubmit: "dsar-how-to-submit",
  verification: "dsar-verification",
  rejection: "dsar-rejection",
  tracking: "dsar-tracking",
  complaint: "dsar-complaint",
} as const;

const SECTION_HEADING = "scroll-mt-24 text-h3 font-bold text-text-primary";

/**
 * `/legal/data-requests` (p9 §9.4.3 #12, p15 §15.8.1 G5 `DSAR_PROCEDURE`). Đây là trang
 * HƯỚNG DẪN thủ tục thực hiện quyền (Điều 5.1 NĐ356 buộc công bố quy trình/biểu mẫu),
 * KHÔNG phải bảng điều khiển DSAR tương tác — bảng điều khiển thật (xuất/xoá/hạn chế dữ
 * liệu) nằm ở `/account/privacy` (`pages/dashboard/AccountPrivacyPage.tsx`, ngoài phạm vi
 * A2-FE — xem ORCHESTRATOR "không được đụng"). Trang này công khai, không cần đăng nhập
 * (p9 route #12 Auth: ❌).
 *
 * DESKTOP (>= lg): sáu mục thủ tục ở cột chính 560px + mục lục dính ở cột phụ 360px. Đây
 * là trang dài nhất của nhóm `/legal` mà không lấy nội dung từ CMS, nên mục lục được dựng
 * thẳng từ danh sách mục bên dưới (không qua `extractMarkdownHeadings`).
 */
export function DataRequestsPage() {
  const { t } = useTranslation(["legal", "common"]);
  const navigate = useNavigate();
  const status = useSessionStore((s) => s.status);
  const isAuthenticated = status === "authenticated";

  const rights = [
    { key: "known", sla: "dataRequests.rights.known.sla" },
    { key: "consent", sla: "dataRequests.rights.consent.sla" },
    { key: "viewEdit", sla: "dataRequests.rights.viewEdit.sla" },
    { key: "export", sla: "dataRequests.rights.export.sla" },
    { key: "erase", sla: "dataRequests.rights.erase.sla" },
    { key: "restrict", sla: "dataRequests.rights.restrict.sla" },
    { key: "complain", sla: "dataRequests.rights.complain.sla" },
    { key: "protectionMeasure", sla: "dataRequests.rights.protectionMeasure.sla" },
  ] as const;

  const tocItems = [
    { id: SECTION_IDS.rights, label: t("dataRequests.rightsTitle") },
    { id: SECTION_IDS.howToSubmit, label: t("dataRequests.howToSubmitTitle") },
    { id: SECTION_IDS.verification, label: t("dataRequests.verificationTitle") },
    { id: SECTION_IDS.rejection, label: t("dataRequests.rejectionTitle") },
    { id: SECTION_IDS.tracking, label: t("dataRequests.trackingTitle") },
    { id: SECTION_IDS.complaint, label: t("dataRequests.complaintTitle") },
  ];

  return (
    <div className={LEGAL_SHELL}>
      <header className="flex flex-col gap-2 pb-8 lg:max-w-[680px]">
        <h1 className="text-h1 font-bold text-text-primary">{t("pages.dataRequests.title")}</h1>
        <p className="text-body text-text-secondary">{t("dataRequests.intro")}</p>
      </header>

      <div className={LEGAL_SPLIT}>
        <div className={cn(LEGAL_COLUMN, "gap-8")}>
          <section className="flex flex-col gap-3">
            <h2 id={SECTION_IDS.rights} className={SECTION_HEADING}>
              {t("dataRequests.rightsTitle")}
            </h2>
            <ul className="flex flex-col gap-2">
              {rights.map((right) => (
                <li key={right.key} className="rounded-lg border border-border bg-surface p-3">
                  <p className="text-body font-semibold text-text-primary">{t(`dataRequests.rights.${right.key}.label`)}</p>
                  <p className="text-caption text-text-secondary">{t(`dataRequests.rights.${right.key}.description`)}</p>
                  <p className="mt-1 text-caption font-semibold text-primary">{t(right.sla)}</p>
                </li>
              ))}
            </ul>
            <p className="text-caption text-text-tertiary">{t("dataRequests.slaNote")}</p>
          </section>

          <section className="flex flex-col gap-3">
            <h2 id={SECTION_IDS.howToSubmit} className={SECTION_HEADING}>
              {t("dataRequests.howToSubmitTitle")}
            </h2>
            <ol className="list-decimal space-y-2 pl-5 text-body text-text-secondary">
              <li>{t("dataRequests.howToSubmit.selfService")}</li>
              <li>{t("dataRequests.howToSubmit.webForm")}</li>
              <li>{t("dataRequests.howToSubmit.email")}</li>
            </ol>
            {isAuthenticated ? (
              <Button type="button" className="lg:self-start" onClick={() => { void navigate("/account/privacy"); }}>
                {t("dataRequests.goToPrivacyCenter")}
              </Button>
            ) : (
              <Button type="button" className="lg:self-start" onClick={() => { void navigate("/auth/login?next=%2Faccount%2Fprivacy"); }}>
                {t("dataRequests.loginToSubmit")}
              </Button>
            )}
          </section>

          <section className="flex flex-col gap-3">
            <h2 id={SECTION_IDS.verification} className={SECTION_HEADING}>
              {t("dataRequests.verificationTitle")}
            </h2>
            <p className="text-body text-text-secondary">{t("dataRequests.verificationDescription")}</p>
          </section>

          <section className="flex flex-col gap-3">
            <h2 id={SECTION_IDS.rejection} className={SECTION_HEADING}>
              {t("dataRequests.rejectionTitle")}
            </h2>
            <p className="text-body text-text-secondary">{t("dataRequests.rejectionDescription")}</p>
          </section>

          <section className="flex flex-col gap-3">
            <h2 id={SECTION_IDS.tracking} className={SECTION_HEADING}>
              {t("dataRequests.trackingTitle")}
            </h2>
            <p className="text-body text-text-secondary">{t("dataRequests.trackingDescription")}</p>
          </section>

          <section className="flex flex-col gap-3">
            <h2 id={SECTION_IDS.complaint} className={SECTION_HEADING}>
              {t("dataRequests.complaintTitle")}
            </h2>
            <p className="text-body text-text-secondary">
              {t("dataRequests.complaintDescription")}{" "}
              <Link to="/legal/contact" className="font-semibold text-primary hover:underline">
                {t("dataRequests.contactLink")}
              </Link>
              .
            </p>
          </section>
        </div>

        <aside className={LEGAL_ASIDE}>
          <DocumentToc items={tocItems} />
        </aside>
      </div>
    </div>
  );
}

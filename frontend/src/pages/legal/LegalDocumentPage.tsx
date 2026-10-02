import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { EmptyState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api";
import { usePolicyCurrent } from "./hooks";
import { extractMarkdownHeadings, renderMarkdown } from "./markdown";
import {
  DocumentToc,
  LEGAL_ASIDE,
  LEGAL_ASIDE_CARD,
  LEGAL_COLUMN,
  LEGAL_SHELL,
  LEGAL_SPLIT,
} from "./shell";
import type { PolicyCode } from "./types";

interface LegalDocumentPageProps {
  policyCode: PolicyCode;
  /** Nội dung phụ đặt SAU thân markdown (vd. `CookieTable` ở `/legal/cookies`). */
  extra?: ReactNode;
  /** Nội dung phụ đặt TRƯỚC thân markdown (vd. `DisclaimerBanner` variant="long"). */
  before?: ReactNode;
}

/**
 * Khung trang pháp lý dùng chung cho `/legal/terms`, `/legal/privacy`,
 * `/legal/medical-disclaimer`, `/legal/cookies` (p10 §5.2 `LegalPage` + `PolicyVersionBanner`,
 * REQ-LEGAL-01/02). Nội dung lấy từ `GET /policies/{code}` (`policy_version.content_md`),
 * KHÔNG hard-code văn bản pháp lý trong component.
 *
 * ⚠ M1 CHƯA có seed `R__seed_policy_version.sql` (xem `docs/handovers/A2.md` §3 mục 7) —
 * endpoint trả 404 `POLICY_VERSION_NOT_FOUND` cho MỌI mã ở thời điểm này. Trang xử lý
 * đúng chuẩn REQ-LEGAL-04 (trạng thái "bản nháp chưa có nội dung"), không giả lập nội dung.
 *
 * DESKTOP (>= lg, xem `shell.tsx`): cột văn bản 560px + cột phụ 360px chứa thẻ phiên bản và
 * mục lục dính theo cuộn. Dưới `lg` vẫn đúng một cột như bản mobile đã chạy.
 */
export function LegalDocumentPage({ policyCode, extra, before }: LegalDocumentPageProps) {
  const { t } = useTranslation(["legal", "common"]);
  const { data, isLoading, isError, error } = usePolicyCurrent(policyCode);

  if (isLoading) {
    return (
      <div className={cn(LEGAL_SHELL, "gap-4 lg:max-w-[760px]")}>
        <SkeletonLoader shape="text" className="h-8 w-2/3" />
        <SkeletonLoader shape="card" className="h-64" />
      </div>
    );
  }

  const notFound = isApiError(error) && error.code === "POLICY_VERSION_NOT_FOUND";

  if (isError || !data) {
    return (
      <div className={cn(LEGAL_SHELL, "gap-6 lg:max-w-[760px]")}>
        {before}
        <EmptyState
          title={t(`pages.${policyCodeToPageKey(policyCode)}.title`)}
          description={notFound ? t("document.notPublishedYet") : t("document.loadError")}
        />
      </div>
    );
  }

  const headings = data.contentMd ? extractMarkdownHeadings(data.contentMd) : [];
  const versionLine = t("document.versionBanner", {
    version: data.version,
    date: new Date(data.effectiveFrom).toLocaleDateString("vi-VN"),
  });
  const versionsHref = `/legal/${policyCode.toLowerCase()}/versions`;

  return (
    <div className={cn(LEGAL_SHELL, "print:max-w-none")}>
      <div className={LEGAL_SPLIT}>
        <article className={cn(LEGAL_COLUMN, "gap-6")}>
          <header className="flex flex-col gap-2">
            <h1 className="text-h1 font-bold text-text-primary">{data.title}</h1>
            {/* Dải phiên bản inline chỉ dùng ở mobile — từ `lg` nó chuyển sang cột phụ. */}
            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-background-alt px-3 py-2 text-caption text-text-secondary print:hidden lg:hidden">
              <span>{versionLine}</span>
              <Link to={versionsHref} className="font-semibold text-primary hover:underline">
                {t("document.viewPreviousVersions")}
              </Link>
            </div>
            {data.summaryOfChanges ? (
              <p className="rounded-lg border border-border bg-chip-bg p-3 text-caption text-text-primary">
                <strong>{t("document.summaryOfChangesLabel")}</strong> {data.summaryOfChanges}
              </p>
            ) : null}
          </header>

          {before}

          {data.contentMd ? (
            renderMarkdown(data.contentMd)
          ) : data.contentUrl ? (
            <p className="text-body text-text-secondary">
              {t("document.hostedExternally")}{" "}
              <a href={data.contentUrl} target="_blank" rel="noreferrer" className="font-semibold text-primary hover:underline">
                {data.contentUrl}
              </a>
            </p>
          ) : null}

          {extra}
        </article>

        <aside className={LEGAL_ASIDE}>
          <div className={LEGAL_ASIDE_CARD}>
            <p className="text-caption text-text-secondary">{versionLine}</p>
            <Link to={versionsHref} className="text-caption font-semibold text-primary hover:underline">
              {t("document.viewPreviousVersions")}
            </Link>
          </div>
          <DocumentToc items={headings} />
        </aside>
      </div>
    </div>
  );
}

function policyCodeToPageKey(code: PolicyCode): "terms" | "privacy" | "medicalDisclaimer" | "cookies" {
  switch (code) {
    case "TERMS":
      return "terms";
    case "PRIVACY":
      return "privacy";
    case "MEDICAL_DISCLAIMER":
      return "medicalDisclaimer";
    case "COOKIE":
      return "cookies";
  }
}

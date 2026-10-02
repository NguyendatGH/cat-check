import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { EmptyState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
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

const SLUG_TO_CODE: Record<string, PolicyCode> = {
  terms: "TERMS",
  privacy: "PRIVACY",
  cookies: "COOKIE",
  "medical-disclaimer": "MEDICAL_DISCLAIMER",
};

/**
 * `/legal/:policyCode/v/:version` (p9 §9.4.3 #16, REQ-LEGAL-03 — "URL của phiên bản cụ
 * thể phải truy cập được vĩnh viễn"). Backend hôm nay không lưu/serve được các bản CŨ
 * (chỉ có "bản hiện hành") — trang chỉ render đúng khi `:version` khớp bản hiện hành,
 * ngược lại báo rõ đây là giới hạn backend thay vì âm thầm hiện sai nội dung.
 *
 * DESKTOP (>= lg): cùng bố cục với `LegalDocumentPage` — cột văn bản 560px + cột phụ 360px
 * (thẻ phiên bản + mục lục dính), để hai URL của cùng một tài liệu đọc giống hệt nhau.
 */
export function PolicyVersionDetailPage() {
  const { t } = useTranslation(["legal", "common"]);
  const { policyCode: slug, version } = useParams<{ policyCode: string; version: string }>();
  const code = slug ? SLUG_TO_CODE[slug] : undefined;
  const { data, isLoading } = usePolicyCurrent(code ?? "TERMS", "vi");

  if (!code) {
    return <EmptyState title={t("versions.notFoundTitle")} description={t("versions.notFoundDescription")} />;
  }

  if (isLoading) {
    return (
      <div className={cn(LEGAL_SHELL, "gap-4 lg:max-w-[760px]")}>
        <SkeletonLoader shape="text" className="h-8 w-1/2" />
        <SkeletonLoader shape="card" className="h-64" />
      </div>
    );
  }

  const matchesCurrent = data && data.version === version;

  if (!matchesCurrent) {
    return (
      <div className={cn(LEGAL_SHELL, "gap-4 lg:max-w-[760px]")}>
        <EmptyState
          title={t("versionDetail.archiveNotAvailableTitle")}
          description={t("versionDetail.archiveNotAvailableDescription", { version: version ?? "" })}
          action={
            <Link to={`/legal/${slug ?? ""}`} className="font-semibold text-primary hover:underline">
              {t("versions.backToDocument")}
            </Link>
          }
        />
      </div>
    );
  }

  const headings = data.contentMd ? extractMarkdownHeadings(data.contentMd) : [];
  const versionLine = t("document.versionBanner", {
    version: data.version,
    date: new Date(data.effectiveFrom).toLocaleDateString("vi-VN"),
  });

  return (
    <div className={cn(LEGAL_SHELL, "print:max-w-none")}>
      <div className={LEGAL_SPLIT}>
        <article className={cn(LEGAL_COLUMN, "gap-6")}>
          <header className="flex flex-col gap-2">
            <h1 className="text-h1 font-bold text-text-primary">{data.title}</h1>
            {/* Từ `lg` dòng phiên bản nằm ở cột phụ, nên bản inline này chỉ phục vụ mobile/in ấn. */}
            <p className="text-caption text-text-secondary lg:hidden lg:print:block">{versionLine}</p>
          </header>
          {data.contentMd ? renderMarkdown(data.contentMd) : null}
        </article>

        <aside className={LEGAL_ASIDE}>
          <div className={LEGAL_ASIDE_CARD}>
            <p className="text-caption text-text-secondary">{versionLine}</p>
            <Link
              to={`/legal/${slug ?? ""}`}
              className="text-caption font-semibold text-primary hover:underline"
            >
              {t("versions.backToDocument")}
            </Link>
          </div>
          <DocumentToc items={headings} />
        </aside>
      </div>
    </div>
  );
}

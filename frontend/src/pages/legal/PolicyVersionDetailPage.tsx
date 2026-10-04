import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { EmptyState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { usePolicyVersion } from "./hooks";
import { extractMarkdownHeadings, renderMarkdown } from "./markdown";
import { DocumentToc, LEGAL_ASIDE, LEGAL_ASIDE_CARD, LEGAL_COLUMN, LEGAL_SHELL, LEGAL_SPLIT } from "./shell";
import type { PolicyCode } from "./types";

const SLUG_TO_CODE: Record<string, PolicyCode> = {
  terms: "TERMS",
  privacy: "PRIVACY",
  cookies: "COOKIE",
  "medical-disclaimer": "MEDICAL_DISCLAIMER",
};

/**
 * `/legal/:policyCode/v/:version` (p9 §9.4.3 #16, REQ-LEGAL-03 — "URL của phiên bản cụ thể
 * phải truy cập được vĩnh viễn").
 *
 * Nguồn dữ liệu là **F10 `GET /policies/{code}/versions/{version}`** — permalink phục vụ CẢ
 * bản đã hết hiệu lực, không bao giờ trả 410, chỉ 404 khi phiên bản chưa từng tồn tại. Trang
 * trước đây gọi C16 (bản hiện hành) và báo "chưa có bản lưu trữ" cho mọi phiên bản cũ; đó là
 * hiển thị sai chứ không phải giới hạn backend.
 *
 * DESKTOP (>= lg): cùng bố cục với `LegalDocumentPage` — cột văn bản 560px + cột phụ 360px.
 */
export function PolicyVersionDetailPage() {
  const { t, i18n } = useTranslation(["legal", "common"]);
  const { policyCode: slug, version } = useParams<{ policyCode: string; version: string }>();
  const code = slug ? SLUG_TO_CODE[slug] : undefined;
  const { data, isPending, isError } = usePolicyVersion(code, version);

  if (!code) {
    return <EmptyState title={t("versions.notFoundTitle")} description={t("versions.notFoundDescription")} />;
  }

  if (isPending) {
    return (
      <div className={cn(LEGAL_SHELL, "gap-4 lg:max-w-[760px]")}>
        <SkeletonLoader shape="text" className="h-8 w-1/2" />
        <SkeletonLoader shape="card" className="h-64" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className={cn(LEGAL_SHELL, "gap-4 lg:max-w-[760px]")}>
        <EmptyState
          title={t("versionDetail.notFoundTitle")}
          description={t("versionDetail.notFoundDescription", { version: version ?? "" })}
          action={
            <Link to={`/legal/${slug ?? ""}/versions`} className="font-semibold text-primary hover:underline">
              {t("versionDetail.backToVersions")}
            </Link>
          }
        />
      </div>
    );
  }

  const headings = data.contentMd ? extractMarkdownHeadings(data.contentMd) : [];
  const versionLine = t("document.versionBanner", {
    version: data.version,
    date: new Date(data.effectiveFrom).toLocaleDateString(i18n.language),
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
          {data.summaryOfChanges ? (
            <p className="rounded-lg border border-border bg-background-alt p-3 text-caption text-text-secondary">
              {t("document.summaryOfChangesLabel")} {data.summaryOfChanges}
            </p>
          ) : null}
          {data.contentMd ? renderMarkdown(data.contentMd) : null}
          {!data.contentMd && data.contentUrl ? (
            <p className="text-caption text-text-secondary">
              {t("document.hostedExternally")}{" "}
              <a href={data.contentUrl} className="font-semibold text-primary hover:underline">
                {data.contentUrl}
              </a>
            </p>
          ) : null}
        </article>

        <aside className={LEGAL_ASIDE}>
          <div className={LEGAL_ASIDE_CARD}>
            <p className="text-caption text-text-secondary">{versionLine}</p>
            <Link
              to={`/legal/${slug ?? ""}/versions`}
              className="text-caption font-semibold text-primary hover:underline"
            >
              {t("versionDetail.backToVersions")}
            </Link>
            <Link to={`/legal/${slug ?? ""}`} className="text-caption font-semibold text-primary hover:underline">
              {t("versions.backToDocument")}
            </Link>
          </div>
          <DocumentToc items={headings} />
        </aside>
      </div>
    </div>
  );
}

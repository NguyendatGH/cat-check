import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { EmptyState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api";
import { intlLocale } from "@/shared/lib/format/locale";
import { usePolicyCurrent } from "./hooks";
import { extractMarkdownHeadings, renderMarkdown, stripLeadingTitle } from "./markdown";
import { DocumentToc, LEGAL_ASIDE, LEGAL_ASIDE_CARD, LEGAL_COLUMN, LEGAL_SHELL, LEGAL_SPLIT } from "./shell";
import type { PolicyCode } from "./types";

interface LegalDocumentPageProps {
  policyCode: PolicyCode;
  extra?: ReactNode;
  before?: ReactNode;
}

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
        {notFound ? extra : null}
      </div>
    );
  }

  const body = data.contentMd ? stripLeadingTitle(data.contentMd, data.title) : null;
  const headings = body ? extractMarkdownHeadings(body) : [];
  const versionLine = t("document.versionBanner", {
    version: data.version,
    date: new Date(data.effectiveFrom).toLocaleDateString(intlLocale()),
  });
  const versionsHref = `/legal/${policyCode.toLowerCase()}/versions`;

  return (
    <div className={cn(LEGAL_SHELL, "print:max-w-none")}>
      <div className={LEGAL_SPLIT}>
        <article className={cn(LEGAL_COLUMN, "gap-6")}>
          <header className="flex flex-col gap-2">
            <h1 className="text-h1 font-bold text-text-primary">{data.title}</h1>
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
            renderMarkdown(body ?? "")
          ) : data.contentUrl ? (
            <p className="text-body text-text-secondary">
              {t("document.hostedExternally")}{" "}
              <a
                href={data.contentUrl}
                target="_blank"
                rel="noreferrer"
                className="font-semibold text-primary hover:underline"
              >
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

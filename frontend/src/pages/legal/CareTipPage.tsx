import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { isApiError } from "@/shared/api";
import { EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useCareTip } from "./hooks";
import { renderMarkdown, stripLeadingTitle } from "./markdown";
import { LEGAL_SHELL } from "./shell";

/** Published care tip — the page an assistant citation opens. */
export function CareTipPage() {
  const { t, i18n } = useTranslation(["legal", "common"]);
  const { slug = "" } = useParams();
  const { data, isLoading, isError, error, refetch } = useCareTip(slug);

  if (isLoading) {
    return (
      <div className={cn(LEGAL_SHELL, "gap-4 lg:max-w-[760px]")}>
        <SkeletonLoader shape="text" className="h-8 w-2/3" />
        <SkeletonLoader shape="card" className="h-64" />
      </div>
    );
  }

  if (isError && !(isApiError(error) && error.status === 404)) {
    return (
      <div className={cn(LEGAL_SHELL, "lg:max-w-[760px]")}>
        <ErrorState
          title={t("document.loadError")}
          onRetry={() => {
            void refetch();
          }}
          retryLabel={t("actions.retry", { ns: "common" })}
        />
      </div>
    );
  }

  if (!data) {
    return (
      <div className={cn(LEGAL_SHELL, "lg:max-w-[760px]")}>
        <EmptyState title={t("careTip.notFoundTitle")} description={t("careTip.notFound")} />
      </div>
    );
  }

  return (
    <article className={cn(LEGAL_SHELL, "gap-6 lg:max-w-[760px]")}>
      <header className="flex flex-col gap-2">
        <h1 className="text-h1 font-bold text-text-primary">{data.title}</h1>
        {data.summary ? <p className="text-body text-text-secondary">{data.summary}</p> : null}
        {data.reviewedAt ? (
          <p className="text-caption text-text-tertiary">
            {t("careTip.reviewedAt", { date: new Date(data.reviewedAt).toLocaleDateString(i18n.language) })}
          </p>
        ) : null}
      </header>
      {data.bodyMd ? renderMarkdown(stripLeadingTitle(data.bodyMd, data.title)) : null}
      {data.sourceReference ? (
        <p className="rounded-lg border border-border bg-background-alt p-3 text-caption text-text-secondary">
          <strong>{t("careTip.sourceLabel")}</strong> {data.sourceReference}
        </p>
      ) : null}
      <p className="text-caption text-text-tertiary">{t("careTip.disclaimer")}</p>
    </article>
  );
}

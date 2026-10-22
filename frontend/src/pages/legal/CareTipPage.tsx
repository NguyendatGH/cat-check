import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import {
  ArrowLeft,
  BookOpen,
  Bot,
  Calendar,
  Check,
  Clock,
  Share2,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import { isApiError } from "@/shared/api";
import { Badge, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { buttonVariants } from "@/shared/ui/Button/Button.variants";
import { cn } from "@/shared/lib/cn";
import { useCareTip } from "./hooks";
import { renderMarkdown, stripLeadingTitle } from "./markdown";
import { LEGAL_SHELL } from "./shell";

/** Published care tip — enhanced with Clean Minimalist B2B design. */
export function CareTipPage() {
  const { t, i18n } = useTranslation(["legal", "common"]);
  const { slug = "" } = useParams();
  const { data, isLoading, isError, error, refetch } = useCareTip(slug);
  const [copied, setCopied] = useState(false);

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setCopied(true);
      setTimeout(() => {
        setCopied(false);
      }, 2000);
    } catch {
      // Ignore clipboard write failure
    }
  };

  if (isLoading) {
    return (
      <div className={cn(LEGAL_SHELL, "gap-6 lg:max-w-[820px]")}>
        <SkeletonLoader shape="text" className="h-6 w-1/4" />
        <SkeletonLoader shape="text" className="h-10 w-3/4" />
        <SkeletonLoader shape="card" className="h-96" />
      </div>
    );
  }

  if (isError && !(isApiError(error) && error.status === 404)) {
    return (
      <div className={cn(LEGAL_SHELL, "lg:max-w-[820px]")}>
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
      <div className={cn(LEGAL_SHELL, "lg:max-w-[820px]")}>
        <EmptyState title={t("careTip.notFoundTitle")} description={t("careTip.notFound")} />
      </div>
    );
  }

  const wordCount = data.bodyMd ? data.bodyMd.trim().split(/\s+/).length : 0;
  const readMinutes = Math.max(1, Math.ceil(wordCount / 180));

  return (
    <div className={cn(LEGAL_SHELL, "gap-6 lg:max-w-[820px]")}>
      {/* Navigation Breadcrumb / Back to Assistant */}
      <nav aria-label="Breadcrumb" className="flex items-center justify-between gap-3 text-caption">
        <Link
          to="/assistant"
          className="inline-flex items-center gap-1.5 font-semibold text-primary transition-colors hover:text-primary-dark"
        >
          <ArrowLeft size={16} aria-hidden="true" />
          <span>{t("careTip.backToAssistant")}</span>
        </Link>

        <button
          type="button"
          onClick={() => {
            void handleCopyLink();
          }}
          className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1 text-text-secondary transition-colors hover:bg-background-alt hover:text-text-primary"
          title={t("careTip.shareTip")}
        >
          {copied ? (
            <>
              <Check size={14} className="text-success-text" />
              <span className="text-success-text">{t("careTip.copied")}</span>
            </>
          ) : (
            <>
              <Share2 size={14} />
              <span>{t("careTip.shareTip")}</span>
            </>
          )}
        </button>
      </nav>

      {/* Article Header & Hero */}
      <header className="flex flex-col gap-4 rounded-3xl border border-border/70 bg-surface p-6 shadow-brand-sm lg:p-8">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="brand" className="font-semibold">
            {t("careTip.category")}
          </Badge>
          <Badge tone="success" className="inline-flex items-center gap-1 font-semibold">
            <ShieldCheck size={12} aria-hidden="true" />
            {t("careTip.verifiedByVet")}
          </Badge>
        </div>

        <h1 className="text-h1 font-bold leading-tight tracking-tight text-text-primary lg:text-3xl">
          {data.title}
        </h1>

        {data.summary ? (
          <div className="rounded-2xl border border-primary/20 bg-primary/5 p-4 text-body font-medium leading-relaxed text-primary-dark">
            {data.summary}
          </div>
        ) : null}

        <div className="flex flex-wrap items-center gap-4 border-t border-border/50 pt-4 text-caption text-text-tertiary">
          {data.reviewedAt ? (
            <div className="inline-flex items-center gap-1.5">
              <Calendar size={14} aria-hidden="true" />
              <span>
                {t("careTip.reviewedAt", {
                  date: new Date(data.reviewedAt).toLocaleDateString(i18n.language),
                })}
              </span>
            </div>
          ) : null}

          <div className="inline-flex items-center gap-1.5">
            <Clock size={14} aria-hidden="true" />
            <span>{t("careTip.readingTime", { minutes: String(readMinutes) })}</span>
          </div>
        </div>
      </header>

      {/* Article Main Content Body */}
      <article className="rounded-3xl border border-border/70 bg-surface p-6 text-body leading-relaxed text-text-primary shadow-brand-sm lg:p-10">
        <div className="space-y-4 prose prose-blue max-w-none">
          {data.bodyMd ? renderMarkdown(stripLeadingTitle(data.bodyMd, data.title)) : null}
        </div>

        {data.sourceReference ? (
          <div className="mt-8 rounded-2xl border border-border/80 bg-background-alt/70 p-4">
            <div className="flex items-start gap-2.5">
              <BookOpen size={18} className="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
              <div className="text-caption text-text-secondary">
                <strong className="font-semibold text-text-primary">{t("careTip.sourceLabel")} </strong>
                <span>{data.sourceReference}</span>
              </div>
            </div>
          </div>
        ) : null}
      </article>

      {/* Interactive AI Assistant Callout */}
      <section className="relative overflow-hidden rounded-3xl border border-primary/20 bg-gradient-to-br from-primary/10 via-primary/5 to-transparent p-6 shadow-sm lg:p-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3.5">
            <div className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-primary text-white shadow-md shadow-primary/25">
              <Bot size={22} />
            </div>
            <div>
              <h2 className="flex items-center gap-1.5 text-body font-bold text-text-primary sm:text-h3">
                <span>{t("careTip.askAiAboutThis")}</span>
                <Sparkles size={16} className="text-accent" />
              </h2>
              <p className="mt-1 text-small text-text-secondary">{t("careTip.askAiDesc")}</p>
            </div>
          </div>

          <Link
            to="/assistant"
            className={cn(
              buttonVariants({ variant: "primary", size: "md" }),
              "shrink-0 gap-2 font-semibold shadow-md shadow-primary/20 hover:shadow-primary/30",
            )}
          >
            <Bot size={16} />
            <span>{t("careTip.askAiButton")}</span>
          </Link>
        </div>
      </section>

      {/* Medical Disclaimer Note */}
      <footer className="px-2 text-center text-caption text-text-tertiary">
        <p>{t("careTip.disclaimer")}</p>
      </footer>
    </div>
  );
}

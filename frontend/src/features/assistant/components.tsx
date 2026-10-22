import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  AlertCircle,
  Bot,
  Check,
  Copy,
  ExternalLink,
  GlassWater,
  MessageCircleQuestion,
  Pipette,
  RotateCcw,
  Shovel,
  TriangleAlert,
} from "lucide-react";
import { CatAvatar } from "@/entities/cat";
import { cn } from "@/shared/lib/cn";
import { intlLocale } from "@/shared/lib/format/locale";
import type { AiCitation } from "./api";
import "./assistant.css";

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, index) =>
    part.startsWith("**") && part.endsWith("**") && part.length > 4 ? (
      <strong key={`${keyPrefix}-${String(index)}`} className="font-semibold">
        {part.slice(2, -2)}
      </strong>
    ) : (
      part
    ),
  );
}

// Lightweight formatting for bullet/numbered lists, **bold**, headings, and dividers; never injects HTML.
export function RichText({ text }: { text: string }) {
  const blocks: ReactNode[] = [];
  let list: { ordered: boolean; items: string[] } | null = null;

  const flush = () => {
    if (!list) return;
    const key = `list-${String(blocks.length)}`;
    const items = list.items.map((item, i) => (
      <li key={`${key}-${String(i)}`}>{renderInline(item, `${key}-${String(i)}`)}</li>
    ));
    blocks.push(
      list.ordered ? (
        <ol key={key} className="list-decimal space-y-1 pl-5">
          {items}
        </ol>
      ) : (
        <ul key={key} className="list-disc space-y-1 pl-5">
          {items}
        </ul>
      ),
    );
    list = null;
  };

  text.split("\n").forEach((raw) => {
    const line = raw.trimEnd();

    // Markdown horizontal divider (---, ***, ___)
    if (/^\s*[-*_]{3,}\s*$/.test(line)) {
      flush();
      const key = `hr-${String(blocks.length)}`;
      blocks.push(<hr key={key} className="my-1.5 border-t border-border/50" />);
      return;
    }

    // Markdown headings (# Title, ## Title, ### Title)
    const headingMatch = /^\s*#{1,6}\s+(.*)$/.exec(line);
    if (headingMatch) {
      flush();
      const key = `h-${String(blocks.length)}`;
      blocks.push(
        <p key={key} className="font-bold text-text-primary text-[15px] pt-1">
          {renderInline(headingMatch[1], key)}
        </p>,
      );
      return;
    }

    // Markdown table divider row (|---|---|) -> ignore
    if (/^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?\s*$/.test(line)) {
      return;
    }

    // Markdown table row (| Col 1 | Col 2 |) -> convert to clean formatted list item
    const tableRowMatch = /^\s*\|(.*)\|\s*$/.exec(line);
    if (tableRowMatch) {
      const cells = tableRowMatch[1]
        .split("|")
        .map((c) => c.trim())
        .filter((c) => c.length > 0);
      if (cells.length > 0) {
        if (list && list.ordered) flush();
        list ??= { ordered: false, items: [] };
        list.items.push(cells.join(" — "));
        return;
      }
    }

    const bullet = /^\s*[-*•]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (bullet || numbered) {
      const ordered = Boolean(numbered);
      if (list && list.ordered !== ordered) flush();
      list ??= { ordered, items: [] };
      list.items.push((bullet ?? numbered)?.[1] ?? "");
      return;
    }
    flush();
    if (line.trim() === "") return;
    const key = `p-${String(blocks.length)}`;
    blocks.push(<p key={key}>{renderInline(line, key)}</p>);
  });
  flush();

  return <div className="flex flex-col gap-2 break-words text-body leading-relaxed">{blocks}</div>;
}

export function AssistantAvatar({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        "flex size-8 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary-dark text-white shadow-sm shadow-primary/25",
        className,
      )}
      aria-hidden="true"
    >
      <Bot size={16} />
    </span>
  );
}

function useTimeLabel(iso: string) {
  useTranslation(); // subscribe so the label re-renders on language change
  return new Date(iso).toLocaleTimeString(intlLocale(), { hour: "2-digit", minute: "2-digit" });
}

export interface UserBubbleProps {
  text: string;
  sentAt: string;
  error?: string;
  onRetry?: () => void;
  retrying?: boolean;
}

export function UserBubble({ text, sentAt, error, onRetry, retrying }: UserBubbleProps) {
  const { t } = useTranslation("assistant");
  const time = useTimeLabel(sentAt);
  const failed = error !== undefined;
  return (
    <div className="assistant-msg-in flex flex-col items-end gap-1">
      <div
        className={cn(
          "max-w-[85%] whitespace-pre-wrap break-words rounded-[20px] rounded-br-md px-4 py-2.5 text-body leading-relaxed",
          failed ? "bg-danger-bg text-danger-text" : "bg-primary text-white shadow-md shadow-primary/20",
        )}
      >
        {text}
      </div>
      {failed ? (
        <div role="alert" className="flex items-center gap-2 text-small text-danger-text">
          <AlertCircle size={14} aria-hidden="true" />
          <span>{error || t("failedToSend")}</span>
          {onRetry ? (
            <button
              type="button"
              onClick={onRetry}
              disabled={retrying}
              className="inline-flex min-h-8 items-center gap-1 rounded-full px-2 font-semibold text-primary-dark hover:bg-chip-bg disabled:opacity-50"
            >
              <RotateCcw size={13} aria-hidden="true" />
              {t("retry")}
            </button>
          ) : null}
        </div>
      ) : (
        <span className="pr-1 text-[11px] text-text-tertiary">{time}</span>
      )}
    </div>
  );
}

export interface AssistantBubbleProps {
  text: string;
  createdAt: string;
  citations: AiCitation[];
  messageId: string;
}

export function AssistantBubble({ text, createdAt, citations, messageId }: AssistantBubbleProps) {
  const { t } = useTranslation("assistant");
  const time = useTimeLabel(createdAt);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timer = window.setTimeout(() => {
      setCopied(false);
    }, 1600);
    return () => {
      window.clearTimeout(timer);
    };
  }, [copied]);

  const copy = () => {
    void navigator.clipboard
      .writeText(text)
      .then(() => {
        setCopied(true);
      })
      .catch(() => undefined);
  };

  return (
    <article className="assistant-msg-in flex items-start gap-2.5">
      <AssistantAvatar className="mt-0.5" />
      <div className="flex min-w-0 max-w-[88%] flex-col gap-1.5">
        <p className="flex items-baseline gap-2 pl-0.5 text-[12px] leading-4">
          <span className="font-semibold text-text-primary">{t("title")}</span>
          <span className="text-text-tertiary">{time}</span>
        </p>
        <div className="rounded-2xl rounded-tl-md bg-surface px-4 py-3 text-text-primary shadow-sm shadow-primary/5">
          <RichText text={text} />
          {citations.length > 0 ? (
            <div className="mt-3 border-t border-border/50 pt-3">
              <p className="text-small font-semibold text-text-secondary">{t("sources")}</p>
              <ol className="mt-2 flex flex-col gap-1.5">
                {citations.map((citation) => (
                  <li key={`${messageId}-${String(citation.rank)}`} className="min-w-0">
                    <a
                      href={citation.sourceUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="group flex min-h-10 items-center gap-2.5 rounded-xl bg-background-alt px-2.5 py-2 text-small font-medium text-text-primary transition-colors duration-200 hover:bg-chip-bg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                    >
                      <span className="flex size-6 shrink-0 items-center justify-center rounded-lg bg-surface text-[11px] font-bold tabular-nums text-primary-dark">
                        {citation.rank}
                      </span>
                      <span className="min-w-0 flex-1 truncate">{citation.title}</span>
                      <ExternalLink
                        size={14}
                        className="shrink-0 text-text-tertiary transition-colors group-hover:text-primary"
                        aria-hidden="true"
                      />
                    </a>
                  </li>
                ))}
              </ol>
            </div>
          ) : null}
        </div>
        <div className="flex items-center pl-0.5">
          <button
            type="button"
            onClick={copy}
            aria-label={copied ? t("copied") : t("copy")}
            className="inline-flex min-h-8 items-center gap-1.5 rounded-lg px-2 text-[12px] font-medium text-text-tertiary transition-colors duration-200 hover:bg-surface hover:text-text-primary active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-primary"
          >
            {copied ? <Check size={13} aria-hidden="true" /> : <Copy size={13} aria-hidden="true" />}
            <span>{copied ? t("copied") : t("copy")}</span>
          </button>
        </div>
      </div>
    </article>
  );
}

export function TypingIndicator() {
  const { t } = useTranslation("assistant");
  return (
    <div className="assistant-msg-in flex items-start gap-2.5" role="status">
      <AssistantAvatar className="mt-0.5" />
      <div className="flex items-center gap-2.5 rounded-2xl rounded-tl-md bg-surface px-4 py-3 shadow-sm shadow-primary/5">
        <span className="flex items-center gap-1" aria-hidden="true">
          <span className="assistant-dot size-1.5 rounded-full bg-primary" />
          <span className="assistant-dot size-1.5 rounded-full bg-primary" />
          <span className="assistant-dot size-1.5 rounded-full bg-primary" />
        </span>
        <span className="text-small text-text-secondary">{t("thinking")}</span>
      </div>
    </div>
  );
}

export function ContextDivider({ label }: { label: string }) {
  return (
    <div className="assistant-msg-in flex items-center gap-3 py-1">
      <span className="h-px flex-1 bg-border/70" />
      <span className="rounded-full bg-chip-bg px-3 py-1 text-[12px] font-semibold text-primary-dark">{label}</span>
      <span className="h-px flex-1 bg-border/70" />
    </div>
  );
}

export interface AssistantEmptyStateProps {
  catName?: string;
  catAvatarUrl?: string | null;
  suggestions: string[];
  onPick: (question: string) => void;
}

// Theo thứ tự câu gợi ý trong assistant.json; câu thêm sau dùng icon chung.
const SUGGESTION_ICONS = [Shovel, Pipette, TriangleAlert, GlassWater];

export function AssistantEmptyState({ catName, catAvatarUrl, suggestions, onPick }: AssistantEmptyStateProps) {
  const { t } = useTranslation("assistant");
  return (
    <section className="assistant-msg-in relative my-auto flex w-full flex-col gap-6 py-4">
      <div
        className="pointer-events-none absolute -left-16 -top-10 size-56 rounded-full bg-chip-bg opacity-70 blur-3xl"
        aria-hidden="true"
      />
      <div className="relative flex flex-col gap-3">
        <div className="flex items-center">
          <AssistantAvatar className="size-12 rounded-2xl ring-4 ring-background [&>svg]:size-6" />
          {catName ? (
            <CatAvatar src={catAvatarUrl} name={catName} size="sm" className="-ml-3 !size-12 ring-4 ring-background" />
          ) : null}
        </div>
        <h2 className="text-h2 font-bold leading-tight tracking-tight text-balance text-text-primary">
          {catName ? t("welcomeNamed", { name: catName }) : t("welcome")}
        </h2>
        <p className="max-w-[52ch] text-body leading-relaxed text-pretty text-text-secondary">{t("empty")}</p>
      </div>
      <div className="relative flex flex-col gap-2.5">
        <p className="text-small font-semibold text-text-secondary">{t("suggestionsTitle")}</p>
        <ul className="grid grid-cols-2 gap-2.5">
          {suggestions.map((question, index) => {
            const Icon = SUGGESTION_ICONS[index] ?? MessageCircleQuestion;
            return (
              <li key={question} className="flex">
                <button
                  type="button"
                  onClick={() => {
                    onPick(question);
                  }}
                  className="group flex min-h-28 w-full flex-col justify-between gap-3 rounded-2xl sm:min-h-16 sm:flex-row sm:items-center sm:justify-start bg-surface p-3.5 text-left shadow-sm shadow-primary/5 transition-[transform,background-color,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-md hover:shadow-primary/10 active:scale-[0.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary motion-reduce:transition-none motion-reduce:hover:translate-y-0"
                >
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary transition-colors duration-200 group-hover:bg-primary group-hover:text-white">
                    <Icon size={18} aria-hidden="true" />
                  </span>
                  <span className="text-small font-semibold leading-snug text-text-primary">{question}</span>
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}

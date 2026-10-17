import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { AlertCircle, Bot, Check, Copy, ExternalLink, RotateCcw, Sparkles } from "lucide-react";
import { cn } from "@/shared/lib/cn";
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

// Lightweight formatting for bullet/numbered lists and **bold**; never injects HTML.
export function RichText({ text }: { text: string }) {
  const blocks: ReactNode[] = [];
  let list: { ordered: boolean; items: string[] } | null = null;

  const flush = () => {
    if (!list) return;
    const key = `list-${String(blocks.length)}`;
    const items = list.items.map((item, i) => <li key={`${key}-${String(i)}`}>{renderInline(item, `${key}-${String(i)}`)}</li>);
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
        "flex size-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary to-primary-dark text-white shadow-xs",
        className,
      )}
      aria-hidden="true"
    >
      <Bot size={16} />
    </span>
  );
}

function useTimeLabel(iso: string) {
  const { i18n } = useTranslation();
  return new Date(iso).toLocaleTimeString(i18n.language || "vi", { hour: "2-digit", minute: "2-digit" });
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
          "max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-md px-4 py-2.5 text-body leading-relaxed shadow-xs",
          failed ? "bg-danger-bg text-danger-text" : "bg-primary text-white",
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
    <div className="assistant-msg-in flex items-end gap-2">
      <AssistantAvatar className="mb-6" />
      <div className="flex min-w-0 max-w-[88%] flex-col gap-1">
        <div className="rounded-2xl rounded-bl-md border border-border/60 bg-surface px-4 py-3 text-text-primary shadow-xs">
          <RichText text={text} />
          {citations.length > 0 ? (
            <div className="mt-3 border-t border-border/60 pt-2.5">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">{t("sources")}</p>
              <ul className="mt-1.5 flex flex-wrap gap-1.5">
                {citations.map((citation) => (
                  <li key={`${messageId}-${String(citation.rank)}`} className="min-w-0 max-w-full">
                    <a
                      href={citation.sourceUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex min-h-8 max-w-full items-center gap-1.5 rounded-full bg-chip-bg px-2.5 py-1 text-small font-medium text-primary-dark transition-colors hover:bg-primary hover:text-white"
                    >
                      <span className="flex size-4 shrink-0 items-center justify-center rounded-full bg-surface text-[10px] font-bold text-primary-dark">
                        {citation.rank}
                      </span>
                      <span className="truncate">{citation.title}</span>
                      <ExternalLink size={12} className="shrink-0" aria-hidden="true" />
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
        <div className="flex items-center gap-2 pl-1 text-[11px] text-text-tertiary">
          <span>{time}</span>
          <button
            type="button"
            onClick={copy}
            aria-label={copied ? t("copied") : t("copy")}
            className="inline-flex min-h-7 items-center gap-1 rounded-full px-1.5 transition-colors hover:bg-background-alt hover:text-text-primary"
          >
            {copied ? <Check size={12} aria-hidden="true" /> : <Copy size={12} aria-hidden="true" />}
            <span>{copied ? t("copied") : t("copy")}</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export function TypingIndicator() {
  const { t } = useTranslation("assistant");
  return (
    <div className="assistant-msg-in flex items-end gap-2" role="status">
      <AssistantAvatar />
      <div className="flex items-center gap-2 rounded-2xl rounded-bl-md border border-border/60 bg-surface px-4 py-3 shadow-xs">
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
    <div className="assistant-msg-in flex items-center gap-3 py-1 text-[11px] font-medium text-text-tertiary">
      <span className="h-px flex-1 bg-border" />
      <span>{label}</span>
      <span className="h-px flex-1 bg-border" />
    </div>
  );
}

export interface AssistantEmptyStateProps {
  catName?: string;
  suggestions: string[];
  onPick: (question: string) => void;
}

export function AssistantEmptyState({ catName, suggestions, onPick }: AssistantEmptyStateProps) {
  const { t } = useTranslation("assistant");
  return (
    <div className="assistant-msg-in mx-auto flex w-full max-w-md flex-col items-center gap-4 py-6 text-center">
      <AssistantAvatar className="size-14 [&>svg]:size-7" />
      <div className="flex flex-col gap-1">
        <p className="text-h3 font-bold text-text-primary">
          {catName ? t("welcomeNamed", { name: catName }) : t("welcome")}
        </p>
        <p className="text-small leading-relaxed text-text-secondary">{t("empty")}</p>
      </div>
      <div className="flex w-full flex-col gap-2">
        <p className="flex items-center justify-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-text-tertiary">
          <Sparkles size={12} aria-hidden="true" />
          {t("suggestionsTitle")}
        </p>
        {suggestions.map((question) => (
          <button
            key={question}
            type="button"
            onClick={() => {
              onPick(question);
            }}
            className="min-h-11 rounded-2xl border border-border bg-surface px-4 py-2.5 text-left text-small font-medium text-text-primary shadow-xs transition-colors hover:border-primary hover:bg-chip-bg"
          >
            {question}
          </button>
        ))}
      </div>
    </div>
  );
}

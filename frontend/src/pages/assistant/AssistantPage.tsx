import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ArrowDown, Bot, Send, Users } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api/errors";
import {
  AssistantBubble,
  AssistantEmptyState,
  ContextDivider,
  TypingIndicator,
  UserBubble,
  chatWithAssistant,
  type AiChatResponse,
} from "@/features/assistant";
import { CatAvatar } from "@/entities/cat";
import { useCatList } from "@/features/cat";

const MAX_LENGTH = 4000;
const COUNTER_FROM = 3500;
const COMPOSER_MAX_HEIGHT = 140;
const BOTTOM_THRESHOLD = 80;

type Turn =
  | { id: string; kind: "user"; text: string; sentAt: string; requestKey: string; error?: string }
  | { id: string; kind: "assistant"; response: AiChatResponse }
  | { id: string; kind: "divider"; label: string };

let turnSeq = 0;
const nextId = () => {
  turnSeq += 1;
  return `turn-${String(turnSeq)}`;
};

function prefersReducedMotion() {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function AssistantPage() {
  const { t } = useTranslation("assistant");
  const { data: catListData } = useCatList("ACTIVE");
  const catItems = catListData?.items;
  const cats = useMemo(() => catItems ?? [], [catItems]);
  const [selectedCatId, setSelectedCatId] = useState("");
  const [autoSelected, setAutoSelected] = useState(false);
  useEffect(() => {
    if (autoSelected || !catItems || catItems.length === 0) return;
    const primary = catItems.find((cat) => cat.isPrimary) ?? catItems[0];
    setSelectedCatId(primary.id);
    setAutoSelected(true);
  }, [autoSelected, catItems]);
  const selectedCat = cats.find((cat) => cat.id === selectedCatId);

  const [message, setMessage] = useState("");
  const [conversationId, setConversationId] = useState<string>();
  const [turns, setTurns] = useState<Turn[]>([]);
  const [pending, setPending] = useState(false);

  const rootRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const atBottomRef = useRef(true);
  const [showJump, setShowJump] = useState(false);

  const coarsePointer = useMemo(
    () => typeof window.matchMedia === "function" && window.matchMedia("(pointer: coarse)").matches,
    [],
  );

  const rawSuggestions: unknown = t("suggestions", { returnObjects: true });
  const suggestions = Array.isArray(rawSuggestions)
    ? rawSuggestions.filter((item): item is string => typeof item === "string")
    : [];

  const scrollToBottom = (smooth: boolean) => {
    const el = scrollRef.current;
    if (!el) return;
    if (typeof el.scrollTo === "function") {
      el.scrollTo({ top: el.scrollHeight, behavior: smooth && !prefersReducedMotion() ? "smooth" : "auto" });
    } else {
      el.scrollTop = el.scrollHeight;
    }
  };

  useEffect(() => {
    if (atBottomRef.current) scrollToBottom(true);
  }, [turns, pending]);

  // Mobile: the app header (with its collapsible shortcut row) has no fixed height, so fill the space
  // between this page's top and the room <main> keeps for the bottom nav instead of a hard-coded offset.
  // Desktop keeps the CSS height.
  useLayoutEffect(() => {
    const el = rootRef.current;
    if (!el || typeof window.matchMedia !== "function") return;
    const desktop = window.matchMedia("(min-width: 1024px)");
    const main = el.closest("main");
    const fit = () => {
      if (desktop.matches) {
        el.style.height = "";
        return;
      }
      const reserved = main ? Number.parseFloat(getComputedStyle(main).paddingBottom) || 0 : 0;
      const top = el.getBoundingClientRect().top + window.scrollY;
      el.style.height = `${String(Math.max(0, window.innerHeight - top - reserved))}px`;
    };
    fit();
    window.addEventListener("resize", fit);
    desktop.addEventListener("change", fit);
    const header = main?.parentElement?.querySelector("header");
    const observer = typeof ResizeObserver === "function" && header ? new ResizeObserver(fit) : undefined;
    if (header) observer?.observe(header);
    return () => {
      window.removeEventListener("resize", fit);
      desktop.removeEventListener("change", fit);
      observer?.disconnect();
    };
  }, []);

  useLayoutEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${String(Math.min(el.scrollHeight, COMPOSER_MAX_HEIGHT))}px`;
  }, [message]);

  const onScroll = () => {
    const el = scrollRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < BOTTOM_THRESHOLD;
    atBottomRef.current = atBottom;
    setShowJump(!atBottom);
  };

  async function send(text: string, retryTurnId?: string) {
    if (pending) return;
    let userTurnId = retryTurnId;
    // A retry reuses the question's key so an answer the server already produced is replayed, not re-asked.
    const retried = turns.find((turn) => turn.id === retryTurnId && turn.kind === "user");
    const requestKey = retried?.kind === "user" ? retried.requestKey : crypto.randomUUID();
    if (retryTurnId) {
      setTurns((current) =>
        current.map((turn) => (turn.id === retryTurnId && turn.kind === "user" ? { ...turn, error: undefined } : turn)),
      );
    } else {
      userTurnId = nextId();
      const turn: Turn = { id: userTurnId, kind: "user", text, sentAt: new Date().toISOString(), requestKey };
      setTurns((current) => [...current, turn]);
    }
    atBottomRef.current = true;
    setPending(true);
    try {
      const response = await chatWithAssistant(text, conversationId, selectedCatId || undefined, requestKey);
      setConversationId(response.conversationId);
      setTurns((current) => [...current, { id: nextId(), kind: "assistant", response }]);
    } catch (cause) {
      const detail = isApiError(cause) && cause.status < 500 && cause.code ? cause.message : t("error");
      setTurns((current) =>
        current.map((turn) => (turn.id === userTurnId && turn.kind === "user" ? { ...turn, error: detail } : turn)),
      );
    } finally {
      setPending(false);
    }
  }

  const submit = () => {
    const value = message.trim();
    if (!value || pending) return;
    setMessage("");
    void send(value);
  };

  const selectCat = (catId: string) => {
    if (catId === selectedCatId || pending) return;
    setSelectedCatId(catId);
    setConversationId(undefined);
    if (turns.length > 0) {
      const name = cats.find((cat) => cat.id === catId)?.name;
      setTurns((current) => [
        ...current,
        { id: nextId(), kind: "divider", label: name ? t("switchedTo", { name }) : t("switchedGeneral") },
      ]);
    }
  };

  const chipClass = (active: boolean) =>
    cn(
      "inline-flex min-h-9 shrink-0 items-center gap-1.5 rounded-full border py-1 pl-1 pr-3 text-small font-semibold transition-colors disabled:opacity-60",
      active
        ? "border-primary bg-primary text-white"
        : "border-border bg-surface text-text-secondary hover:border-primary/50 hover:text-text-primary",
    );

  const canSend = !pending && message.trim().length > 0;

  return (
    <div
      ref={rootRef}
      className="mx-auto flex h-[calc(100dvh-136px-env(safe-area-inset-bottom))] w-full max-w-3xl lg:min-h-[420px] flex-col overflow-hidden lg:h-[calc(100dvh-120px)] lg:rounded-3xl lg:border lg:border-border/60 lg:bg-surface lg:shadow-brand-md"
    >
      <header className="shrink-0 border-b border-border/60 bg-surface/95 px-4 pb-2.5 pt-3 lg:px-6 lg:pt-4">
        <div className="flex items-center gap-3">
          <span className="relative flex size-10 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-primary to-primary-dark text-white shadow-xs">
            <Bot size={20} aria-hidden="true" />
            <span
              className="absolute -bottom-0.5 -right-0.5 size-3 rounded-full border-2 border-surface bg-success"
              aria-hidden="true"
            />
          </span>
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-h3 font-bold text-text-primary">{t("title")}</h1>
            <p className="truncate text-small text-text-secondary">{t("description")}</p>
          </div>
        </div>

        {cats.length > 0 ? (
          <div
            role="radiogroup"
            aria-label={t("selectCat.label")}
            className="-mx-4 mt-2.5 flex gap-2 overflow-x-auto px-4 pb-0.5 [scrollbar-width:none] lg:-mx-6 lg:px-6"
          >
            {cats.map((cat) => {
              const active = cat.id === selectedCatId;
              return (
                <button
                  key={cat.id}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  disabled={pending}
                  onClick={() => {
                    selectCat(cat.id);
                  }}
                  className={chipClass(active)}
                >
                  <CatAvatar src={cat.avatarUrl} name={cat.name} size="sm" className="!size-7" />
                  <span className="max-w-[9rem] truncate">{cat.name}</span>
                </button>
              );
            })}
            <button
              type="button"
              role="radio"
              aria-checked={selectedCatId === ""}
              disabled={pending}
              onClick={() => {
                selectCat("");
              }}
              className={chipClass(selectedCatId === "")}
            >
              <span
                className="flex size-7 items-center justify-center rounded-full bg-chip-bg text-primary"
                aria-hidden="true"
              >
                <Users size={14} />
              </span>
              {t("selectCat.generalOption")}
            </button>
          </div>
        ) : null}
      </header>

      <div className="relative min-h-0 flex-1">
        <div
          ref={scrollRef}
          onScroll={onScroll}
          className="assistant-scroll flex h-full flex-col gap-4 overflow-y-auto px-4 py-4 lg:px-6"
          aria-live="polite"
          aria-busy={pending}
        >
          {turns.length === 0 ? (
            <AssistantEmptyState
              catName={selectedCat?.name}
              suggestions={suggestions}
              onPick={(question) => {
                void send(question);
              }}
            />
          ) : null}
          {turns.map((turn) => {
            if (turn.kind === "divider") return <ContextDivider key={turn.id} label={turn.label} />;
            if (turn.kind === "assistant") {
              return (
                <AssistantBubble
                  key={turn.id}
                  text={turn.response.answer}
                  createdAt={turn.response.createdAt}
                  citations={turn.response.citations}
                  messageId={turn.response.messageId}
                />
              );
            }
            return (
              <UserBubble
                key={turn.id}
                text={turn.text}
                sentAt={turn.sentAt}
                error={turn.error}
                retrying={pending}
                onRetry={() => {
                  void send(turn.text, turn.id);
                }}
              />
            );
          })}
          {pending ? <TypingIndicator /> : null}
        </div>

        {showJump ? (
          <button
            type="button"
            onClick={() => {
              atBottomRef.current = true;
              setShowJump(false);
              scrollToBottom(true);
            }}
            className="assistant-msg-in absolute bottom-3 left-1/2 inline-flex -translate-x-1/2 items-center gap-1.5 rounded-full bg-surface px-3 py-1.5 text-small font-semibold text-primary-dark shadow-brand-md"
          >
            <ArrowDown size={14} aria-hidden="true" />
            {t("jumpToLatest")}
          </button>
        ) : null}
      </div>

      <div className="shrink-0 border-t border-border/60 bg-surface/95 px-3 pb-2 pt-2.5 lg:px-6 lg:pb-4">
        <div className="flex items-end gap-2 rounded-3xl border border-border bg-background-alt p-1.5 pl-4 transition-colors focus-within:border-primary focus-within:bg-surface">
          <textarea
            ref={textareaRef}
            rows={1}
            className="max-h-[140px] min-h-10 flex-1 resize-none bg-transparent py-2 text-body leading-6 text-text-primary outline-none placeholder:text-text-tertiary"
            value={message}
            maxLength={MAX_LENGTH}
            onChange={(event) => {
              setMessage(event.target.value);
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing && !coarsePointer) {
                event.preventDefault();
                submit();
              }
            }}
            placeholder={t("placeholder")}
            aria-label={t("inputLabel")}
          />
          <button
            type="button"
            disabled={!canSend}
            onClick={submit}
            aria-label={t("send")}
            className={cn(
              "flex size-10 shrink-0 items-center justify-center rounded-full transition-[background-color,color,opacity] duration-200",
              canSend ? "bg-primary text-white shadow-xs hover:bg-primary-dark" : "bg-border/70 text-text-tertiary",
            )}
          >
            <Send size={17} aria-hidden="true" />
          </button>
        </div>
        <div className="flex items-start justify-between gap-3 px-2 pt-1.5">
          <p className="text-[11px] leading-4 text-text-tertiary">{t("disclaimer")}</p>
          {message.length >= COUNTER_FROM ? (
            <span
              className={cn(
                "shrink-0 text-[11px] tabular-nums",
                message.length >= MAX_LENGTH ? "text-danger-text" : "text-text-tertiary",
              )}
            >
              {t("charCount", { count: message.length, max: MAX_LENGTH })}
            </span>
          ) : null}
        </div>
      </div>
    </div>
  );
}

import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ArrowDown, ArrowUp, History, Plus, Users } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api/errors";
import {
  AssistantAvatar,
  AssistantBubble,
  AssistantEmptyState,
  ContextDivider,
  TypingIndicator,
  UserBubble,
  chatWithAssistant,
  fetchAiConversationMessages,
  type AiChatResponse,
  type AiMessageItem,
} from "@/features/assistant";
import { CatAvatar } from "@/entities/cat";
import { useCatList } from "@/features/cat";
import { AssistantHistoryDrawer } from "./AssistantHistoryDrawer";

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
  const [historyOpen, setHistoryOpen] = useState(false);

  const rootRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const atBottomRef = useRef(true);
  const [showJump, setShowJump] = useState(false);

  const handleSelectConversation = async (selectedId: string) => {
    if (selectedId === conversationId || pending) return;
    setPending(true);
    try {
      const messages = await fetchAiConversationMessages(selectedId);
      const restoredTurns: Turn[] = messages.map((msg: AiMessageItem) => {
        if (msg.role === "USER") {
          return {
            id: msg.id,
            kind: "user",
            text: msg.content,
            sentAt: msg.createdAt,
            requestKey: msg.id,
          };
        }
        return {
          id: msg.id,
          kind: "assistant",
          response: {
            conversationId: selectedId,
            messageId: msg.id,
            answer: msg.content,
            provider: msg.provider || "assistant",
            citations: [],
            createdAt: msg.createdAt,
          },
        };
      });
      setConversationId(selectedId);
      setTurns(restoredTurns);
      atBottomRef.current = true;
      setTimeout(() => {
        scrollToBottom(false);
      }, 50);
    } catch {
      // Fallback if loading messages fails
    } finally {
      setPending(false);
    }
  };

  const handleNewChat = () => {
    if (pending) return;
    setConversationId(undefined);
    setTurns([]);
    setMessage("");
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
    }
  };

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
    // Chưa có tin nhắn thì giữ đầu trang: empty state cao hơn màn hình nhỏ, cuộn xuống sẽ che mất lời chào.
    if (atBottomRef.current && (turns.length > 0 || pending)) scrollToBottom(true);
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
      "inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-full py-1 pl-1 pr-3 text-[13px] font-semibold transition-[background-color,color,transform] duration-200 active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary disabled:opacity-60",
      active
        ? "bg-primary text-white shadow-sm shadow-primary/25"
        : "bg-background-alt text-text-secondary hover:bg-chip-bg hover:text-text-primary",
    );

  const canSend = !pending && message.trim().length > 0;

  return (
    <div
      ref={rootRef}
      className="mx-auto flex h-[calc(100dvh-136px-env(safe-area-inset-bottom))] w-full max-w-3xl lg:min-h-[420px] flex-col overflow-hidden lg:h-[calc(100dvh-120px)] lg:rounded-3xl lg:border lg:border-border/60 lg:bg-surface lg:shadow-brand-md"
    >
      <header className="flex shrink-0 items-center gap-3 border-b border-border/50 bg-surface px-4 py-3 lg:px-6">
        <AssistantAvatar className="size-10 [&>svg]:size-5" />
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-body font-bold leading-tight text-text-primary">{t("title")}</h1>
          <p className="truncate text-small text-text-secondary">{t("description")}</p>
        </div>
        <div className="flex items-center gap-1.5">
          {turns.length > 0 && (
            <button
              type="button"
              onClick={handleNewChat}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-border/70 bg-surface px-3 text-caption font-semibold text-text-secondary transition-colors hover:bg-background-alt hover:text-text-primary focus-visible:outline-2 focus-visible:outline-primary active:scale-95"
              title={t("newChat")}
              aria-label={t("newChat")}
            >
              <Plus size={15} />
              <span className="hidden sm:inline">{t("newChat")}</span>
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              setHistoryOpen(true);
            }}
            className="inline-flex size-9 items-center justify-center rounded-full border border-border/70 bg-surface text-text-secondary transition-colors hover:bg-background-alt hover:text-text-primary focus-visible:outline-2 focus-visible:outline-primary active:scale-95 sm:w-auto sm:px-3 sm:gap-1.5"
            title={t("historyButton")}
            aria-label={t("historyButton")}
          >
            <History size={16} />
            <span className="hidden sm:inline text-caption font-semibold">{t("historyButton")}</span>
          </button>
        </div>
      </header>

      <div className="relative min-h-0 flex-1">
        <div
          ref={scrollRef}
          onScroll={onScroll}
          className="assistant-scroll flex h-full flex-col gap-5 overflow-y-auto bg-background px-4 py-5 lg:px-8"
          aria-live="polite"
          aria-busy={pending}
        >
          {turns.length === 0 ? (
            <AssistantEmptyState
              catName={selectedCat?.name}
              catAvatarUrl={selectedCat?.avatarUrl}
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
            className="assistant-msg-in absolute bottom-3 left-1/2 inline-flex min-h-9 -translate-x-1/2 items-center gap-1.5 rounded-full bg-surface px-3.5 text-small font-semibold text-primary-dark shadow-md shadow-primary/15 transition-transform active:scale-[0.97]"
          >
            <ArrowDown size={14} aria-hidden="true" />
            {t("jumpToLatest")}
          </button>
        ) : null}
      </div>

      <div className="shrink-0 bg-surface px-3 pb-2 pt-2.5 lg:px-6 lg:pb-4">
        <div className="rounded-[26px] bg-surface shadow-lg shadow-primary/10 ring-1 ring-border/70 transition-shadow duration-200 focus-within:ring-2 focus-within:ring-primary">
          {cats.length > 0 ? (
            <div
              role="radiogroup"
              aria-label={t("selectCat.label")}
              className="flex items-center gap-1.5 overflow-x-auto px-2 pt-2 [scrollbar-width:none]"
            >
              <span className="shrink-0 pl-1.5 pr-0.5 text-[12px] font-medium text-text-tertiary" aria-hidden="true">
                {t("selectCat.short")}
              </span>
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
                    <CatAvatar src={cat.avatarUrl} name={cat.name} size="sm" className="!size-6" />
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
                  className="flex size-6 items-center justify-center rounded-full bg-surface text-primary"
                  aria-hidden="true"
                >
                  <Users size={13} />
                </span>
                {t("selectCat.generalOption")}
              </button>
            </div>
          ) : null}
          <div className="flex items-end gap-2 p-1.5 pl-4">
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
                "flex size-10 shrink-0 items-center justify-center rounded-full transition-[background-color,color,transform] duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary",
                canSend
                  ? "bg-primary text-white shadow-md shadow-primary/30 hover:bg-primary-dark active:scale-95"
                  : "bg-chip-bg text-primary/45",
              )}
            >
              <ArrowUp size={19} strokeWidth={2.4} aria-hidden="true" />
            </button>
          </div>
        </div>
        <div className="flex items-start justify-between gap-3 px-2 pt-1.5">
          <p className="flex-1 text-center text-[11px] leading-4 text-pretty text-text-tertiary">{t("disclaimer")}</p>
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

      <AssistantHistoryDrawer
        open={historyOpen}
        onOpenChange={setHistoryOpen}
        activeConversationId={conversationId}
        onSelectConversation={(id) => {
          void handleSelectConversation(id);
        }}
        onNewChat={handleNewChat}
      />
    </div>
  );
}

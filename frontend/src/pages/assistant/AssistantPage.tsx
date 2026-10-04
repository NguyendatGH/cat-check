import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Bot, ExternalLink, Send } from "lucide-react";
import { Button } from "@/shared/ui";
import { chatWithAssistant, type AiChatResponse } from "@/features/assistant";

export function AssistantPage() {
  const { t } = useTranslation("assistant");
  const [message, setMessage] = useState("");
  const [conversationId, setConversationId] = useState<string>();
  const [turns, setTurns] = useState<Array<{ role: "user" | "assistant"; text: string; response?: AiChatResponse }>>(
    [],
  );
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState(false);

  async function submit() {
    const value = message.trim();
    if (!value || pending) return;
    setMessage("");
    setError(undefined);
    setTurns((current) => [...current, { role: "user", text: value }]);
    setPending(true);
    try {
      const response = await chatWithAssistant(value, conversationId);
      setConversationId(response.conversationId);
      setTurns((current) => [...current, { role: "assistant", text: response.answer, response }]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("error"));
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-5">
      <header>
        <div className="flex items-center gap-3">
          <span className="flex size-11 items-center justify-center rounded-2xl bg-primary text-white">
            <Bot size={23} aria-hidden="true" />
          </span>
          <div>
            <h1 className="text-h2 font-bold text-text-primary">{t("title")}</h1>
            <p className="text-body text-text-secondary">{t("description")}</p>
          </div>
        </div>
      </header>
      <section
        className="flex min-h-[55vh] flex-col gap-4 rounded-3xl bg-surface p-4 shadow-brand-md sm:p-6"
        aria-live="polite"
      >
        {turns.length === 0 ? (
          <p className="rounded-2xl bg-background-alt p-4 text-body text-text-secondary">{t("empty")}</p>
        ) : null}
        <div className="flex flex-col gap-4">
          {turns.map((turn, index) => (
            <div
              key={`${turn.role}-${String(index)}`}
              className={
                turn.role === "user"
                  ? "ml-auto max-w-[85%] rounded-2xl rounded-br-sm bg-primary px-4 py-3 text-white"
                  : "max-w-[92%] rounded-2xl rounded-bl-sm bg-background-alt px-4 py-3 text-text-primary"
              }
            >
              <p className="whitespace-pre-wrap text-body">{turn.text}</p>
              {turn.response && turn.response.citations.length > 0 ? (
                <div className="mt-3 border-t border-border/60 pt-2">
                  <p className="text-overline text-text-tertiary">{t("sources")}</p>
                  <ul className="mt-1 flex flex-col gap-1">
                    {turn.response.citations.map((citation) => (
                      <li key={`${turn.response?.messageId ?? ""}-${String(citation.rank)}`}>
                        <a
                          className="inline-flex items-center gap-1 text-small text-primary-dark hover:underline"
                          href={`/api/v1${citation.sourceUrl}`}
                          target="_blank"
                          rel="noreferrer"
                        >
                          {citation.rank}. {citation.title}
                          <ExternalLink size={12} aria-hidden="true" />
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}
            </div>
          ))}
        </div>
        {pending ? <p className="text-small text-text-secondary">{t("thinking")}</p> : null}
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-3 py-2 text-small text-danger-text">
            {error}
          </p>
        ) : null}
        <div className="mt-auto flex items-end gap-2 border-t border-border/60 pt-4">
          <textarea
            className="min-h-12 flex-1 resize-y rounded-2xl bg-background-alt px-4 py-3 text-body text-text-primary outline-none focus-visible:ring-2 focus-visible:ring-primary"
            value={message}
            maxLength={4000}
            onChange={(event) => {
              setMessage(event.target.value);
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                void submit();
              }
            }}
            placeholder={t("placeholder")}
            aria-label={t("inputLabel")}
          />
          <Button
            variant="primary"
            disabled={pending || message.trim().length === 0}
            onClick={() => {
              void submit();
            }}
            aria-label={t("send")}
          >
            <Send size={17} aria-hidden="true" />
          </Button>
        </div>
        <p className="text-small text-text-tertiary">{t("disclaimer")}</p>
      </section>
    </div>
  );
}

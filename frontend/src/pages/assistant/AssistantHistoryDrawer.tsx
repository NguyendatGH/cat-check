import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Clock, MessageSquare, Plus, RefreshCw, X } from "lucide-react";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from "@/shared/ui/Sheet";
import { Button } from "@/shared/ui/Button";
import { Badge } from "@/shared/ui/Badge";
import { cn } from "@/shared/lib/cn";
import { fetchAiConversations, type AiConversationSummary } from "@/features/assistant";

export interface AssistantHistoryDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  activeConversationId?: string;
  onSelectConversation: (conversationId: string) => void;
  onNewChat: () => void;
}

export function AssistantHistoryDrawer({
  open,
  onOpenChange,
  activeConversationId,
  onSelectConversation,
  onNewChat,
}: AssistantHistoryDrawerProps) {
  const { t, i18n } = useTranslation("assistant");
  const [conversations, setConversations] = useState<AiConversationSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const tRef = useRef(t);
  tRef.current = t;

  const loadData = useCallback(() => {
    setLoading(true);
    setError(null);
    void fetchAiConversations(30)
      .then((data) => {
        setConversations(data);
      })
      .catch(() => {
        setError(tRef.current("loadHistoryError"));
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    if (open) {
      loadData();
    }
  }, [open, loadData]);

  const formatDate = (dateStr: string) => {
    try {
      const d = new Date(dateStr);
      return new Intl.DateTimeFormat(i18n.language === "vi" ? "vi-VN" : "en-US", {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }).format(d);
    } catch {
      return dateStr;
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="flex h-full w-[88vw] max-w-md flex-col overflow-hidden border-l border-border bg-surface p-0 shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-border/60 px-5 py-4">
          <div className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Clock size={16} aria-hidden="true" />
            </div>
            <div>
              <SheetTitle className="text-body font-bold text-text-primary">{t("historyTitle")}</SheetTitle>
              <SheetDescription className="text-caption text-text-tertiary">
                {t("historyDescription")}
              </SheetDescription>
            </div>
          </div>
          <SheetClose asChild>
            <button
              type="button"
              className="rounded-lg p-1.5 text-text-tertiary hover:bg-background hover:text-text-primary focus-visible:outline-2 focus-visible:outline-primary"
              aria-label="Close"
            >
              <X size={18} />
            </button>
          </SheetClose>
        </div>

        <div className="border-b border-border/40 p-4">
          <Button
            type="button"
            variant="tertiary"
            className="w-full justify-center gap-2 rounded-xl border-dashed border-primary/40 font-semibold text-primary hover:bg-primary/5"
            onClick={() => {
              onNewChat();
              onOpenChange(false);
            }}
          >
            <Plus size={16} />
            {t("newChat")}
          </Button>
        </div>

        <div className="flex-1 overflow-y-auto px-4 py-3">
          {loading ? (
            <div className="flex h-40 flex-col items-center justify-center gap-2 text-text-secondary">
              <RefreshCw size={20} className="animate-spin text-primary" />
              <span className="text-caption">{t("thinking")}</span>
            </div>
          ) : error ? (
            <div className="flex h-40 flex-col items-center justify-center gap-2 px-4 text-center">
              <p className="text-caption text-error">{error}</p>
              <Button
                type="button"
                size="sm"
                variant="tertiary"
                onClick={() => {
                  loadData();
                }}
              >
                {t("retry")}
              </Button>
            </div>
          ) : conversations.length === 0 ? (
            <div className="flex h-48 flex-col items-center justify-center gap-2 px-4 text-center">
              <div className="flex size-10 items-center justify-center rounded-full bg-background-alt text-text-tertiary">
                <MessageSquare size={20} />
              </div>
              <p className="text-small font-semibold text-text-primary">{t("noHistory")}</p>
              <p className="text-caption text-text-tertiary">{t("noHistoryDesc")}</p>
            </div>
          ) : (
            <div className="space-y-2">
              {conversations.map((item) => {
                const isActive = item.id === activeConversationId;
                const title = item.preview && item.preview.trim().length > 0 ? item.preview : t("untitledConversation");

                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => {
                      onSelectConversation(item.id);
                      onOpenChange(false);
                    }}
                    className={cn(
                      "w-full rounded-2xl p-3.5 text-left transition-all duration-150 active:scale-[0.99]",
                      "border focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary",
                      isActive
                        ? "border-primary/40 bg-primary/5 shadow-sm"
                        : "border-transparent bg-background-alt/50 hover:border-border hover:bg-background-alt",
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <p
                        className={cn(
                          "line-clamp-2 text-small font-semibold leading-snug",
                          isActive ? "text-primary" : "text-text-primary",
                        )}
                      >
                        {title}
                      </p>
                      {isActive && (
                        <Badge tone="brand" className="shrink-0 text-[10px]">
                          {t("active")}
                        </Badge>
                      )}
                    </div>
                    <div className="mt-2 flex items-center gap-1.5 text-caption text-text-tertiary">
                      <Clock size={12} />
                      <span>{formatDate(item.updatedAt || item.createdAt)}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

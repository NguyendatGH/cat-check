import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Heart, ImagePlus, Loader2, Send, X } from "lucide-react";
import { useGSAP } from "@gsap/react";
import { gsap } from "gsap";
import { cn } from "@/shared/lib/cn";
import { formatNumber } from "@/shared/lib/format/formatNumber";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { Dialog, DialogContent, DialogTitle, toast } from "@/shared/ui";
import { useSessionStore } from "@/entities/user";
import {
  COMMUNITY_IMAGE,
  COMMUNITY_LIMITS,
  communityCommentErrorKey,
  useCommunityCommentLike,
  useCommunityReplies,
  useCreateCommunityComment,
  type CommunityCommentApi,
} from "@/features/community";
import { AuthorAvatar, ContentMenu, RelativeTime } from "./parts";

const MAX_IMAGE_MB = Math.round(COMMUNITY_IMAGE.maxBytes / (1024 * 1024));
const NO_REPLIES: CommunityCommentApi[] = [];

/** Who the open inline reply composer answers; replies always live under the top-level `threadId`. */
interface ReplyTarget {
  threadId: string;
  commentId: string;
  name: string;
}

function CommentImage({ src, authorName }: { src: string; authorName: string }) {
  const { t } = useTranslation("community");
  const [open, setOpen] = useState(false);
  const alt = t("detail.commentImageAlt", { name: authorName });
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setOpen(true);
        }}
        aria-label={t("detail.viewImage")}
        className="mt-2 block overflow-hidden rounded-xl border border-border/60 bg-background-alt focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
      >
        <img src={src} alt={alt} loading="lazy" className="max-h-48 w-auto max-w-full object-cover lg:max-h-60" />
      </button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-3xl p-2 pt-14 lg:p-3 lg:pt-14">
          <DialogTitle className="sr-only">{t("detail.imageViewerTitle")}</DialogTitle>
          <img src={src} alt={alt} className="max-h-[75vh] w-full rounded-xl object-contain" />
        </DialogContent>
      </Dialog>
    </>
  );
}

function useImageAttachment() {
  const { t } = useTranslation("community");
  const [file, setFile] = useState<File | null>(null);
  const previewUrl = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);

  useEffect(
    () => () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    },
    [previewUrl],
  );

  const pick = (candidate: File | undefined) => {
    if (!candidate) return;
    if (!COMMUNITY_IMAGE.types.includes(candidate.type)) {
      toast.error(t("detail.commentErrors.imageType"));
      return;
    }
    if (candidate.size > COMMUNITY_IMAGE.maxBytes) {
      toast.error(t("detail.commentErrors.imageSize", { max: MAX_IMAGE_MB }));
      return;
    }
    setFile(candidate);
  };

  return {
    file,
    previewUrl,
    pick,
    clear: () => {
      setFile(null);
    },
  };
}

function AttachButton({ onPick, disabled }: { onPick: (file: File | undefined) => void; disabled: boolean }) {
  const { t } = useTranslation("community");
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept={COMMUNITY_IMAGE.types.join(",")}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(event) => {
          onPick(event.target.files?.[0]);
          event.target.value = "";
        }}
      />
      <button
        type="button"
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
        aria-label={t("detail.attachImage")}
        title={t("detail.attachImage")}
        className="flex size-11 shrink-0 items-center justify-center rounded-full text-primary-dark hover:bg-background-alt disabled:cursor-not-allowed disabled:opacity-50"
      >
        <ImagePlus size={20} aria-hidden="true" />
      </button>
    </>
  );
}

interface ComposerProps {
  postId: string;
  /** "bottom": the post's main composer, fixed above the bottom nav on mobile. "inline": reply box under a thread. */
  variant: "bottom" | "inline";
  parentCommentId?: string;
  replyToName?: string;
  onCancel?: () => void;
  onPosted?: (comment: CommunityCommentApi) => void;
}

export function CommentComposer({ postId, variant, parentCommentId, replyToName, onCancel, onPosted }: ComposerProps) {
  const { t } = useTranslation("community");
  const displayName = useSessionStore((s) => s.user?.displayName ?? "");
  const isDesktop = useBreakpoint("lg");
  const create = useCreateCommunityComment(postId);
  const image = useImageAttachment();
  const [value, setValue] = useState("");
  const inputId = useId();
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const max = COMMUNITY_LIMITS.comment;
  const trimmed = value.trim();
  const tooLong = trimmed.length > max;
  const inline = variant === "inline";

  // Opening a reply box moves focus into it (instead of the autoFocus prop, which jsx-a11y forbids).
  useEffect(() => {
    if (inline) textareaRef.current?.focus();
  }, [inline]);
  const canSend = (trimmed.length > 0 || image.file !== null) && !tooLong && !create.isPending;

  const submit = () => {
    if (!canSend) return;
    create.mutate(
      { body: trimmed, parentCommentId, image: image.file },
      {
        onSuccess: (comment) => {
          setValue("");
          image.clear();
          onPosted?.(comment);
        },
        onError: (error) => {
          toast.error(t(`detail.commentErrors.${communityCommentErrorKey(error)}`, { max: MAX_IMAGE_MB }));
        },
      },
    );
  };

  const message = tooLong ? t("detail.commentTooLong", { max }) : null;
  const sendLabel = create.isPending
    ? t("detail.commentSending")
    : inline
      ? t("detail.replySend")
      : t("detail.commentSend");
  const placeholder = inline
    ? t("detail.replyPlaceholder")
    : isDesktop
      ? t("detail.commentPlaceholder")
      : t("detail.commentPlaceholderShort");

  const preview =
    image.file && image.previewUrl ? (
      <div className="relative mb-2 inline-block">
        <img
          src={image.previewUrl}
          alt={t("detail.imagePreviewAlt")}
          className="h-20 w-auto max-w-[160px] rounded-lg border border-border/60 object-cover"
        />
        <button
          type="button"
          onClick={image.clear}
          disabled={create.isPending}
          aria-label={t("detail.removeImage")}
          className="absolute -right-2 -top-2 flex size-7 items-center justify-center rounded-full bg-text-primary text-white shadow-brand-md disabled:opacity-50"
        >
          <X size={14} aria-hidden="true" />
        </button>
      </div>
    ) : null;

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      className={cn(
        inline
          ? "rounded-xl bg-background-alt p-2.5"
          : [
              "fixed inset-x-0 bottom-[calc(72px+env(safe-area-inset-bottom))] z-[var(--z-dropdown)] mx-auto w-full max-w-[480px] border-t border-border bg-surface/95 px-4 py-2.5 backdrop-blur-md",
              "lg:static lg:z-auto lg:mx-0 lg:max-w-none lg:rounded-2xl lg:border-0 lg:bg-background-alt lg:p-4 lg:backdrop-blur-none",
            ],
      )}
    >
      {inline && replyToName ? (
        <div className="flex items-center gap-2 pb-1.5 text-[12px] text-text-secondary">
          <span className="min-w-0 flex-1 truncate">{t("detail.replyingTo", { name: replyToName })}</span>
          {onCancel ? (
            <button
              type="button"
              onClick={onCancel}
              className="min-h-8 shrink-0 rounded-lg px-2 font-semibold text-primary-dark hover:underline"
            >
              {t("detail.replyCancel")}
            </button>
          ) : null}
        </div>
      ) : null}
      <div className={cn("flex items-end gap-1.5", !inline && "lg:items-start lg:gap-3")}>
        {!inline ? (
          <span className="hidden lg:block">
            <AuthorAvatar name={displayName} />
          </span>
        ) : null}
        <div className="min-w-0 flex-1">
          {preview}
          <label htmlFor={inputId} className="sr-only">
            {inline ? t("detail.replyLabel") : t("detail.commentLabel")}
          </label>
          <textarea
            id={inputId}
            rows={1}
            value={value}
            ref={textareaRef}
            onChange={(event) => {
              setValue(event.target.value);
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
                event.preventDefault();
                submit();
              }
              if (event.key === "Escape" && inline) onCancel?.();
            }}
            placeholder={placeholder}
            aria-invalid={tooLong ? true : undefined}
            aria-describedby={message ? `${inputId}-msg` : undefined}
            className={cn(
              "block h-11 w-full resize-none rounded-full border border-border-strong bg-background-alt px-4 py-2.5 text-[14px] leading-snug text-text-primary placeholder:text-text-tertiary",
              inline ? "bg-surface" : "lg:h-24 lg:rounded-xl lg:bg-surface lg:py-3",
              "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
              tooLong && "border-2 border-danger",
            )}
          />
          {message ? (
            <p id={`${inputId}-msg`} role="alert" className="pt-1.5 text-[12px] font-medium text-danger-text">
              {message}
            </p>
          ) : null}
          {!inline ? (
            <div className="hidden items-center justify-end gap-2 pt-3 lg:flex">
              <AttachButton onPick={image.pick} disabled={create.isPending} />
              <button
                type="submit"
                disabled={!canSend}
                className="flex min-h-11 items-center gap-2 rounded-xl bg-primary-dark px-5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Send size={15} aria-hidden="true" />
                {sendLabel}
              </button>
            </div>
          ) : null}
        </div>
        <span className={cn("flex items-end gap-1", !inline && "lg:hidden")}>
          <AttachButton onPick={image.pick} disabled={create.isPending} />
          <button
            type="submit"
            disabled={!canSend}
            aria-label={sendLabel}
            className="flex size-11 shrink-0 items-center justify-center rounded-full bg-primary-dark text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
          >
            {create.isPending ? (
              <Loader2 size={17} className="animate-spin" aria-hidden="true" />
            ) : (
              <Send size={17} aria-hidden="true" />
            )}
          </button>
        </span>
      </div>
    </form>
  );
}

function CommentLikeButton({ comment }: { comment: CommunityCommentApi }) {
  const { t } = useTranslation("community");
  const like = useCommunityCommentLike(comment.postId);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const { contextSafe } = useGSAP({ scope: buttonRef });
  // Optimistic state until the refetched comment arrives.
  const [pending, setPending] = useState<{ liked: boolean; count: number } | null>(null);
  const liked = pending?.liked ?? comment.likedByCurrentUser ?? false;
  const count = pending?.count ?? comment.likeCount ?? 0;

  const pop = contextSafe(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    gsap.fromTo(
      "[data-like-heart]",
      { scale: 0.55, rotation: -18 },
      { scale: 1, rotation: 0, duration: 0.55, ease: "back.out(3)", overwrite: "auto" },
    );
  });

  return (
    <button
      ref={buttonRef}
      type="button"
      aria-pressed={liked}
      aria-label={t("detail.likeComment")}
      onClick={() => {
        const next = !liked;
        setPending({ liked: next, count: Math.max(0, count + (next ? 1 : -1)) });
        if (next) pop();
        like.mutate(
          { commentId: comment.id, active: next },
          {
            onError: () => {
              toast.error(t("post.reactionError"));
            },
            onSettled: () => {
              setPending(null);
            },
          },
        );
      }}
      className={cn(
        "flex min-h-9 items-center gap-1 rounded-lg px-2 text-[12px] font-semibold transition-colors",
        liked ? "text-danger" : "text-text-secondary hover:text-danger",
      )}
    >
      <Heart data-like-heart size={15} fill={liked ? "currentColor" : "none"} aria-hidden="true" />
      {count > 0 ? <span className="tabular-nums">{formatNumber(count)}</span> : null}
    </button>
  );
}

function CommentBody({
  comment,
  compact,
  onReply,
}: {
  comment: CommunityCommentApi;
  compact?: boolean;
  onReply: () => void;
}) {
  const { t } = useTranslation("community");
  return (
    <div className="flex gap-3">
      <AuthorAvatar name={comment.authorName} size="sm" />
      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-2">
          <p className="min-w-0 flex-1">
            <span className="block truncate text-[14px] font-bold leading-tight text-text-primary">
              {comment.authorName}
            </span>
            <span className="block truncate pt-0.5 text-[12px] text-text-tertiary">
              <RelativeTime value={comment.createdAt} />
              {comment.replyToName ? (
                <>
                  {" · "}
                  {t("detail.replyTo", { name: comment.replyToName })}
                </>
              ) : null}
            </span>
          </p>
          <ContentMenu target={{ commentId: comment.id }} label={t("post.more")} />
        </div>
        {comment.body ? (
          <p
            className={cn(
              "whitespace-pre-wrap break-words pt-2 leading-relaxed text-text-primary",
              compact ? "text-[14px]" : "text-[14px] lg:text-[15px]",
            )}
          >
            {comment.body}
          </p>
        ) : null}
        {comment.imageUrl ? <CommentImage src={comment.imageUrl} authorName={comment.authorName} /> : null}
        <div className="-ml-2 mt-1 flex items-center gap-1">
          <CommentLikeButton comment={comment} />
          <button
            type="button"
            onClick={onReply}
            className="min-h-9 rounded-lg px-2 text-[12px] font-semibold text-text-secondary hover:text-primary-dark"
          >
            {t("detail.reply")}
          </button>
        </div>
      </div>
    </div>
  );
}

function byCreatedAt(a: CommunityCommentApi, b: CommunityCommentApi): number {
  return a.createdAt === b.createdAt ? a.id.localeCompare(b.id) : a.createdAt.localeCompare(b.createdAt);
}

function CommentThread({
  postId,
  comment,
  replyTarget,
  onReplyTargetChange,
}: {
  postId: string;
  comment: CommunityCommentApi;
  replyTarget: ReplyTarget | null;
  onReplyTargetChange: (target: ReplyTarget | null) => void;
}) {
  const { t } = useTranslation("community");
  const preview = comment.replies ?? NO_REPLIES;
  const [expanded, setExpanded] = useState(false);
  const [mine, setMine] = useState<CommunityCommentApi[]>([]);
  const more = useCommunityReplies(postId, comment.id, preview.length, expanded);

  const loaded = useMemo(
    () => (expanded ? (more.data?.pages.flatMap((page) => page.items) ?? []) : []),
    [expanded, more.data],
  );
  const replies = useMemo(() => {
    const seen = new Map<string, CommunityCommentApi>();
    for (const item of [...preview, ...loaded, ...mine]) seen.set(item.id, item);
    return [...seen.values()].sort(byCreatedAt);
  }, [preview, loaded, mine]);
  const hiddenCount = Math.max(0, (comment.replyCount ?? 0) - replies.length);
  const canLoadMore = hiddenCount > 0 && (!expanded || more.hasNextPage || more.isPending);
  const replying = replyTarget?.threadId === comment.id;

  useEffect(() => {
    if (more.isError) toast.error(t("detail.repliesError"));
  }, [more.isError, t]);

  const reply = (target: CommunityCommentApi) => {
    onReplyTargetChange({ threadId: comment.id, commentId: target.id, name: target.authorName });
  };

  const loadMore = () => {
    if (!expanded) {
      setExpanded(true);
      return;
    }
    if (more.isError) {
      void more.refetch();
      return;
    }
    if (more.hasNextPage && !more.isFetchingNextPage) void more.fetchNextPage();
  };

  const busy = expanded && (more.isPending || more.isFetchingNextPage) && !more.isError;

  return (
    <li className="border-t border-border/60 pt-4 first:border-t-0 first:pt-0">
      <CommentBody
        comment={comment}
        onReply={() => {
          reply(comment);
        }}
      />
      {replies.length > 0 || canLoadMore || replying ? (
        <div className="ml-6 mt-2 border-l-2 border-border/60 pl-3 lg:ml-12 lg:pl-4">
          {replies.length > 0 ? (
            <ul className="flex flex-col gap-3" aria-label={t("detail.repliesLabel", { name: comment.authorName })}>
              {replies.map((item) => (
                <li key={item.id}>
                  <CommentBody
                    comment={item}
                    compact
                    onReply={() => {
                      reply(item);
                    }}
                  />
                </li>
              ))}
            </ul>
          ) : null}
          {canLoadMore || (expanded && loaded.length > 0) ? (
            <div className="flex flex-wrap items-center gap-x-4 pt-1">
              {canLoadMore ? (
                <button
                  type="button"
                  onClick={loadMore}
                  disabled={busy}
                  aria-expanded={expanded}
                  className="flex min-h-10 items-center gap-1.5 text-[13px] font-semibold text-primary-dark hover:underline disabled:opacity-60"
                >
                  {busy ? <Loader2 size={14} className="animate-spin" aria-hidden="true" /> : null}
                  {busy
                    ? t("detail.loadingReplies")
                    : t("detail.showMoreReplies", { count: hiddenCount, formattedCount: formatNumber(hiddenCount) })}
                </button>
              ) : null}
              {expanded && loaded.length > 0 ? (
                <button
                  type="button"
                  onClick={() => {
                    setExpanded(false);
                  }}
                  className="min-h-10 text-[13px] font-semibold text-text-secondary hover:underline"
                >
                  {t("detail.hideReplies")}
                </button>
              ) : null}
            </div>
          ) : null}
          {replying ? (
            <div className="pt-2">
              <CommentComposer
                key={replyTarget.commentId}
                postId={postId}
                variant="inline"
                parentCommentId={replyTarget.commentId}
                replyToName={replyTarget.name}
                onCancel={() => {
                  onReplyTargetChange(null);
                }}
                onPosted={(created) => {
                  setMine((list) => [...list, created]);
                  onReplyTargetChange(null);
                }}
              />
            </div>
          ) : null}
        </div>
      ) : null}
    </li>
  );
}

export function CommentsSection({
  postId,
  total,
  comments,
}: {
  postId: string;
  total: number;
  comments: CommunityCommentApi[];
}) {
  const { t } = useTranslation("community");
  const [replyTarget, setReplyTarget] = useState<ReplyTarget | null>(null);
  return (
    <section className="rounded-2xl bg-surface p-4 shadow-brand-md lg:p-7" aria-labelledby="community-comments-title">
      <div className="flex flex-wrap items-center gap-2.5">
        <h2 id="community-comments-title" className="text-[17px] font-bold text-text-primary lg:text-[20px]">
          {t("detail.commentsTitle")}
        </h2>
        <span className="rounded-full bg-chip-bg px-2.5 py-0.5 text-[12px] font-bold text-primary-dark">
          {t("detail.commentCount", { count: total, formattedCount: formatNumber(total) })}
        </span>
      </div>

      <div className="lg:pt-5">
        <CommentComposer postId={postId} variant="bottom" />
      </div>

      {comments.length > 0 ? (
        <ul className="flex flex-col gap-4 pt-4 lg:pt-6">
          {comments.map((item) => (
            <CommentThread
              key={item.id}
              postId={postId}
              comment={item}
              replyTarget={replyTarget}
              onReplyTargetChange={setReplyTarget}
            />
          ))}
        </ul>
      ) : (
        <p className="pt-3 text-[13px] leading-relaxed text-text-secondary lg:pt-5">{t("detail.noComments")}</p>
      )}
    </section>
  );
}

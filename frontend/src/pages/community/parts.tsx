import { useId, useState } from "react";
import * as Popover from "@radix-ui/react-popover";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Bookmark, Check, Flag, Heart, MessageSquare, MoreHorizontal, Share2, ShieldCheck } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { formatDate } from "@/shared/lib/format/formatDate";
import { formatRelative } from "@/shared/lib/format/formatRelative";
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle, SkeletonLoader, toast } from "@/shared/ui";
import {
  COMMUNITY_LIMITS,
  COMMUNITY_REPORT_REASONS,
  communityReportErrorKey,
  useCommunityReaction,
  useReportCommunityContent,
  type CommunityPostApi,
  type CommunityReportReason,
} from "@/features/community";

/**
 * Mảnh giao diện dùng chung cho 3 trang Cộng đồng. MỌI dữ liệu hiển thị đều là field của
 * `CommunityPostResponse` / `CommunityCommentResponse` (backend `community/api/dto`) — không
 * có huy hiệu "đã xác minh", tên bé mèo, ý kiến bác sĩ hay số liệu cộng đồng như trong design
 * (API không trả những field đó).
 */

/** Ba quy tắc trung tính — cột phải bảng tin, chi tiết bài và trang soạn bài. */
export const RULE_KEYS = ["respect", "noDiagnosis", "privacy"] as const;

/** Cặp màu nền/chữ của avatar chữ cái — chọn ổn định theo tên, chỉ để phân biệt người viết. */
const AVATAR_TONES = [
  "bg-chip-bg text-primary-dark",
  "bg-secondary-light text-secondary-text-on",
  "bg-success-bg text-success-text",
  "bg-deco-backdrop text-primary",
] as const;

function toneFor(name: string): string {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + (char.codePointAt(0) ?? 0)) >>> 0;
  return AVATAR_TONES[hash % AVATAR_TONES.length] ?? AVATAR_TONES[0];
}

export function AuthorAvatar({ name, size = "md" }: { name: string; size?: "sm" | "md" | "lg" }) {
  const initial = (name.trim() || "?").slice(0, 1).toUpperCase();
  return (
    <span
      aria-hidden="true"
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full font-bold ring-2 ring-surface",
        toneFor(name),
        size === "sm" && "size-9 text-[14px]",
        size === "md" && "size-10 text-[15px] lg:size-11",
        size === "lg" && "size-11 text-[16px] lg:size-12 lg:text-[17px]",
      )}
    >
      {initial}
    </span>
  );
}

export function CategoryBadge({ category }: { category: string }) {
  const { t } = useTranslation("community");
  return (
    <span className="inline-flex shrink-0 items-center whitespace-nowrap rounded-full bg-chip-bg px-2.5 py-0.5 text-[11px] font-semibold text-primary-dark">
      {t(`category.${category}`, { defaultValue: category })}
    </span>
  );
}

/** Thời điểm tương đối, kèm ngày giờ tuyệt đối ở `title`/`dateTime` cho người cần chính xác. */
export function RelativeTime({ value, prefix }: { value: string; prefix?: boolean }) {
  const { t } = useTranslation("community");
  const relative = formatRelative(value);
  return (
    <time dateTime={value} title={formatDate(value, "HH:mm dd/MM/yyyy")}>
      {prefix ? t("post.postedAt", { time: relative }) : relative}
    </time>
  );
}

/**
 * Tên tác giả + chuyên mục + thời điểm. Desktop: tên và nhãn chuyên mục cùng dòng (design web).
 * Mobile: nhãn xuống dòng 2 cạnh giờ (design mobile) để tên dài không đẩy nhãn vỡ dòng.
 */
export function AuthorMeta({
  name,
  category,
  createdAt,
  large = false,
}: {
  name: string;
  category: string;
  createdAt: string;
  large?: boolean;
}) {
  return (
    <div className="min-w-0 flex-1">
      <div className="flex min-w-0 items-center gap-2">
        <p
          className={cn(
            "min-w-0 truncate font-bold leading-tight text-text-primary",
            large ? "text-[16px] lg:text-[17px]" : "text-[15px]",
          )}
        >
          {name}
        </p>
        <span className="hidden shrink-0 lg:inline-flex">
          <CategoryBadge category={category} />
        </span>
      </div>
      <p
        className={cn(
          "flex flex-wrap items-center gap-x-2 gap-y-1 pt-1 text-[12px] text-text-tertiary",
          large && "lg:text-[13px]",
        )}
      >
        <span className="inline-flex lg:hidden">
          <CategoryBadge category={category} />
        </span>
        <RelativeTime value={createdAt} prefix={large} />
      </p>
    </div>
  );
}

function tagLabel(tag: string): string {
  return tag.startsWith("#") ? tag : "#" + tag;
}

export function TagChips({ tags, className }: { tags: string[]; className?: string }) {
  if (tags.length === 0) return null;
  return (
    <ul className={cn("flex flex-wrap gap-2", className)}>
      {tags.map((tag) => (
        <li
          key={tag}
          className="max-w-full truncate rounded-lg bg-chip-bg px-2.5 py-1 text-[11px] font-semibold text-primary-dark"
        >
          {tagLabel(tag)}
        </li>
      ))}
    </ul>
  );
}

/** Khối quy tắc trung tính + disclaimer. Không chứng thực tổ chức nào. */
export function RulesCard({
  className,
  showDisclaimer = true,
}: {
  className?: string;
  /** Tắt khi trang đã hiện disclaimer ở chỗ khác (vd dải lưu ý đầu bảng tin mobile). */
  showDisclaimer?: boolean;
}) {
  const { t } = useTranslation("community");
  return (
    <section className={cn("rounded-2xl bg-surface p-5 shadow-brand-md", className)}>
      <h2 className="flex items-center gap-2 text-[15px] font-bold text-primary-dark">
        <ShieldCheck size={16} className="text-primary" aria-hidden="true" />
        {t("rules.title")}
      </h2>
      <p className="pt-2 text-[12px] leading-relaxed text-text-secondary">{t("rules.intro")}</p>
      <ul className="flex flex-col gap-2.5 pt-3">
        {RULE_KEYS.map((key) => (
          <li key={key} className="flex gap-2 text-pretty text-[12px] leading-relaxed text-text-secondary">
            <Check size={14} className="mt-0.5 shrink-0 text-success-text" aria-hidden="true" />
            {t(`rules.${key}`)}
          </li>
        ))}
      </ul>
      {showDisclaimer ? (
        <p className="mt-4 text-pretty rounded-xl bg-background-alt p-3 text-[11px] leading-relaxed text-text-tertiary">
          {t("disclaimer")}
        </p>
      ) : null}
    </section>
  );
}

/* ------------------------------ Hành động trên bài ------------------------------ */

/** Chia sẻ liên kết bài: Web Share API nếu có (mobile), không thì chép vào clipboard. */
export function useSharePost() {
  const { t } = useTranslation("community");
  return async (post: Pick<CommunityPostApi, "id" | "title">) => {
    const url = `${window.location.origin}/community/posts/${post.id}`;
    try {
      if (typeof navigator.share === "function") {
        await navigator.share({ title: post.title, url });
        return;
      }
      await navigator.clipboard.writeText(url);
      toast.success(t("post.shareCopied"));
    } catch (error) {
      // Người dùng tự đóng bảng chia sẻ ⇒ AbortError, không phải lỗi.
      if (error instanceof DOMException && error.name === "AbortError") return;
      toast.error(t("post.shareFailed"));
    }
  };
}

/** Nút thích / lưu — dùng chung bảng tin và chi tiết. Trạng thái thật từ `liked`/`bookmarked`. */
export function PostActions({
  post,
  commentHref,
  secondaryClassName,
  className,
}: {
  post: CommunityPostApi;
  /** Có ⇒ số bình luận là liên kết tới chi tiết (bảng tin); không ⇒ chỉ hiển thị số. */
  commentHref?: string;
  /** Class thêm cho 2 nút phụ Chia sẻ + Lưu (vd `lg:hidden` khi trang đã có bản khác). */
  secondaryClassName?: string;
  className?: string;
}) {
  const { t } = useTranslation("community");
  const reaction = useCommunityReaction();
  const share = useSharePost();

  const toggle = (kind: "LIKE" | "BOOKMARK") => {
    reaction.mutate(
      { postId: post.id, reaction: kind, active: kind === "LIKE" ? !post.liked : !post.bookmarked },
      {
        onError: () => {
          toast.error(t("post.reactionError"));
        },
      },
    );
  };

  // Nhãn chữ chỉ hiện từ `md` (768px): design mobile chỉ có icon + số. KHÔNG dùng `sm` — token
  // `--breakpoint-sm` của dự án là 375px nên `sm:` vẫn bật ở khung 390px.
  const actionClass =
    "flex min-h-11 items-center gap-1.5 whitespace-nowrap rounded-lg px-2 text-[13px] font-semibold text-text-secondary transition-colors hover:bg-background-alt hover:text-primary-dark";

  const commentContent = (
    <>
      <MessageSquare size={17} aria-hidden="true" />
      <span>{post.commentCount}</span>
      <span className="hidden md:inline">{t("post.comment")}</span>
    </>
  );

  // Viền trên nằm ở hộp ngoài; hàng nút bên trong lùi -mx-2 để vùng chạm rộng mà chữ vẫn
  // thẳng mép nội dung thẻ (đặt -mx-2 ở hộp có viền thì viền lòi ra ngoài lề).
  return (
    <div className={className}>
      <div className="-mx-2 flex items-center gap-2 md:gap-3">
        <button
          type="button"
          onClick={() => {
            toggle("LIKE");
          }}
          aria-pressed={post.liked}
          aria-label={t("post.like")}
          className={cn(actionClass, post.liked && "text-danger hover:text-danger")}
        >
          <Heart size={17} fill={post.liked ? "currentColor" : "none"} aria-hidden="true" />
          <span>{post.likeCount}</span>
          <span className="hidden md:inline">{t("post.like")}</span>
        </button>
        {commentHref ? (
          <Link
            to={commentHref}
            className={actionClass}
            aria-label={`${String(post.commentCount)} ${t("post.comment")}`}
          >
            {commentContent}
          </Link>
        ) : (
          <span className={cn(actionClass, "hover:bg-transparent hover:text-text-secondary")}>
            {commentContent}
            <span className="sr-only md:hidden">{t("post.comment")}</span>
          </span>
        )}
        <button
          type="button"
          onClick={() => {
            void share(post);
          }}
          aria-label={t("post.share")}
          className={cn(actionClass, secondaryClassName)}
        >
          <Share2 size={17} aria-hidden="true" />
          <span className="hidden md:inline">{t("post.share")}</span>
        </button>
        <button
          type="button"
          onClick={() => {
            toggle("BOOKMARK");
          }}
          aria-pressed={post.bookmarked}
          aria-label={t("post.bookmarkLabel")}
          className={cn(actionClass, "ml-auto", post.bookmarked && "text-primary-dark", secondaryClassName)}
        >
          <Bookmark size={17} fill={post.bookmarked ? "currentColor" : "none"} aria-hidden="true" />
          <span className="hidden md:inline">{post.bookmarked ? t("post.bookmarked") : t("post.bookmark")}</span>
        </button>
      </div>
    </div>
  );
}

/* ------------------------------ Menu ⋯ + báo cáo ------------------------------ */

export type ReportTarget = { postId: string; commentId?: undefined } | { commentId: string; postId?: undefined };

/** Nút ⋯ ở góc thẻ bài / bình luận — hiện tại có đúng một hành động thật: báo cáo vi phạm. */
export function ContentMenu({ target, label }: { target: ReportTarget; label?: string }) {
  const { t } = useTranslation("community");
  const [menuOpen, setMenuOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);

  return (
    <>
      <Popover.Root open={menuOpen} onOpenChange={setMenuOpen}>
        <Popover.Trigger
          aria-label={label ?? t("post.more")}
          className="-mr-2 -mt-1 flex size-11 shrink-0 items-center justify-center rounded-full text-text-tertiary hover:bg-background-alt hover:text-text-primary"
        >
          <MoreHorizontal size={18} aria-hidden="true" />
        </Popover.Trigger>
        <Popover.Portal>
          <Popover.Content
            align="end"
            sideOffset={4}
            className="z-[var(--z-dropdown)] min-w-[200px] rounded-xl border border-border bg-surface p-1.5 shadow-brand-lg"
          >
            <button
              type="button"
              onClick={() => {
                setMenuOpen(false);
                setReportOpen(true);
              }}
              className="flex min-h-11 w-full items-center gap-2.5 rounded-lg px-3 text-left text-[13px] font-semibold text-danger-text hover:bg-danger-bg"
            >
              <Flag size={15} aria-hidden="true" />
              {t("report.action")}
            </button>
          </Popover.Content>
        </Popover.Portal>
      </Popover.Root>
      <ReportDialog target={target} open={reportOpen} onOpenChange={setReportOpen} />
    </>
  );
}

function ReportDialog({
  target,
  open,
  onOpenChange,
}: {
  target: ReportTarget;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation("community");
  const report = useReportCommunityContent();
  const [reason, setReason] = useState<CommunityReportReason | null>(null);
  const [details, setDetails] = useState("");
  const [showErrors, setShowErrors] = useState(false);
  const detailsId = useId();
  const max = COMMUNITY_LIMITS.reportDetails;

  const trimmed = details.trim();
  const reasonError = reason === null ? t("report.reasonMissing") : null;
  const detailsError =
    trimmed.length > max
      ? t("report.detailsTooLong", { max })
      : reason === "OTHER" && trimmed.length === 0
        ? t("report.detailsMissing")
        : null;

  const reset = () => {
    setReason(null);
    setDetails("");
    setShowErrors(false);
    report.reset();
  };

  const submit = () => {
    setShowErrors(true);
    if (reasonError || detailsError || reason === null) return;
    report.mutate(
      { ...target, reason, details: trimmed || undefined },
      {
        onSuccess: () => {
          toast.success(t("report.success"));
          onOpenChange(false);
          reset();
        },
      },
    );
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next);
        if (!next) reset();
      }}
    >
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto">
        <DialogTitle className="pr-10 text-[18px] font-bold">
          {target.commentId ? t("report.titleComment") : t("report.titlePost")}
        </DialogTitle>
        <DialogDescription className="pt-1.5 text-[13px]">{t("report.description")}</DialogDescription>
        <form
          noValidate
          className="flex flex-col gap-4 pt-4"
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <fieldset
            className="min-w-0"
            aria-describedby={showErrors && reasonError ? `${detailsId}-reason` : undefined}
          >
            <legend className="text-[13px] font-bold text-text-primary">{t("report.reasonLabel")}</legend>
            <div className="flex flex-col gap-1.5 pt-2">
              {COMMUNITY_REPORT_REASONS.map((item) => (
                <label
                  key={item}
                  className={cn(
                    "flex min-h-11 cursor-pointer items-center gap-3 rounded-xl border px-3 text-[13px] text-text-primary transition-colors",
                    reason === item
                      ? "border-primary bg-chip-bg font-semibold"
                      : "border-border hover:bg-background-alt",
                  )}
                >
                  <input
                    type="radio"
                    name="report-reason"
                    value={item}
                    checked={reason === item}
                    onChange={() => {
                      setReason(item);
                    }}
                    className="size-4 accent-primary-dark"
                  />
                  {t(`report.reasons.${item}`)}
                </label>
              ))}
            </div>
            {showErrors && reasonError ? (
              <p id={`${detailsId}-reason`} className="pt-1.5 text-[12px] font-medium text-danger-text">
                {reasonError}
              </p>
            ) : null}
          </fieldset>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor={detailsId}
              className="flex items-baseline justify-between gap-2 text-[13px] font-bold text-text-primary"
            >
              {t("report.detailsLabel")}
              <span className="text-[11px] font-medium text-text-tertiary">
                {reason === "OTHER" ? t("report.detailsRequired") : t("report.detailsOptional")}
              </span>
            </label>
            <textarea
              id={detailsId}
              rows={3}
              value={details}
              onChange={(event) => {
                setDetails(event.target.value);
              }}
              placeholder={t("report.detailsPlaceholder")}
              aria-invalid={showErrors && detailsError ? true : undefined}
              aria-describedby={showErrors && detailsError ? `${detailsId}-error` : undefined}
              className={cn(
                "w-full resize-none rounded-xl border border-border-strong bg-surface px-3.5 py-2.5 text-[13px] leading-relaxed text-text-primary placeholder:text-text-tertiary",
                "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
                showErrors && detailsError && "border-2 border-danger",
              )}
            />
            <div className="flex items-start justify-between gap-3 text-[11px]">
              <span id={`${detailsId}-error`} className="font-medium text-danger-text">
                {showErrors ? detailsError : null}
              </span>
              <span className={cn("shrink-0 text-text-tertiary", trimmed.length > max && "text-danger-text")}>
                {t("newPost.counter", { count: trimmed.length, max })}
              </span>
            </div>
          </div>

          {report.isError ? (
            <p role="alert" className="rounded-xl bg-danger-bg px-3 py-2.5 text-[12px] font-medium text-danger-text">
              {t(`report.errors.${communityReportErrorKey(report.error)}`)}
            </p>
          ) : null}

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="tertiary"
              onClick={() => {
                onOpenChange(false);
                reset();
              }}
            >
              {t("report.cancel")}
            </Button>
            <Button type="submit" loading={report.isPending}>
              {report.isPending ? t("report.submitting") : t("report.submit")}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/* ------------------------------ Khung chờ tải ------------------------------ */

export function PostCardSkeleton() {
  return (
    <div className="rounded-2xl bg-surface p-4 shadow-brand-md lg:p-6" aria-hidden="true">
      <div className="flex items-center gap-3">
        <SkeletonLoader shape="circle" className="lg:size-11" />
        <div className="flex flex-1 flex-col gap-2">
          <SkeletonLoader className="h-3.5 w-40" />
          <SkeletonLoader className="h-3 w-24" />
        </div>
      </div>
      <SkeletonLoader className="mt-4 h-5 w-3/4" />
      <SkeletonLoader className="mt-3 h-3.5" />
      <SkeletonLoader className="mt-2 h-3.5 w-5/6" />
      <SkeletonLoader className="mt-5 h-8 w-1/2" />
    </div>
  );
}

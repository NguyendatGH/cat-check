import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { ArrowLeft, Bookmark, ChevronRight, FileQuestion, Send, Share2 } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { formatNumber } from "@/shared/lib/format/formatNumber";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { isApiError } from "@/shared/api/errors";
import { EmptyState, ErrorState, SkeletonLoader, toast } from "@/shared/ui";
import { useSessionStore } from "@/entities/user";
import {
  COMMUNITY_LIMITS,
  useCommunityPost,
  useCommunityReaction,
  useCreateCommunityComment,
  type CommunityCommentApi,
  type CommunityPostApi,
} from "@/features/community";
import {
  AuthorAvatar,
  AuthorMeta,
  ContentMenu,
  PostActions,
  PostCardSkeleton,
  RelativeTime,
  RulesCard,
  TagChips,
  useSharePost,
} from "./parts";

/**
 * `/community/posts/:postId` — chi tiết bài viết (design `Web - Chi tiết Thảo luận…` + mobile
 * `Chi tiết Thảo luận Cộng đồng`).
 *
 * NGUỒN DỮ LIỆU: 100% `GET /api/v1/community/posts/{id}` (bài + bình luận); thích/lưu qua
 * `POST …/reactions`, bình luận qua `POST …/comments`, báo cáo qua `POST /community/reports`.
 * Đã BỎ so với design vì API không có: thẻ bác sĩ + chỉ số tư vấn, hồ sơ ca bệnh của bé, "ca
 * tương tự", hotline cấp cứu, khối "phân tích AI"/pH gắn trong bài, ý kiến chuyên môn ghim,
 * huy hiệu người dùng, thích/trả lời từng bình luận, sắp xếp bình luận. Cột phải thay bằng quy
 * tắc cộng đồng trung tính.
 *
 * `AppLayout` không cấp padding ngang dưới `lg` nên trang tự thêm `px-4`.
 */
export function CommunityPostDetailPage() {
  const { postId } = useParams<{ postId: string }>();
  if (!postId) return <NotFound />;
  return <PostDetail postId={postId} />;
}

function BackLink() {
  const { t } = useTranslation("community");
  return (
    <Link
      to="/community"
      className="flex min-h-11 items-center gap-2 self-start text-[14px] font-semibold text-primary-dark hover:underline"
    >
      <ArrowLeft size={18} aria-hidden="true" />
      {t("detail.backToFeed")}
    </Link>
  );
}

function NotFound() {
  const { t } = useTranslation("community");
  return (
    <div className="flex flex-col gap-4 px-4 py-4 lg:p-0">
      <BackLink />
      <EmptyState
        icon={<FileQuestion size={22} />}
        title={t("detail.notFoundTitle")}
        description={t("detail.notFoundBody")}
        className="rounded-2xl bg-surface shadow-brand-md"
      />
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-4 px-4 py-4 lg:p-0" role="status" aria-busy="true">
      <SkeletonLoader className="h-5 w-48" />
      <div className="flex items-start gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          <PostCardSkeleton />
          <PostCardSkeleton />
        </div>
        <SkeletonLoader shape="card" className="hidden h-72 w-[296px] shrink-0 rounded-2xl lg:block" />
      </div>
    </div>
  );
}

/** Breadcrumb + Lưu/Chia sẻ ở đầu trang (desktop), nút quay lại (mobile). */
function DetailTopBar({ post }: { post: CommunityPostApi }) {
  const { t } = useTranslation("community");
  const reaction = useCommunityReaction();
  const share = useSharePost();

  return (
    <div className="flex items-center gap-4">
      <div className="lg:hidden">
        <BackLink />
      </div>
      <nav aria-label={t("detail.breadcrumb")} className="hidden min-w-0 flex-1 lg:block">
        <ol className="flex min-w-0 items-center gap-1.5 text-[13px] text-text-secondary">
          <li className="shrink-0">
            <Link to="/community" className="font-semibold hover:text-primary-dark hover:underline">
              {t("feed.title")}
            </Link>
          </li>
          <ChevronRight size={14} className="shrink-0 text-text-tertiary" aria-hidden="true" />
          <li className="shrink-0">{t(`category.${post.category}`, { defaultValue: post.category })}</li>
          <ChevronRight size={14} className="shrink-0 text-text-tertiary" aria-hidden="true" />
          <li
            className="min-w-0 truncate rounded-md bg-chip-bg px-2 py-0.5 font-semibold text-primary-dark"
            aria-current="page"
          >
            {post.title}
          </li>
        </ol>
      </nav>
      <div className="ml-auto hidden shrink-0 items-center gap-2 lg:flex">
        <button
          type="button"
          onClick={() => {
            reaction.mutate(
              { postId: post.id, reaction: "BOOKMARK", active: !post.bookmarked },
              {
                onError: () => {
                  toast.error(t("post.reactionError"));
                },
              },
            );
          }}
          aria-pressed={post.bookmarked}
          className={cn(
            "flex min-h-11 items-center gap-2 rounded-xl bg-surface px-4 text-[13px] font-semibold shadow-xs hover:bg-background-alt",
            post.bookmarked ? "text-primary-dark" : "text-text-primary",
          )}
        >
          <Bookmark size={16} fill={post.bookmarked ? "currentColor" : "none"} aria-hidden="true" />
          {post.bookmarked ? t("post.bookmarked") : t("post.bookmarkLabel")}
        </button>
        <button
          type="button"
          onClick={() => {
            void share(post);
          }}
          className="flex min-h-11 items-center gap-2 rounded-xl bg-surface px-4 text-[13px] font-semibold text-text-primary shadow-xs hover:bg-background-alt"
        >
          <Share2 size={16} aria-hidden="true" />
          {t("post.share")}
        </button>
      </div>
    </div>
  );
}

function PostArticle({ post }: { post: CommunityPostApi }) {
  const { t } = useTranslation("community");
  return (
    <article className="rounded-2xl bg-surface p-4 shadow-brand-md lg:p-7">
      <header className="flex items-start gap-3">
        <AuthorAvatar name={post.authorName} size="lg" />
        <AuthorMeta name={post.authorName} category={post.category} createdAt={post.createdAt} large />
        <ContentMenu target={{ postId: post.id }} label={t("post.more")} />
      </header>

      <TagChips tags={post.tags} className="pt-4" />

      <h1 className="break-words pt-4 text-[20px] font-bold leading-snug text-text-primary lg:text-[26px]">
        {post.title}
      </h1>
      <p className="whitespace-pre-wrap break-words pt-3 text-[15px] leading-relaxed text-text-primary lg:text-[16px]">
        {post.body}
      </p>

      {post.imageUrl ? <img src={post.imageUrl} alt="" className="mt-5 w-full rounded-xl object-cover" /> : null}

      {/* Desktop: Lưu/Chia sẻ đã nằm ở thanh đầu trang (như design web) ⇒ ẩn bản trùng ở chân bài. */}
      <PostActions post={post} secondaryClassName="lg:hidden" className="mt-5 border-t border-border/60 pt-3" />
    </article>
  );
}

function CommentItem({ comment }: { comment: CommunityCommentApi }) {
  const { t } = useTranslation("community");
  return (
    <li className="flex gap-3 border-t border-border/60 pt-4 first:border-t-0 first:pt-0">
      <AuthorAvatar name={comment.authorName} size="sm" />
      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-2">
          <p className="min-w-0 flex-1">
            <span className="block truncate text-[14px] font-bold leading-tight text-text-primary">
              {comment.authorName}
            </span>
            <span className="block pt-0.5 text-[12px] text-text-tertiary">
              <RelativeTime value={comment.createdAt} />
            </span>
          </p>
          <ContentMenu target={{ commentId: comment.id }} label={t("post.more")} />
        </div>
        <p className="whitespace-pre-wrap break-words pt-2 text-[14px] leading-relaxed text-text-primary lg:text-[15px]">
          {comment.body}
        </p>
      </div>
    </li>
  );
}

/**
 * Ô bình luận — MỘT form duy nhất: mobile ghim đáy (trên bottom nav, như design mobile),
 * desktop nằm trong khối bình luận kèm avatar người đang đăng nhập (như design web).
 */
function CommentComposer({ postId }: { postId: string }) {
  const { t } = useTranslation("community");
  const displayName = useSessionStore((s) => s.user?.displayName ?? "");
  const isDesktop = useBreakpoint("lg");
  const create = useCreateCommunityComment(postId);
  const [value, setValue] = useState("");
  const inputId = useId();
  const max = COMMUNITY_LIMITS.comment;
  const trimmed = value.trim();
  const tooLong = trimmed.length > max;

  const submit = () => {
    if (!trimmed || tooLong || create.isPending) return;
    create.mutate(trimmed, {
      onSuccess: () => {
        setValue("");
      },
    });
  };

  const message = tooLong ? t("detail.commentTooLong", { max }) : create.isError ? t("detail.commentError") : null;

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      className={cn(
        "fixed inset-x-0 bottom-[calc(64px+env(safe-area-inset-bottom))] z-[var(--z-dropdown)] mx-auto w-full max-w-[480px] border-t border-border bg-surface/95 px-4 py-2.5 backdrop-blur-md",
        "lg:static lg:z-auto lg:mx-0 lg:max-w-none lg:rounded-2xl lg:border-0 lg:bg-background-alt lg:p-4 lg:backdrop-blur-none",
      )}
    >
      <div className="flex items-end gap-2 lg:items-start lg:gap-3">
        <span className="hidden lg:block">
          <AuthorAvatar name={displayName} />
        </span>
        <div className="min-w-0 flex-1">
          <label htmlFor={inputId} className="sr-only">
            {t("detail.commentLabel")}
          </label>
          <textarea
            id={inputId}
            rows={1}
            value={value}
            onChange={(event) => {
              setValue(event.target.value);
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
                event.preventDefault();
                submit();
              }
            }}
            placeholder={isDesktop ? t("detail.commentPlaceholder") : t("detail.commentPlaceholderShort")}
            aria-invalid={tooLong ? true : undefined}
            aria-describedby={message ? `${inputId}-msg` : undefined}
            className={cn(
              "block h-11 w-full resize-none rounded-full border border-border-strong bg-background-alt px-4 py-2.5 text-[14px] leading-snug text-text-primary placeholder:text-text-tertiary",
              "lg:h-24 lg:rounded-xl lg:bg-surface lg:py-3",
              "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
              tooLong && "border-2 border-danger",
            )}
          />
          {message ? (
            <p id={`${inputId}-msg`} role="alert" className="pt-1.5 text-[12px] font-medium text-danger-text">
              {message}
            </p>
          ) : null}
          <div className="hidden justify-end pt-3 lg:flex">
            <button
              type="submit"
              disabled={!trimmed || tooLong || create.isPending}
              className="flex min-h-11 items-center gap-2 rounded-xl bg-primary-dark px-5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Send size={15} aria-hidden="true" />
              {create.isPending ? t("detail.commentSending") : t("detail.commentSend")}
            </button>
          </div>
        </div>
        <button
          type="submit"
          disabled={!trimmed || tooLong || create.isPending}
          aria-label={create.isPending ? t("detail.commentSending") : t("detail.commentSend")}
          className="flex size-11 shrink-0 items-center justify-center rounded-full bg-primary-dark text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50 lg:hidden"
        >
          <Send size={17} aria-hidden="true" />
        </button>
      </div>
    </form>
  );
}

function CommentsSection({
  postId,
  total,
  comments,
}: {
  postId: string;
  total: number;
  comments: CommunityCommentApi[];
}) {
  const { t } = useTranslation("community");
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

      {/* Mobile: form ghim đáy (fixed) nên khung bọc không chiếm chỗ; desktop: nằm ngay dưới tiêu đề. */}
      <div className="lg:pt-5">
        <CommentComposer postId={postId} />
      </div>

      {comments.length > 0 ? (
        <ul className="flex flex-col gap-4 pt-4 lg:pt-6">
          {comments.map((item) => (
            <CommentItem key={item.id} comment={item} />
          ))}
        </ul>
      ) : (
        <p className="pt-3 text-[13px] leading-relaxed text-text-secondary lg:pt-5">{t("detail.noComments")}</p>
      )}
    </section>
  );
}

function PostDetail({ postId }: { postId: string }) {
  const { t } = useTranslation("community");
  const detail = useCommunityPost(postId);

  if (detail.isPending) return <DetailSkeleton />;

  if (detail.isError) {
    if (isApiError(detail.error) && (detail.error.status === 404 || detail.error.status === 400)) return <NotFound />;
    return (
      <div className="flex flex-col gap-4 px-4 py-4 lg:p-0">
        <BackLink />
        <ErrorState
          title={t("detail.errorTitle")}
          description={t("detail.errorBody")}
          onRetry={() => {
            void detail.refetch();
          }}
          className="rounded-2xl bg-surface shadow-brand-md"
        />
      </div>
    );
  }

  const { post, comments } = detail.data;

  return (
    <div className="flex flex-col gap-4 px-4 py-2 pb-20 lg:gap-5 lg:p-0">
      <DetailTopBar post={post} />
      <div className="flex items-start gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4 lg:gap-5">
          <PostArticle post={post} />
          <CommentsSection postId={post.id} total={post.commentCount} comments={comments} />
        </div>
        <aside className="sticky top-[88px] hidden w-[296px] shrink-0 lg:block">
          <RulesCard />
        </aside>
      </div>
    </div>
  );
}

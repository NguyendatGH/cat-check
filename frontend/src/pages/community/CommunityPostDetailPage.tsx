import { useTranslation } from "react-i18next";
import { Link, useLocation, useParams } from "react-router";
import { ArrowLeft, Bookmark, ChevronRight, FileQuestion, Share2 } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api/errors";
import { EmptyState, ErrorState, SkeletonLoader, toast } from "@/shared/ui";
import { useCommunityPost, useCommunityReaction, type CommunityPostApi } from "@/features/community";
import { CommentsSection } from "./comments";
import {
  AuthorAvatar,
  AuthorMeta,
  ContentMenu,
  PostActions,
  PostCardSkeleton,
  RulesCard,
  TagChips,
  useSharePost,
} from "./parts";

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
  const location = useLocation();
  const imageUploadFailed = (location.state as { imageUploadFailed?: boolean } | null)?.imageUploadFailed === true;
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
      {imageUploadFailed && !post.imageUrl ? (
        <p role="status" className="mt-4 rounded-xl bg-warning-bg px-3 py-2 text-[12px] font-medium text-warning-text">
          {t("newPost.image.errors.upload")}
        </p>
      ) : null}

      <PostActions post={post} secondaryClassName="lg:hidden" className="mt-5 border-t border-border/60 pt-3" />
    </article>
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

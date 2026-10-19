import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  Lightbulb,
  MessageCircleQuestion,
  MessagesSquare,
  PenLine,
  Plus,
  Users,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { formatNumber } from "@/shared/lib/format/formatNumber";
import { Button, ErrorState } from "@/shared/ui";
import { useSessionStore } from "@/entities/user";
import { useCommunityFeed, type CommunityCategory, type CommunityPostApi } from "@/features/community";
import { AuthorAvatar, AuthorMeta, ContentMenu, PostActions, PostCardSkeleton, RulesCard, TagChips } from "./parts";


type FeedFilter = CommunityCategory | "ALL";

const FILTERS: { key: FeedFilter; icon: LucideIcon }[] = [
  { key: "ALL", icon: MessagesSquare },
  { key: "QA", icon: MessageCircleQuestion },
  { key: "TIP", icon: Lightbulb },
  { key: "EXPERIENCE", icon: PenLine },
];

function PostCard({ post }: { post: CommunityPostApi }) {
  const { t } = useTranslation("community");
  const href = `/community/posts/${post.id}`;

  return (
    <article className="rounded-2xl bg-surface p-4 shadow-brand-md lg:p-6">
      <header className="flex items-start gap-3">
        <AuthorAvatar name={post.authorName} />
        <AuthorMeta name={post.authorName} category={post.category} createdAt={post.createdAt} />
        <ContentMenu target={{ postId: post.id }} label={t("post.more")} />
      </header>

      <h2 className="pt-3 text-[16px] font-bold leading-snug text-text-primary lg:pt-4 lg:text-[18px]">
        <Link to={href} className="break-words hover:text-primary-dark">
          {post.title}
        </Link>
      </h2>
      <p className="line-clamp-3 whitespace-pre-line break-words pt-1.5 text-[14px] leading-relaxed text-text-secondary lg:line-clamp-4 lg:text-[15px]">
        {post.body}
      </p>

      {post.imageUrl ? (
        <img src={post.imageUrl} alt="" loading="lazy" className="mt-3 aspect-[16/10] w-full rounded-xl object-cover" />
      ) : null}

      <TagChips tags={post.tags} className="pt-3" />

      <PostActions post={post} commentHref={href} className="mt-3 border-t border-border/60 pt-2" />
    </article>
  );
}

function ComposerPrompt() {
  const { t } = useTranslation("community");
  const displayName = useSessionStore((s) => s.user?.displayName ?? "");

  return (
    <section className="hidden items-center gap-4 rounded-2xl bg-surface p-5 shadow-brand-md lg:flex">
      <AuthorAvatar name={displayName} size="lg" />
      <Link
        to="/community/new"
        className="flex min-h-[52px] min-w-0 flex-1 items-center rounded-xl bg-background-alt px-4 text-[14px] text-text-tertiary hover:bg-chip-bg"
      >
        <span className="truncate">{t("feed.composerPlaceholder")}</span>
      </Link>
      <Link
        to="/community/new"
        className="flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-xl bg-primary-dark px-5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary"
      >
        {t("feed.composerSubmit")}
      </Link>
    </section>
  );
}

function FeedEmptyState({ filtered }: { filtered: boolean }) {
  const { t } = useTranslation("community");
  return (
    <section className="flex flex-col items-center gap-3 rounded-2xl bg-surface px-6 py-10 text-center shadow-brand-md">
      <span
        className="flex size-14 items-center justify-center rounded-full bg-deco-backdrop text-primary-dark"
        aria-hidden="true"
      >
        <Users size={24} />
      </span>
      <h2 className="text-[17px] font-bold text-text-primary">{t(filtered ? "empty.filterTitle" : "empty.title")}</h2>
      <p className="max-w-[420px] text-[13px] leading-relaxed text-text-secondary">
        {t(filtered ? "empty.filterBody" : "empty.body")}
      </p>
      <Link
        to="/community/new"
        className="mt-2 flex min-h-11 items-center gap-2 rounded-xl bg-primary-dark px-5 text-[13px] font-bold text-white shadow-brand-md hover:bg-primary"
      >
        <Plus size={15} aria-hidden="true" />
        {t("empty.cta")}
      </Link>
    </section>
  );
}

function FeedHero() {
  const { t } = useTranslation("community");
  const all = useCommunityFeed("ALL");
  const total = all.data?.pages[0]?.totalElements ?? 0;

  return (
    <section className="hidden items-center gap-6 rounded-2xl bg-surface p-6 shadow-brand-lg lg:flex xl:p-8">
      <div className="min-w-0 flex-1">
        {total > 0 ? (
          <span className="mb-2 inline-flex items-center gap-1.5 rounded-full bg-chip-bg px-3 py-1 text-[12px] font-bold text-primary-dark">
            <MessagesSquare size={13} aria-hidden="true" />
            {t("feed.postCount", { count: total, formattedCount: formatNumber(total) })}
          </span>
        ) : null}
        <h1 className="text-[28px] font-bold leading-tight text-primary-dark xl:text-[30px]">{t("feed.title")}</h1>
        <p className="max-w-[560px] pt-2 text-[14px] leading-relaxed text-text-secondary">{t("feed.subtitle")}</p>
      </div>
      <Link
        to="/community/new"
        className="flex min-h-12 shrink-0 items-center gap-2 rounded-xl bg-primary-dark px-6 text-[14px] font-bold text-white shadow-brand-lg hover:bg-primary"
      >
        <Plus size={16} aria-hidden="true" />
        {t("feed.newPostCta")}
      </Link>
    </section>
  );
}

function FilterTabs({ value, onChange }: { value: FeedFilter; onChange: (value: FeedFilter) => void }) {
  const { t } = useTranslation("community");
  return (
    <div className="relative -mx-4 lg:mx-0">
      <div
        role="group"
        aria-label={t("feed.filterLabel")}
        className="flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] lg:flex-wrap lg:gap-3 lg:overflow-visible lg:px-0 lg:pb-0"
      >
        {FILTERS.map(({ key, icon: Icon }) => {
          const active = value === key;
          return (
            <button
              key={key}
              type="button"
              onClick={() => {
                onChange(key);
              }}
              aria-pressed={active}
              className={cn(
                "flex min-h-10 shrink-0 items-center gap-2 whitespace-nowrap rounded-full px-4 text-[13px] font-semibold transition-colors lg:min-h-11 lg:px-5 lg:text-[14px]",
                active
                  ? "bg-primary-dark text-white shadow-brand-md"
                  : "bg-deco-backdrop text-primary-dark hover:bg-chip-bg lg:bg-surface lg:text-text-primary lg:shadow-xs lg:hover:bg-background-alt",
              )}
            >
              <Icon size={16} aria-hidden="true" className={cn("hidden lg:block", !active && "text-primary-dark")} />
              {t(`category.${key}`)}
            </button>
          );
        })}
      </div>
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 right-0 w-8 bg-gradient-to-l from-background to-transparent lg:hidden"
      />
    </div>
  );
}

function FeedList({ filter }: { filter: FeedFilter }) {
  const { t } = useTranslation("community");
  const feed = useCommunityFeed(filter);

  if (feed.isPending) {
    return (
      <div className="flex flex-col gap-4 lg:gap-5" role="status" aria-busy="true">
        <span className="sr-only">{t("feed.loadingMore")}</span>
        <PostCardSkeleton />
        <PostCardSkeleton />
      </div>
    );
  }

  if (feed.data === undefined) {
    return (
      <ErrorState
        title={t("api.error")}
        description={t("api.errorBody")}
        onRetry={() => {
          void feed.refetch();
        }}
        className="rounded-2xl bg-surface shadow-brand-md"
      />
    );
  }

  const posts = feed.data.pages.flatMap((page) => page.items);
  if (posts.length === 0) {
    return (
      <>
        <FeedEmptyState filtered={filter !== "ALL"} />
        <RulesCard className="lg:hidden" showDisclaimer={false} />
      </>
    );
  }

  return (
    <>
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
      {feed.hasNextPage ? (
        <div className="flex flex-col items-center gap-2">
          <Button
            variant="tertiary"
            loading={feed.isFetchingNextPage}
            onClick={() => {
              void feed.fetchNextPage();
            }}
            className="bg-surface"
          >
            {feed.isFetchingNextPage ? t("feed.loadingMore") : t("feed.loadMore")}
          </Button>
          {feed.isFetchNextPageError ? (
            <p role="alert" className="text-[12px] font-medium text-danger-text">
              {t("feed.loadMoreError")}
            </p>
          ) : null}
        </div>
      ) : null}
    </>
  );
}

export function CommunityFeedPage() {
  const { t } = useTranslation("community");
  const [filter, setFilter] = useState<FeedFilter>("ALL");

  return (
    <div className="flex flex-col gap-4 px-4 py-4 lg:gap-5 lg:p-0">
      <h1 className="sr-only lg:hidden">{t("feed.title")}</h1>
      <FeedHero />

      <FilterTabs value={filter} onChange={setFilter} />

      <div className="flex items-start gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4 pb-20 lg:gap-5 lg:pb-0">
          <ComposerPrompt />
          <FeedList filter={filter} />
        </div>
        <aside className="sticky top-[88px] hidden w-[296px] shrink-0 lg:block">
          <RulesCard />
        </aside>
      </div>

      <Link
        to="/community/new"
        className="fixed bottom-[calc(76px+env(safe-area-inset-bottom))] right-4 z-[var(--z-dropdown)] flex min-h-12 items-center gap-2 rounded-full bg-primary-dark px-5 text-[14px] font-bold text-white shadow-brand-xl hover:bg-primary lg:hidden"
      >
        <Plus size={18} aria-hidden="true" />
        {t("feed.composeFab")}
      </Link>
    </div>
  );
}

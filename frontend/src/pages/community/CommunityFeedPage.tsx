import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  Bookmark,
  Bot,
  Camera,
  Share2,
  ShieldCheck,
  Stethoscope,
  ThumbsUp,
  TrendingUp,
  Users,
  BadgeCheck,
  FileText,
  Heart,
  Lightbulb,
  MessageSquare,
  MoreHorizontal,
  Plus,
  ScanLine,
  Siren,
  Check,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { listCommunityPosts, type CommunityPostApi } from "@/features/community";
import {
  DESIGN_MOCK_COMMUNITY_RULES,
  DESIGN_MOCK_COMPOSER_TAGS,
  DESIGN_MOCK_RAIL_MARK,
  DESIGN_MOCK_SAFETY_NOTICE,
  DESIGN_MOCK_WEB_HEADER,
  DESIGN_MOCK_WEEKLY_TOPICS,
  type MockAuthor,
  type MockBodySegment,
  type MockMobilePost,
  type MockPhGauge,
  type MockTone,
  type MockWebPost,
} from "./mockData";

/**
 * `/community` — Bảng tin Cộng đồng.
 *
 * Hai cây DOM tách rời (giống `CatTrendsPage` + `webTrends.tsx`) vì bố cục mobile và desktop
 * của Figma khác hẳn nhau, không phải chỉ giãn cột:
 *  - `< lg`: `11. Bảng tin Cộng đồng (Community Feed)` — dải lưu ý an toàn, 3 chip lọc,
 *    danh sách bài dạng thẻ, nút FAB "Đăng bài viết" nổi trên bottom nav.
 *  - `>= lg`: `Web - 15. Cộng đồng & Thảo luận Y khoa` — khối tiêu đề cộng đồng, 5 tab,
 *    ô soạn bài nhanh, 3 bài thảo luận, cột phải 296px (chủ đề tuần + quy chuẩn ISFM).
 *
 * KHÔNG tự thêm padding ngang ở `lg` — `AppLayout` đã cấp hộp nội dung 944px kèm padding.
 *
 * DỮ LIỆU bài viết lấy từ Community API; `mockData.ts` chỉ còn cung cấp nội dung phụ cho
 * rail và style của frame Figma.
 */

/* --------------------------------- mảnh dùng chung --------------------------------- */

const PH_CHIP_TONE: Record<MockTone, string> = {
  primary: "bg-chip-bg text-primary-dark",
  secondary: "bg-secondary text-secondary-text-on",
  success: "bg-success-bg text-success-text",
  danger: "bg-danger-bg text-danger-text",
  neutral: "bg-background-alt text-text-secondary",
};

const BADGE_TONE: Record<MockTone, string> = {
  primary: "bg-chip-bg text-primary-dark",
  secondary: "bg-secondary text-secondary-text-on",
  success: "bg-success-bg text-success-text",
  danger: "bg-danger-bg text-danger-text",
  neutral: "bg-background-alt text-text-secondary",
};

const AVATAR_TONE: Record<MockTone, string> = {
  primary: "bg-info text-primary-dark",
  secondary: "bg-secondary text-secondary-text-on",
  success: "bg-verified-bright text-verified-deep",
  danger: "bg-danger-bg text-danger-text",
  neutral: "bg-background-alt text-text-secondary",
};

/** Đoạn văn trộn chữ thường với chip pH (mọi con số đến từ `mockData`). */
function BodyText({ segments, className }: { segments: readonly MockBodySegment[]; className?: string }) {
  return (
    <p className={className}>
      {segments.map((seg, i) =>
        seg.kind === "text" ? (
          <span key={`t${String(i)}`}>{seg.value}</span>
        ) : (
          <span
            key={`${seg.value}-${String(i)}`}
            className={cn("mx-0.5 inline-block rounded px-1.5 py-0.5 text-[12px] font-bold", PH_CHIP_TONE[seg.tone])}
          >
            {seg.value}
          </span>
        ),
      )}
    </p>
  );
}

/** Thước pH ngang — gradient toan → chuẩn → kiềm, chấm chỉ vị trí lấy từ mock. */
function PhScale({ gauge }: { gauge: MockPhGauge }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="relative h-3 rounded-full bg-gradient-to-r from-secondary via-success to-primary">
        <span
          className="absolute top-1/2 size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-primary-darker shadow-xs"
          style={{ left: `${String(gauge.markerPercent)}%` }}
          aria-hidden="true"
        />
      </div>
      <div className="flex items-center justify-between text-[10px] leading-tight">
        <span className="text-text-tertiary">{gauge.lowLabel}</span>
        <span className="font-bold text-success-text">{gauge.idealLabel}</span>
        <span className="text-primary-dark">{gauge.highLabel}</span>
      </div>
    </div>
  );
}

function AuthorBadge({ author }: { author: MockAuthor }) {
  if (!author.roleBadge) return null;
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold",
        BADGE_TONE[author.roleBadgeTone ?? "neutral"],
      )}
    >
      <BadgeCheck size={10} aria-hidden="true" />
      {author.roleBadge}
    </span>
  );
}

/* =============================== BẢN MOBILE (< lg) =============================== */

function MobilePostCard({ post }: { post: MockMobilePost }) {
  const { t } = useTranslation("community");

  return (
    <article className="rounded-2xl bg-surface p-4 shadow-brand-md">
      <div className="flex items-start gap-3">
        {post.author.avatarUrl ? (
          <img src={post.author.avatarUrl} alt="" className="size-11 shrink-0 rounded-full object-cover" />
        ) : (
          <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-chip-bg text-[15px] font-bold text-primary-dark">
            {post.author.name.slice(0, 1).toUpperCase()}
          </span>
        )}
        <div className="min-w-0 flex-1">
          <p className="text-[15px] font-bold leading-tight text-text-primary">
            {post.author.name}
            {post.author.nameSuffix ? (
              <span className="font-normal text-text-secondary"> {post.author.nameSuffix}</span>
            ) : null}
          </p>
          <div className="flex flex-wrap items-center gap-2 pt-1">
            <AuthorBadge author={post.author} />
            <span className="text-[11px] text-text-tertiary">{post.author.meta}</span>
          </div>
        </div>
        <button
          type="button"
          aria-label={t("post.moreOptions")}
          className="-mr-1 flex size-8 shrink-0 items-center justify-center rounded-full text-text-tertiary hover:bg-background-alt"
        >
          <MoreHorizontal size={16} aria-hidden="true" />
        </button>
      </div>

      <BodyText segments={post.body} className="pt-3 text-[14px] leading-relaxed text-text-primary" />

      {post.photoUrl ? (
        <div className="relative mt-3 overflow-hidden rounded-xl">
          <img src={post.photoUrl} alt="" className="aspect-[326/224] w-full object-cover" />
          {post.photoCaption ? (
            <span className="absolute bottom-2.5 right-2.5 inline-flex items-center gap-1.5 rounded-full bg-surface/90 px-2.5 py-1 text-[11px] font-semibold text-text-primary">
              <span className="size-1.5 rounded-full bg-secondary" aria-hidden="true" />
              {post.photoCaption}
            </span>
          ) : null}
        </div>
      ) : null}

      {post.indicatorStrip ? (
        <div className="mt-3 flex items-center justify-between gap-2 rounded-xl border border-border/60 bg-background-alt px-3 py-2">
          <span className="flex min-w-0 items-center gap-2 text-[12px] font-semibold text-primary-dark">
            <TrendingUp size={13} className="shrink-0" aria-hidden="true" />
            <span className="truncate">{post.indicatorStrip.label}</span>
          </span>
          <span className="shrink-0 rounded-full bg-primary px-2.5 py-1 text-[11px] font-bold text-white">
            {post.indicatorStrip.value}
          </span>
        </div>
      ) : null}

      {post.expertNote ? (
        <div className="mt-3 flex gap-2.5 rounded-xl bg-background-alt p-3">
          <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-white">
            <Stethoscope size={13} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-[12px] font-bold text-primary-dark">{post.expertNote.title}</p>
            <p className="text-[11px] text-text-tertiary">{post.expertNote.subtitle}</p>
            <p className="pt-2 text-[12px] leading-relaxed text-text-secondary">{post.expertNote.body}</p>
          </div>
        </div>
      ) : null}

      {post.tipNote ? (
        <div className="mt-3 flex gap-2.5 rounded-xl border border-border/60 bg-background-alt p-3">
          <span className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary-dark">
            <ScanLine size={18} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-[12px] font-bold text-text-primary">{post.tipNote.title}</p>
            <p className="pt-0.5 text-[12px] leading-relaxed text-text-secondary">{post.tipNote.body}</p>
          </div>
        </div>
      ) : null}

      <div className="flex items-center gap-5 pt-4 text-[13px] text-text-secondary">
        <span className="flex items-center gap-1.5">
          <Heart size={16} aria-hidden="true" />
          {post.likeCount}
          <span className="sr-only">{t("post.like")}</span>
        </span>
        <Link to={`/community/posts/${post.id}`} className="flex items-center gap-1.5 hover:text-primary-dark">
          <MessageSquare size={16} aria-hidden="true" />
          {post.commentCount}
          <span className="sr-only">{t("post.comment")}</span>
        </Link>
        <span className="ml-auto flex items-center gap-1.5">
          <Bookmark size={16} aria-hidden="true" />
          {post.saveCount > 0 ? post.saveCount : null}
          <span className="sr-only">{t("post.save")}</span>
        </span>
        <span className="flex items-center gap-1.5">
          <Share2 size={16} aria-hidden="true" />
          <span className="sr-only">{t("post.share")}</span>
        </span>
      </div>
    </article>
  );
}

const MOBILE_TABS = ["all", "qa", "tips"] as const;

function toMobilePost(post: CommunityPostApi): MockMobilePost {
  return {
    id: post.id,
    author: { name: post.authorName || "Thành viên CATCHECK", avatarUrl: "", meta: post.category },
    body: [{ kind: "text", value: post.body }],
    photoUrl: post.imageUrl ?? undefined,
    likeCount: post.likeCount,
    commentCount: post.commentCount,
    saveCount: post.bookmarked ? 1 : 0,
  };
}

function toWebPost(post: CommunityPostApi): MockWebPost {
  return {
    id: post.id,
    author: { name: post.authorName || "Thành viên CATCHECK", avatarUrl: "", meta: post.category },
    avatarInitial: (post.authorName || "C").slice(0, 1).toUpperCase(),
    avatarTone: "primary",
    title: post.title,
    body: post.body,
    photos: post.imageUrl ? [{ url: post.imageUrl, caption: "Ảnh đính kèm" }] : [],
    likeCount: post.likeCount,
    commentCount: post.commentCount,
    hasShare: true,
    saveLabel: post.bookmarked ? "Đã lưu" : "Lưu bài viết",
  };
}

function MobileFeed({ posts }: { posts: CommunityPostApi[] }) {
  const { t } = useTranslation("community");
  const [tab, setTab] = useState<(typeof MOBILE_TABS)[number]>("all");
  const visiblePosts = posts.filter((post) => {
    if (tab === "qa") return post.category === "QA";
    if (tab === "tips") return post.category === "TIP" || post.category === "EXPERIENCE";
    return true;
  });

  return (
    <div className="flex flex-col gap-4 px-4 py-4">
      <section className="flex gap-3 rounded-xl bg-background-alt p-4">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-deco-backdrop text-primary-dark">
          <ShieldCheck size={15} aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <h2 className="text-[11px] font-bold tracking-[0.5px] text-primary-dark">{t("mobile.safetyTitle")}</h2>
          <p className="pt-1 text-[13px] leading-relaxed text-text-secondary">{DESIGN_MOCK_SAFETY_NOTICE.body}</p>
        </div>
      </section>

      <div className="relative -mx-4">
        <div className="flex gap-2 overflow-x-auto px-4 pb-1 pr-10">
          {MOBILE_TABS.map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => {
                setTab(key);
              }}
              aria-pressed={tab === key}
              className={cn(
                "shrink-0 rounded-full px-4 py-1.5 text-[13px] font-semibold transition-colors",
                tab === key ? "bg-primary text-white" : "bg-deco-backdrop text-primary-dark hover:bg-chip-bg",
              )}
            >
              {t(`mobile.tabs.${key}`)}
            </button>
          ))}
        </div>
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-y-0 right-0 w-8 bg-gradient-to-l from-background to-transparent"
        />
      </div>

      {visiblePosts.map((post) => (
        <MobilePostCard key={post.id} post={toMobilePost(post)} />
      ))}
      {visiblePosts.length === 0 ? (
        <p className="rounded-xl bg-surface p-4 text-center text-[13px] text-text-secondary shadow-xs">{t("api.empty")}</p>
      ) : null}

      <Link
        to="/community/new"
        className="fixed bottom-[calc(76px+env(safe-area-inset-bottom))] right-4 z-[var(--z-dropdown)] flex items-center gap-2 rounded-full bg-primary px-5 py-3.5 text-[14px] font-bold text-white shadow-brand-xl"
      >
        <Plus size={16} aria-hidden="true" />
        {t("mobile.composeFab")}
        <span className="size-2 rounded-full bg-secondary" aria-hidden="true" />
      </Link>
    </div>
  );
}

/* =============================== BẢN DESKTOP (>= lg) =============================== */

const WEB_TABS = [
  { key: "all", icon: MessageSquare },
  { key: "vetQa", icon: Stethoscope },
  { key: "colorTips", icon: ScanLine },
  { key: "nutrition", icon: Lightbulb },
  { key: "clinicReview", icon: FileText },
] as const;

function WebComposer() {
  const { t } = useTranslation("community");
  return (
    <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
      <div className="flex gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-chip-bg text-[14px] font-bold text-primary-dark">
          {DESIGN_MOCK_WEB_HEADER.composerAvatarInitial}
        </span>
        <Link
          to="/community/new"
          className="flex min-h-[72px] flex-1 items-start rounded-xl bg-background-alt px-4 py-3 text-[13px] text-text-tertiary hover:bg-chip-bg"
        >
          {t("web.composer.placeholder")}
        </Link>
      </div>

      <div className="flex flex-wrap items-center gap-2 pl-12 pt-3">
        <span className="text-[10px] font-bold tracking-[0.5px] text-text-tertiary">{t("web.composer.tagsLabel")}</span>
        {DESIGN_MOCK_COMPOSER_TAGS.map((tag) => (
          <span key={tag} className="rounded-full bg-chip-bg px-2.5 py-1 text-[11px] font-semibold text-primary-dark">
            {tag}
          </span>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-4 pt-4">
        <span className="flex items-center gap-1.5 text-[12px] font-semibold text-text-secondary">
          <Camera size={14} aria-hidden="true" />
          {t("web.composer.attachPhoto")}
        </span>
        <span className="flex items-center gap-1.5 text-[12px] font-semibold text-text-secondary">
          <ScanLine size={14} aria-hidden="true" />
          {t("web.composer.attachScan")}
        </span>
        <span className="flex items-center gap-1.5 text-[12px] font-semibold text-text-secondary">
          <FileText size={14} aria-hidden="true" />
          {t("web.composer.attachRecord")}
        </span>
        <Link
          to="/community/new"
          className="ml-auto rounded-xl bg-primary-dark px-5 py-2.5 text-[13px] font-bold text-white hover:bg-primary"
        >
          {t("web.composer.submit")}
        </Link>
      </div>
    </section>
  );
}

function WebPostCard({ post }: { post: MockWebPost }) {
  const { t } = useTranslation("community");

  return (
    <article className="rounded-2xl bg-surface p-5 shadow-brand-md">
      <div className="flex items-start gap-3">
        <span
          className={cn(
            "flex size-10 shrink-0 items-center justify-center rounded-full text-[15px] font-bold",
            AVATAR_TONE[post.avatarTone],
          )}
        >
          {post.avatarInitial}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[14px] font-bold text-text-primary">{post.author.name}</span>
            {post.petBadge ? (
              <span className="rounded-full bg-background-alt px-2 py-0.5 text-[10px] font-semibold text-text-secondary">
                {post.petBadge}
              </span>
            ) : null}
          </div>
          <p className="pt-0.5 text-[11px] text-text-tertiary">{post.author.meta}</p>
        </div>
        {post.cornerBadge ? (
          <span
            className={cn(
              "shrink-0 rounded-full px-2.5 py-1 text-[10px] font-bold",
              BADGE_TONE[post.cornerBadgeTone ?? "neutral"],
            )}
          >
            {post.cornerBadge}
          </span>
        ) : null}
        <button
          type="button"
          aria-label={t("post.moreOptions")}
          className="flex size-7 shrink-0 items-center justify-center rounded-full text-text-tertiary hover:bg-background-alt"
        >
          <MoreHorizontal size={15} aria-hidden="true" />
        </button>
      </div>

      <Link
        to={`/community/posts/${post.id}`}
        className="block pt-3 text-[17px] font-bold leading-snug text-text-primary hover:text-primary-dark"
      >
        {post.title}
      </Link>
      <p className="pt-2 text-[13px] leading-relaxed text-text-secondary">{post.body}</p>

      {post.gauge ? (
        <div className="grid gap-2 pt-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <div className="relative overflow-hidden rounded-xl">
            <img src={post.photos[0].url} alt="" className="aspect-[283/212] w-full object-cover" />
            <span className="absolute bottom-2 left-2 inline-flex items-center gap-1 rounded-md bg-primary-darker/85 px-2.5 py-1 text-[11px] font-semibold text-white">
              <span className="size-1.5 rounded-full bg-success-strong" aria-hidden="true" />
              {post.photos[0].caption}
            </span>
          </div>
          <div className="flex flex-col gap-3 rounded-xl bg-background-alt p-4">
            <div className="flex items-start justify-between gap-2">
              <span className="text-[10px] font-bold tracking-[0.5px] text-text-tertiary">{post.gaugeTitle}</span>
              <span
                className={cn(
                  "shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold",
                  BADGE_TONE[post.gauge.statusTone],
                )}
              >
                {post.gauge.statusLabel}
              </span>
            </div>
            <p className="flex items-baseline gap-1.5">
              <span className="text-[30px] font-bold leading-none text-primary-dark">{post.gauge.value}</span>
              <span className="text-[12px] font-semibold text-text-primary">{post.gauge.unitLabel}</span>
              <span className="text-[11px] text-text-tertiary">{post.gauge.referenceLabel}</span>
            </p>
            <PhScale gauge={post.gauge} />
            <p className="flex items-center gap-2 rounded-lg bg-surface px-3 py-2 text-[11px] font-semibold text-text-secondary">
              <Bot size={13} className="shrink-0 text-primary-dark" aria-hidden="true" />
              {post.gaugeConfidence}
            </p>
          </div>
        </div>
      ) : null}

      {!post.gauge && post.photos.length > 0 ? (
        <div className="grid gap-2 pt-4 sm:grid-cols-2">
          {post.photos.map((photo) => (
            <div key={photo.url} className="relative overflow-hidden rounded-xl">
              <img src={photo.url} alt="" className="aspect-[283/159] w-full object-cover" />
              <span className="absolute bottom-2 left-2 rounded bg-primary-darker/80 px-2 py-0.5 text-[11px] font-semibold text-white">
                {photo.caption}
              </span>
            </div>
          ))}
        </div>
      ) : null}

      {post.vetReply ? (
        <div className="mt-4 rounded-xl bg-deco-backdrop p-4">
          <div className="flex items-start gap-2.5">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary text-white">
              <ShieldCheck size={14} aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[13px] font-bold text-primary-dark">{post.vetReply.name}</span>
                <span className="rounded-full bg-primary px-2 py-0.5 text-[10px] font-bold text-white">
                  {post.vetReply.badge}
                </span>
              </div>
              <p className="pt-0.5 text-[11px] text-text-tertiary">{post.vetReply.org}</p>
            </div>
            <Bookmark size={14} className="shrink-0 text-primary" aria-hidden="true" />
          </div>
          <div className="flex flex-col gap-1.5 pt-3">
            {post.vetReply.paragraphs.map((para) => (
              <p key={para} className="text-[13px] leading-relaxed text-text-primary">
                {para}
              </p>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-4 pt-3 text-[11px] font-semibold text-text-secondary">
            <span className="flex items-center gap-1.5">
              <ThumbsUp size={12} aria-hidden="true" />
              {post.vetReply.helpfulLabel}
            </span>
            <span>{post.vetReply.replyLabel}</span>
          </div>
        </div>
      ) : null}

      {post.tipNote ? (
        <div className="mt-4 flex gap-3 rounded-xl bg-background-alt p-4">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
            <Lightbulb size={18} aria-hidden="true" />
          </span>
          <p className="text-[13px] leading-relaxed text-text-secondary">
            <span className="font-bold text-text-primary">{post.tipNote.title}</span> {post.tipNote.body}
          </p>
        </div>
      ) : null}

      <div className="mt-4 flex items-center gap-6 border-t border-border/50 pt-3 text-[12px] font-semibold text-text-secondary">
        <span className="flex items-center gap-1.5">
          <Heart size={15} aria-hidden="true" />
          {post.likeCount} {t("post.like")}
        </span>
        <Link to={`/community/posts/${post.id}`} className="flex items-center gap-1.5 hover:text-primary-dark">
          <MessageSquare size={15} aria-hidden="true" />
          {post.commentCount} {post.commentLabelOverride ?? t("post.comment")}
        </Link>
        {post.hasShare ? (
          <span className="flex items-center gap-1.5">
            <Share2 size={15} aria-hidden="true" />
            {t("post.share")}
          </span>
        ) : null}
        <span className="ml-auto flex items-center gap-1.5">
          <Bookmark size={15} aria-hidden="true" />
          {post.saveLabel}
        </span>
      </div>
    </article>
  );
}

function WebRail() {
  const { t } = useTranslation("community");
  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-center rounded-2xl bg-surface py-3 shadow-brand-md">
        <img src={DESIGN_MOCK_RAIL_MARK} alt="" className="h-8 w-12 object-contain" />
      </div>

      <section className="rounded-2xl bg-surface p-6 shadow-brand-md">
        <h2 className="flex items-center gap-2 text-[14px] font-bold text-text-primary">
          <TrendingUp size={15} className="text-primary" aria-hidden="true" />
          {t("web.rail.topicsTitle")}
        </h2>
        <ul className="flex flex-col gap-2 pt-3">
          {DESIGN_MOCK_WEEKLY_TOPICS.map((topic) => (
            <li key={topic.tag} className="rounded-xl bg-background-alt px-3 py-2.5">
              <div className="flex items-baseline justify-between gap-2">
                <span className="truncate text-[12px] font-bold text-primary-dark">{topic.tag}</span>
                <span className="shrink-0 text-[10px] text-text-tertiary">{topic.count}</span>
              </div>
              <p className="truncate pt-0.5 text-[11px] text-text-secondary">{topic.body}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded-2xl bg-surface p-6 shadow-brand-md">
        <h2 className="flex items-center gap-2 text-[14px] font-bold text-text-primary">
          <ShieldCheck size={15} className="text-primary" aria-hidden="true" />
          {t("web.rail.rulesTitle")}
        </h2>
        <p className="pt-2 text-[11px] leading-relaxed text-text-secondary">{DESIGN_MOCK_COMMUNITY_RULES.intro}</p>
        <p className="mt-3 flex gap-2 rounded-lg bg-danger-bg/40 p-3 text-[11px] font-semibold leading-relaxed text-danger-text">
          <Siren size={13} className="mt-0.5 shrink-0" aria-hidden="true" />
          {DESIGN_MOCK_COMMUNITY_RULES.alert}
        </p>
        <ul className="flex flex-col gap-2 pt-3">
          {DESIGN_MOCK_COMMUNITY_RULES.items.map((item) => (
            <li key={item} className="flex gap-2 text-[11px] leading-relaxed text-text-secondary">
              <Check size={12} className="mt-0.5 shrink-0 text-success-text" aria-hidden="true" />
              {item}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function WebFeed({ posts }: { posts: CommunityPostApi[] }) {
  const { t } = useTranslation("community");
  const [tab, setTab] = useState<(typeof WEB_TABS)[number]["key"]>("all");
  const visiblePosts = posts.filter((post) => {
    if (tab === "all") return true;
    if (tab === "vetQa") return post.category === "QA";
    if (tab === "clinicReview") return post.category === "EXPERIENCE";
    return post.category === "TIP";
  });

  return (
    <div className="flex flex-col gap-5">
      <section className="flex items-center gap-6 rounded-2xl bg-surface p-6 shadow-brand-lg">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="flex items-center gap-1.5 rounded-full bg-info px-3 py-1 text-[11px] font-bold text-primary-dark">
              <Users size={12} aria-hidden="true" />
              {DESIGN_MOCK_WEB_HEADER.memberBadge}
            </span>
            <span className="flex items-center gap-1.5 rounded-full bg-secondary px-3 py-1 text-[11px] font-bold text-secondary-text-on">
              <BadgeCheck size={12} aria-hidden="true" />
              {DESIGN_MOCK_WEB_HEADER.sponsorBadge}
            </span>
          </div>
          <h1 className="pt-3 text-[28px] font-bold leading-tight text-primary-dark">{DESIGN_MOCK_WEB_HEADER.title}</h1>
          <p className="max-w-[520px] pt-2 text-[13px] leading-relaxed text-text-secondary">
            {DESIGN_MOCK_WEB_HEADER.subtitle}
          </p>
        </div>
        <Link
          to="/community/new"
          className="flex shrink-0 items-center gap-2 rounded-xl bg-primary-dark px-5 py-3 text-[13px] font-bold text-white shadow-brand-md hover:bg-primary"
        >
          <Stethoscope size={15} aria-hidden="true" />
          {t("web.askVetCta")}
        </Link>
      </section>

      <div className="flex flex-wrap gap-2">
        {WEB_TABS.map(({ key, icon: Icon }) => (
          <button
            key={key}
            type="button"
            onClick={() => {
              setTab(key);
            }}
            aria-pressed={tab === key}
            className={cn(
              "flex items-center gap-2 rounded-full px-4 py-2.5 text-[12px] font-semibold transition-colors",
              tab === key ? "bg-primary text-white" : "bg-surface text-text-secondary hover:bg-background-alt",
            )}
          >
            <Icon size={13} aria-hidden="true" />
            {t(`web.tabs.${key}`)}
          </button>
        ))}
      </div>

      <div className="flex items-start gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          <WebComposer />
          {visiblePosts.map((post) => (
            <WebPostCard key={post.id} post={toWebPost(post)} />
          ))}
          {visiblePosts.length === 0 ? <p className="rounded-xl bg-surface p-4 text-center text-[13px] text-text-secondary">{t("api.empty")}</p> : null}
        </div>
        <div className="sticky top-6 w-[296px] shrink-0">
          <WebRail />
        </div>
      </div>
    </div>
  );
}

export function CommunityFeedPage() {
  const { t } = useTranslation("community");
  const postsQuery = useQuery({
    queryKey: ["community", "posts"],
    queryFn: () => listCommunityPosts({ size: 20 }),
    staleTime: 30_000,
  });
  const posts = postsQuery.data?.items ?? [];

  if (postsQuery.isPending) {
    return <div className="p-6 text-sm text-text-secondary">{t("api.loading")}</div>;
  }

  if (postsQuery.isError) {
    return <div className="p-6 text-sm text-danger-text">{t("api.error")}</div>;
  }

  return (
    <>
      <div className="lg:hidden">
        <MobileFeed posts={posts} />
      </div>
      <div className="hidden lg:block">
        <WebFeed posts={posts} />
      </div>
    </>
  );
}

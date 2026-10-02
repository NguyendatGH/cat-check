import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import {
  ArrowLeft,
  BadgeCheck,
  Bookmark,
  Bot,
  Building2,
  Calendar,
  Camera,
  ChevronDown,
  CornerUpLeft,
  Heart,
  Home,
  Image as ImageIcon,
  MessageSquare,
  MoreHorizontal,
  PhoneCall,
  ScanLine,
  Send,
  Share2,
  ShieldCheck,
  Siren,
  Smile,
  Sparkles,
  Star,
  Stethoscope,
  ThumbsUp,
  ZoomIn,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import {
  DESIGN_MOCK_CASE_PROFILE,
  DESIGN_MOCK_COMMENTS,
  DESIGN_MOCK_COMMENTS_META,
  DESIGN_MOCK_EMERGENCY_CARD,
  DESIGN_MOCK_MOBILE_THREAD,
  DESIGN_MOCK_SIMILAR_CASES,
  DESIGN_MOCK_THREAD,
  DESIGN_MOCK_VET_OPINION,
  DESIGN_MOCK_VET_PROFILE,
  type MockBodySegment,
  type MockPhGauge,
  type MockTone,
} from "./mockData";

/**
 * `/community/posts/:postId` — chi tiết một thảo luận.
 *
 * Hai cây DOM tách rời (giống `CatTrendsPage` + `webTrends.tsx`):
 *  - `< lg`: `Chi tiết Thảo luận Cộng đồng` — thẻ bài viết + thước chuyển dịch pH, ghi chú
 *    chuyên môn dạng trích dẫn, danh sách bình luận, ô soạn bình luận dính đáy trên bottom nav.
 *  - `>= lg`: `Web - Chi tiết Thảo luận Ca Bệnh & Ý kiến Bác sĩ Thú y` — breadcrumb, cột
 *    trái (bài viết + ý kiến chuyên môn + bình luận), cột phải 296px (hồ sơ bác sĩ, hồ sơ
 *    lâm sàng ca bệnh, ca tương tự, báo động cấp cứu).
 *
 * KHÔNG tự thêm padding ngang ở `lg` — `AppLayout` đã cấp hộp nội dung 944px kèm padding.
 *
 * DỮ LIỆU: 100% mock (`mockData.ts`). `:postId` hiện KHÔNG dùng để tra cứu gì vì Phase 1
 * không có bảng `post`/`comment` lẫn API (p4, mục Phase 2) — mọi route id đều hiển thị cùng
 * một thảo luận mẫu của thiết kế.
 */

const TONE_CHIP: Record<MockTone, string> = {
  primary: "bg-chip-bg text-primary-dark",
  secondary: "bg-secondary/35 text-secondary-text-on",
  success: "bg-success-bg text-success-text",
  danger: "bg-danger-bg text-danger-text",
  neutral: "bg-background-alt text-text-secondary",
};

const PH_CHIP_TONE: Record<MockTone, string> = {
  primary: "bg-chip-bg text-primary-dark",
  secondary: "bg-secondary/30 text-secondary-text-on",
  success: "bg-success-bg text-success-text",
  danger: "bg-danger-bg text-danger-text",
  neutral: "bg-background-alt text-text-secondary",
};

function BodyText({ segments, className }: { segments: readonly MockBodySegment[]; className?: string }) {
  return (
    <p className={className}>
      {segments.map((seg, i) =>
        seg.kind === "text" ? (
          <span key={`t${String(i)}`}>{seg.value}</span>
        ) : (
          <span
            key={`${seg.value}-${String(i)}`}
            className={cn("mx-0.5 inline-block rounded px-1.5 py-0.5 text-[13px] font-bold", PH_CHIP_TONE[seg.tone])}
          >
            {seg.value}
          </span>
        ),
      )}
    </p>
  );
}

/** Thước pH ngang: gradient toan → chuẩn → kiềm, chấm ở vị trí lấy từ mock. */
function PhScale({ gauge }: { gauge: MockPhGauge }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="relative h-2 rounded-full bg-gradient-to-r from-secondary via-success to-primary">
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

/* =============================== BẢN MOBILE (< lg) =============================== */

function MobileThread() {
  const { t } = useTranslation("community");
  const navigate = useNavigate();
  const thread = DESIGN_MOCK_MOBILE_THREAD;

  return (
    <div className="flex flex-col gap-4 px-4 pb-24 pt-3">
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => {
            void navigate(-1);
          }}
          aria-label={t("mobile.detailTitle")}
          className="-ml-2 flex size-10 items-center justify-center rounded-full text-text-primary hover:bg-background-alt"
        >
          <ArrowLeft size={19} aria-hidden="true" />
        </button>
        <h1 className="flex-1 text-[17px] font-bold text-text-primary">{t("mobile.detailTitle")}</h1>
        <button
          type="button"
          aria-label={t("post.moreOptions")}
          className="-mr-2 flex size-10 items-center justify-center rounded-full text-text-secondary hover:bg-background-alt"
        >
          <MoreHorizontal size={18} aria-hidden="true" />
        </button>
      </div>

      <article className="rounded-2xl bg-surface p-4 shadow-brand-md">
        <div className="flex items-start gap-3">
          <img src={thread.author.avatarUrl} alt="" className="size-11 shrink-0 rounded-full object-cover" />
          <div className="min-w-0 flex-1">
            <p className="text-[15px] font-bold leading-tight text-text-primary">{thread.author.name}</p>
            <div className="flex flex-wrap items-center gap-2 pt-1">
              <span
                className={cn(
                  "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold",
                  TONE_CHIP[thread.author.roleBadgeTone],
                )}
              >
                <BadgeCheck size={10} aria-hidden="true" />
                {thread.author.roleBadge}
              </span>
              <span className="text-[11px] text-text-tertiary">{thread.author.meta}</span>
            </div>
          </div>
        </div>

        <BodyText segments={thread.body} className="pt-3 text-[15px] leading-relaxed text-text-primary" />

        <div className="mt-4 rounded-xl bg-background-alt p-3">
          <div className="flex items-start justify-between gap-2">
            <span className="flex items-start gap-2">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-primary-dark text-white">
                <Sparkles size={13} aria-hidden="true" />
              </span>
              <span className="max-w-[140px] text-[12px] font-bold leading-tight text-primary-dark">
                {thread.dataCard.title}
              </span>
            </span>
            <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-success-bg px-2.5 py-1 text-[11px] font-bold text-success-text">
              <span className="size-1.5 rounded-full bg-success" aria-hidden="true" />
              {thread.dataCard.statusLabel}
            </span>
          </div>

          <div className="mt-3 rounded-lg bg-surface p-3">
            <div className="flex items-start justify-between gap-3">
              <span className="text-[13px] text-text-secondary">{thread.dataCard.shiftLabel}</span>
              <span className="flex shrink-0 items-baseline gap-1.5 text-[18px] font-bold">
                <span className="text-secondary-text-on">{thread.dataCard.shiftFrom}</span>
                <span className="text-text-tertiary" aria-hidden="true">
                  {"\u2192"}
                </span>
                <span className="text-success-text">{thread.dataCard.shiftTo}</span>
              </span>
            </div>
            <div className="pt-3">
              <div className="relative h-2 rounded-full bg-gradient-to-r from-secondary via-success to-primary">
                <span
                  className="absolute top-1/2 size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-primary-darker shadow-xs"
                  style={{ left: `${String(thread.dataCard.markerPercent)}%` }}
                  aria-hidden="true"
                />
              </div>
              <div className="flex items-center justify-between pt-1.5 text-[10px]">
                <span className="text-text-tertiary">{thread.dataCard.lowLabel}</span>
                <span className="font-bold text-success-text">{thread.dataCard.idealLabel}</span>
                <span className="text-text-tertiary">{thread.dataCard.highLabel}</span>
              </div>
            </div>
          </div>
        </div>

        <div className="relative mt-4 overflow-hidden rounded-xl">
          <img src={thread.photo.url} alt="" className="aspect-[326/256] w-full object-cover" />
          <span className="absolute bottom-2 right-2 inline-flex items-center gap-1.5 rounded-full bg-primary-darker/80 px-2.5 py-1 text-[11px] font-semibold text-white">
            <span className="size-1.5 rounded-full bg-secondary" aria-hidden="true" />
            {thread.photo.caption}
          </span>
        </div>

        <div className="flex items-center gap-5 pt-4 text-[13px] text-text-secondary">
          <span className="flex items-center gap-1.5">
            <Heart size={16} aria-hidden="true" />
            {thread.likeCount}
            <span className="sr-only">{t("post.like")}</span>
          </span>
          <span className="flex items-center gap-1.5">
            <MessageSquare size={16} aria-hidden="true" />
            {thread.commentCount}
            <span className="sr-only">{t("post.comment")}</span>
          </span>
          <span className="ml-auto flex items-center gap-1.5">
            <Bookmark size={16} aria-hidden="true" />
            {thread.saveLabel}
          </span>
        </div>
      </article>

      <section className="rounded-2xl bg-deco-backdrop/70 p-4">
        <div className="flex items-start gap-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-dark text-white">
            <Stethoscope size={16} aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h2 className="text-[17px] font-bold leading-tight text-primary-dark">{thread.expertNote.title}</h2>
            <p className="pt-0.5 text-[12px] leading-relaxed text-text-secondary">{thread.expertNote.subtitle}</p>
          </div>
        </div>
        <blockquote className="mt-3 rounded-xl bg-surface p-3 text-[13px] leading-relaxed text-text-primary">
          {thread.expertNote.quote}
        </blockquote>
        <div className="flex flex-wrap items-center justify-between gap-2 pt-3">
          <span className="flex items-center gap-1.5 text-[11px] font-semibold text-primary-dark">
            <ShieldCheck size={12} aria-hidden="true" />
            {thread.expertNote.verifyLabel}
          </span>
          <span className="text-[11px] font-bold text-primary-dark">{thread.expertNote.linkLabel}</span>
        </div>
      </section>

      <div className="flex items-baseline justify-between gap-2">
        <h2 className="text-[17px] font-bold text-text-primary">
          {thread.commentsTitle} <span className="font-normal text-text-tertiary">{thread.commentsCount}</span>
        </h2>
        <span className="flex items-center gap-1 text-[12px] text-text-secondary">
          {t("detail.sortLabel")}
          <span className="font-bold text-primary-dark">{thread.sortValue}</span>
          <ChevronDown size={12} aria-hidden="true" />
        </span>
      </div>

      {thread.comments.map((comment) => (
        <article key={comment.id} className="rounded-2xl bg-surface p-4 shadow-brand-md">
          <div className="flex items-start gap-3">
            <img src={comment.author.avatarUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[15px] font-bold text-text-primary">{comment.author.name}</span>
                <span
                  className={cn(
                    "rounded-full px-2 py-0.5 text-[10px] font-bold",
                    TONE_CHIP[comment.author.roleBadgeTone],
                  )}
                >
                  {comment.author.roleBadge}
                </span>
              </div>
              <p className="pt-0.5 text-[11px] text-text-tertiary">{comment.author.meta}</p>
            </div>
            <button
              type="button"
              aria-label={t("post.moreOptions")}
              className="-mr-1 flex size-8 shrink-0 items-center justify-center rounded-full text-text-tertiary hover:bg-background-alt"
            >
              <MoreHorizontal size={15} aria-hidden="true" />
            </button>
          </div>
          <p className="pl-13 pt-2 text-[13px] leading-relaxed text-text-primary">{comment.body}</p>
          <div className="flex items-center justify-between pl-13 pt-3">
            <span className="text-[12px] font-semibold text-text-secondary">{t("detail.commentReply")}</span>
            <span
              className={cn(
                "flex items-center gap-1.5 text-[12px] font-bold",
                comment.likeTone === "secondary" ? "text-secondary-text-on" : "text-text-secondary",
              )}
            >
              <ThumbsUp size={14} aria-hidden="true" />
              {comment.likeCount}
            </span>
          </div>
        </article>
      ))}

      {/* Ô soạn bình luận dính đáy, nằm ngay trên bottom nav 64px của AppLayout. */}
      <div className="fixed inset-x-0 bottom-16 z-[var(--z-dropdown)] mx-auto flex w-full max-w-[480px] items-center gap-2 bg-background px-4 py-3">
        <label className="flex min-w-0 flex-1 items-center gap-2 rounded-full bg-background-alt px-4 py-2.5">
          <span className="sr-only">{t("detail.mobileCommentPlaceholder")}</span>
          <input
            type="text"
            placeholder={thread.composerPlaceholder}
            className="min-w-0 flex-1 bg-transparent text-[13px] text-text-primary outline-none placeholder:text-text-tertiary"
          />
          <button
            type="button"
            aria-label={t("detail.mobileAttachPhoto")}
            className="flex size-7 shrink-0 items-center justify-center text-text-secondary"
          >
            <Camera size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            aria-label={t("detail.mobileAttachEmoji")}
            className="flex size-7 shrink-0 items-center justify-center text-text-secondary"
          >
            <Smile size={16} aria-hidden="true" />
          </button>
        </label>
        <button
          type="button"
          aria-label={t("detail.mobileSend")}
          className="flex size-11 shrink-0 items-center justify-center rounded-full bg-primary text-white shadow-brand-md"
        >
          <Send size={17} aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}

/* =============================== BẢN DESKTOP (>= lg) =============================== */

function WebPostCard() {
  const { t } = useTranslation("community");
  const thread = DESIGN_MOCK_THREAD;

  return (
    <article className="rounded-2xl bg-surface p-6 shadow-brand-md">
      <div className="flex items-start gap-3">
        <img src={thread.author.avatarUrl} alt="" className="size-12 shrink-0 rounded-full object-cover" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[16px] font-bold text-text-primary">{thread.author.name}</span>
            <span
              className={cn("rounded-full px-2 py-0.5 text-[10px] font-bold", TONE_CHIP[thread.author.roleBadgeTone])}
            >
              {thread.author.roleBadge}
            </span>
          </div>
          <p className="pt-1 text-[12px] leading-relaxed text-text-secondary">{thread.author.meta}</p>
        </div>
        <span className="flex shrink-0 items-center gap-2 rounded-xl bg-success-bg px-3 py-2 text-[11px] font-bold leading-tight text-success-text">
          <BadgeCheck size={14} className="shrink-0" aria-hidden="true" />
          {thread.verifiedBadge}
        </span>
      </div>

      <div className="flex flex-wrap gap-2 pt-4">
        {thread.tags.map((tag) => (
          <span key={tag} className="rounded-md bg-chip-bg px-2 py-1 text-[11px] font-semibold text-primary-dark">
            {tag}
          </span>
        ))}
      </div>

      <h1 className="pt-3 text-[22px] font-bold leading-snug text-text-primary">{thread.title}</h1>
      <p className="pt-2 text-[14px] leading-relaxed text-text-secondary">{thread.body}</p>

      <div className="grid gap-4 pt-4 md:grid-cols-[minmax(0,264fr)_minmax(0,264fr)]">
        <div>
          <div className="relative overflow-hidden rounded-xl">
            <img src={thread.photo.url} alt="" className="aspect-[264/224] w-full object-cover" />
            <span className="absolute bottom-2 left-2 flex items-center gap-1.5 rounded-full bg-primary-darker/80 px-2.5 py-1 text-[11px] font-semibold text-white">
              <span className="size-1.5 rounded-full bg-secondary" aria-hidden="true" />
              {thread.photo.caption}
            </span>
            <span className="absolute bottom-2 right-2 rounded-full bg-surface/90 px-2.5 py-1 text-[11px] font-semibold text-text-primary">
              {thread.photo.chip}
            </span>
          </div>
          <p className="flex items-center justify-between gap-2 pt-2 text-[12px] font-semibold text-text-secondary">
            <span className="flex items-center gap-1.5">
              <ZoomIn size={14} className="text-primary-dark" aria-hidden="true" />
              {thread.zoomNote.title}
            </span>
            <span className="shrink-0 text-[11px] font-normal text-text-tertiary">{thread.zoomNote.meta}</span>
          </p>
        </div>

        <div className="flex flex-col gap-3 rounded-xl bg-background-alt p-4">
          <div className="flex items-start justify-between gap-2">
            <span className="flex items-start gap-2">
              <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary-dark text-white">
                <Bot size={15} aria-hidden="true" />
              </span>
              <span className="max-w-[110px] text-[14px] font-bold leading-tight text-text-primary">
                {thread.analysis.title}
              </span>
            </span>
            <span className="shrink-0 rounded-full bg-chip-bg px-2.5 py-1 text-[11px] font-semibold text-primary-dark">
              {thread.analysis.confidence}
            </span>
          </div>

          <div className="rounded-lg bg-surface p-3">
            <div className="flex items-start justify-between gap-2">
              <span className="text-[10px] font-bold tracking-[0.5px] text-text-tertiary">
                {thread.analysis.metricLabel}
              </span>
              <span className="shrink-0 rounded-md bg-secondary/35 px-2 py-1 text-[11px] font-bold leading-tight text-secondary-text-on">
                {thread.analysis.statusLabel}
              </span>
            </div>
            <p className="pt-1 text-[30px] font-bold leading-none text-primary-dark">{thread.analysis.gauge.value}</p>
            <p className="pt-2 text-[11px] text-text-tertiary">{thread.analysis.referenceLabel}</p>
          </div>

          <PhScale gauge={thread.analysis.gauge} />

          <div className="flex items-start justify-between gap-2 text-[11px]">
            <span className="text-text-tertiary">{thread.analysis.sampleId}</span>
            <span className="shrink-0 text-right font-semibold text-primary-dark">{thread.analysis.algorithm}</span>
          </div>
          <div className="flex items-start justify-between gap-2 text-[11px]">
            <span className="flex items-center gap-1.5 text-text-secondary">
              <BadgeCheck size={12} className="shrink-0 text-success" aria-hidden="true" />
              {thread.analysis.lightCheck}
            </span>
            <span className="shrink-0 font-semibold text-primary-dark">{thread.analysis.chartLink}</span>
          </div>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-6 border-t border-border/50 pt-4 text-[13px] font-semibold text-text-secondary">
        <span className="flex items-center gap-1.5">
          <Heart size={16} className="text-danger" fill="currentColor" aria-hidden="true" />
          {thread.likeCount} {t("post.like")}
        </span>
        <span className="flex items-center gap-1.5">
          <MessageSquare size={16} aria-hidden="true" />
          {thread.commentCount} {t("post.comment")}
        </span>
        <span className="flex items-center gap-1.5">
          <Share2 size={16} aria-hidden="true" />
          {thread.shareCount} {t("post.share")}
        </span>
      </div>
      <p className="flex items-center gap-1.5 pt-2 text-[12px] text-success-text">
        <Bookmark size={13} aria-hidden="true" />
        {thread.savedNote}
      </p>
    </article>
  );
}

function WebVetOpinion() {
  const vet = DESIGN_MOCK_VET_OPINION;
  return (
    <section className="overflow-hidden rounded-2xl border-l-4 border-primary-dark bg-deco-backdrop/60 p-6 shadow-brand-md">
      <div className="flex items-start gap-3">
        <img src={vet.avatarUrl} alt="" className="size-14 shrink-0 rounded-full object-cover" />
        <div className="min-w-0 flex-1">
          <h2 className="text-[18px] font-bold leading-tight text-primary-dark">{vet.name}</h2>
          <span className="mt-1 inline-block rounded-md bg-primary-dark px-2 py-0.5 text-[10px] font-bold tracking-[0.4px] text-white">
            {vet.advisorBadge}
          </span>
          <p className="pt-1.5 text-[12px] leading-relaxed text-text-secondary">{vet.org}</p>
          <p className="pt-1 text-[11px] italic text-text-tertiary">{vet.timestamp}</p>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          <span className="flex items-center gap-1.5 rounded-full bg-secondary px-3 py-1.5 text-[11px] font-bold text-secondary-text-on">
            <ShieldCheck size={12} aria-hidden="true" />
            {vet.opinionBadge}
          </span>
          <span className="text-[10px] text-text-tertiary">{vet.verifyCode}</span>
        </div>
      </div>

      <div className="mt-4 rounded-xl bg-surface p-4">
        <p className="text-[14px] leading-relaxed text-text-primary">{vet.greeting}</p>
        <p className="pt-3 text-[14px] leading-relaxed text-text-primary">{vet.explanation}</p>
      </div>

      <div className="mt-4 rounded-xl bg-surface p-4">
        <h3 className="flex items-center gap-2 text-[15px] font-bold text-primary-dark">
          <Stethoscope size={16} aria-hidden="true" />
          {vet.stepsTitle}
        </h3>
        <ol className="flex flex-col gap-3 pt-3">
          {vet.steps.map((step) => (
            <li key={step.no} className="flex gap-3">
              <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary-dark text-[11px] font-bold text-white">
                {step.no}
              </span>
              <p className="text-[13px] leading-relaxed text-text-secondary">
                <span className="font-bold text-primary-dark">{step.title}</span> {step.body}
              </p>
            </li>
          ))}
        </ol>
      </div>

      <div className="flex flex-wrap items-center gap-3 pt-4">
        <span className="flex items-center gap-2 rounded-xl bg-success-bg px-4 py-2.5 text-[12px] font-semibold text-success-text">
          <ThumbsUp size={14} aria-hidden="true" />
          {vet.helpfulLabel}
        </span>
        <span className="flex items-center gap-2 rounded-xl bg-surface px-4 py-2.5 text-[12px] font-semibold text-text-secondary">
          <CornerUpLeft size={14} aria-hidden="true" />
          {vet.askMoreLabel}
        </span>
      </div>
      <button
        type="button"
        className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-5 py-3 text-[13px] font-bold text-white hover:bg-primary"
      >
        <Calendar size={15} aria-hidden="true" />
        {vet.bookingLabel}
      </button>
    </section>
  );
}

function WebComments() {
  const { t } = useTranslation("community");
  return (
    <section className="rounded-2xl bg-surface p-6 shadow-brand-md">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-[19px] font-bold leading-tight text-text-primary">{t("detail.commentsTitle")}</h2>
        <span className="rounded-lg bg-chip-bg px-2.5 py-1 text-[11px] font-bold text-primary-dark">
          {DESIGN_MOCK_COMMENTS_META.countBadge}
        </span>
        <span className="ml-auto flex items-center gap-1.5 text-[12px] text-text-secondary">
          {t("detail.sortLabel")}
          <span className="font-bold text-text-primary">{DESIGN_MOCK_COMMENTS_META.sortValue}</span>
          <ChevronDown size={13} aria-hidden="true" />
        </span>
      </div>

      <div className="mt-4 flex gap-3 rounded-xl bg-background-alt p-4">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-dark text-white">
          <Stethoscope size={15} aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <label className="block">
            <span className="sr-only">{t("detail.commentPlaceholder")}</span>
            <textarea
              rows={2}
              placeholder={t("detail.commentPlaceholder")}
              className="w-full resize-none rounded-xl bg-surface px-4 py-3 text-[13px] text-text-primary outline-none placeholder:text-text-tertiary"
            />
          </label>
          <div className="flex flex-wrap items-center gap-4 pt-3">
            <span className="flex items-center gap-1.5 text-[12px] font-semibold text-text-secondary">
              <ImageIcon size={14} aria-hidden="true" />
              {t("detail.commentAttachPhoto")}
            </span>
            <span className="flex items-center gap-1.5 text-[12px] font-semibold text-text-secondary">
              <ScanLine size={14} aria-hidden="true" />
              {t("detail.commentAttachScan")}
            </span>
            <button
              type="button"
              className="ml-auto rounded-xl bg-primary-dark px-5 py-2.5 text-[12px] font-bold text-white hover:bg-primary"
            >
              {t("detail.commentSubmit")}
            </button>
          </div>
        </div>
      </div>

      <ul className="flex flex-col divide-y divide-border/50">
        {DESIGN_MOCK_COMMENTS.map((comment) => (
          <li key={comment.id} className="py-5">
            <div className="flex items-start gap-3">
              <img src={comment.author.avatarUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-[14px] font-bold text-text-primary">{comment.author.name}</span>
                  {comment.author.roleBadge ? (
                    <span
                      className={cn(
                        "rounded-full px-2 py-0.5 text-[10px] font-bold",
                        TONE_CHIP[comment.author.roleBadgeTone ?? "neutral"],
                      )}
                    >
                      {comment.author.roleBadge}
                    </span>
                  ) : null}
                </div>
                <p className="pt-0.5 text-[11px] text-text-tertiary">{comment.author.meta}</p>
              </div>
              <button
                type="button"
                aria-label={t("post.moreOptions")}
                className="flex size-7 shrink-0 items-center justify-center rounded-full text-text-tertiary hover:bg-background-alt"
              >
                <MoreHorizontal size={15} aria-hidden="true" />
              </button>
            </div>

            <p className="pl-13 pt-3 text-[13px] leading-relaxed text-text-primary">{comment.body}</p>

            <div className="flex items-center gap-5 pl-13 pt-3 text-[12px] font-semibold text-text-secondary">
              <span className="flex items-center gap-1.5">
                <ThumbsUp size={13} aria-hidden="true" />
                {comment.likeCount} {t("detail.commentLike")}
              </span>
              <span className="flex items-center gap-1.5">
                <CornerUpLeft size={13} aria-hidden="true" />
                {t("detail.commentReply")}
              </span>
            </div>

            {comment.reply ? (
              <div className="ml-13 mt-4 rounded-xl border-l-2 border-primary bg-background-alt p-4">
                <p className="flex flex-wrap items-baseline gap-2">
                  <span className="text-[13px] font-bold text-primary-dark">{comment.reply.name}</span>
                  <span className="text-[11px] text-text-tertiary">{comment.reply.meta}</span>
                </p>
                <p className="pt-2 text-[13px] leading-relaxed text-text-secondary">{comment.reply.body}</p>
              </div>
            ) : null}
          </li>
        ))}
      </ul>

      <button
        type="button"
        className="w-full rounded-xl bg-background-alt px-4 py-3 text-[13px] font-bold text-primary-dark hover:bg-chip-bg"
      >
        {DESIGN_MOCK_COMMENTS_META.moreCta}
      </button>
    </section>
  );
}

/** Đường pH 7 ngày ở cột phải — toạ độ `y` (%) lấy nguyên từ mock, không tính ngưỡng ở đây. */
function MiniPhLine() {
  const pts = DESIGN_MOCK_CASE_PROFILE.historyPoints;
  const path = pts
    .map((p, i) => `${i === 0 ? "M" : "L"}${String((i / (pts.length - 1)) * 100)},${String(p.y)}`)
    .join(" ");
  const last = pts[pts.length - 1];
  return (
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="h-14 w-full" aria-hidden="true">
      <path d={path} fill="none" stroke="currentColor" strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
      {pts.map((p, i) => (
        <circle
          key={p.label}
          cx={(i / (pts.length - 1)) * 100}
          cy={p.y}
          r="2"
          fill="currentColor"
          vectorEffect="non-scaling-stroke"
        />
      ))}
      <circle cx={100} cy={last.y} r="3.5" className="fill-secondary" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

function WebRail() {
  const { t } = useTranslation("community");
  const vet = DESIGN_MOCK_VET_PROFILE;
  const profile = DESIGN_MOCK_CASE_PROFILE;

  return (
    <div className="flex flex-col gap-4">
      <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
        <div className="flex items-start gap-3">
          <img src={vet.avatarUrl} alt="" className="size-14 shrink-0 rounded-full object-cover" />
          <div className="min-w-0">
            <p className="text-[15px] font-bold leading-tight text-text-primary">{vet.name}</p>
            <p className="pt-1 text-[11px] font-semibold leading-tight text-primary-dark">{vet.membership}</p>
            <p className="pt-0.5 text-[11px] text-text-tertiary">{vet.experience}</p>
          </div>
        </div>

        <div className="mt-3 grid grid-cols-3 gap-2">
          {vet.stats.map((stat) => (
            <div key={stat.label} className={cn("rounded-lg px-2 py-2 text-center", TONE_CHIP[stat.tone])}>
              <p className="text-[14px] font-bold leading-tight">{stat.value}</p>
              <p className="pt-0.5 text-[10px] opacity-80">{stat.label}</p>
            </div>
          ))}
        </div>

        <p className="flex items-start gap-2 pt-3 text-[11px] leading-relaxed text-text-secondary">
          <Calendar size={13} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
          <span>
            {vet.scheduleLabel} <span className="font-bold text-text-primary">{vet.scheduleValue}</span>
          </span>
        </p>
        <p className="flex items-start gap-2 pt-1.5 text-[11px] leading-relaxed text-text-secondary">
          <Building2 size={13} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
          {vet.clinic}
        </p>

        <button
          type="button"
          className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-2.5 text-[12px] font-bold text-white hover:bg-primary"
        >
          <MessageSquare size={14} aria-hidden="true" />
          {vet.messageCta}
        </button>
        <button
          type="button"
          className="mt-2 w-full rounded-xl bg-chip-bg px-4 py-2.5 text-[12px] font-semibold text-primary-dark hover:bg-info"
        >
          {vet.articlesCta}
        </button>
      </section>

      <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
        <div className="flex items-start justify-between gap-2">
          <h2 className="max-w-[140px] text-[11px] font-bold tracking-[0.4px] text-text-tertiary">{profile.title}</h2>
          <span className="shrink-0 rounded-full bg-secondary/35 px-2.5 py-1 text-[11px] font-bold text-secondary-text-on">
            {profile.catBadge}
          </span>
        </div>

        <div className="mt-3 flex items-center gap-3 rounded-xl bg-background-alt p-2.5">
          <img src={profile.catPhotoUrl} alt="" className="size-11 shrink-0 rounded-lg object-cover" />
          <div className="min-w-0">
            <p className="text-[14px] font-bold leading-tight text-text-primary">{profile.catName}</p>
            <p className="pt-0.5 text-[11px] text-text-secondary">{profile.catMeta}</p>
          </div>
        </div>

        <dl className="flex flex-col divide-y divide-border/50 pt-2">
          {profile.rows.map((row) => (
            <div key={row.label} className="flex items-start justify-between gap-3 py-2">
              <dt className="text-[11px] text-text-secondary">{row.label}</dt>
              <dd
                className={cn(
                  "shrink-0 text-right text-[11px] font-bold",
                  row.highlight ? "text-primary-dark" : "text-text-primary",
                )}
              >
                {row.value}
              </dd>
            </div>
          ))}
        </dl>

        <div className="mt-2 rounded-xl bg-background-alt p-3">
          <p className="flex flex-wrap items-baseline justify-between gap-2 text-[11px] text-text-secondary">
            {profile.historyTitle}
            <span className="font-bold text-primary-dark">{profile.historyToday}</span>
          </p>
          <div className="pt-2 text-primary">
            <MiniPhLine />
          </div>
          <div className="flex items-center justify-between pt-1 text-[10px] text-text-tertiary">
            {profile.historyPoints.map((point, i) => (
              <span key={point.label} className={i === profile.historyPoints.length - 1 ? "font-bold text-primary" : ""}>
                {point.label}
              </span>
            ))}
          </div>
        </div>
      </section>

      <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
        <h2 className="flex items-center gap-2 text-[15px] font-bold text-text-primary">
          <Star size={15} className="text-secondary" fill="currentColor" aria-hidden="true" />
          {DESIGN_MOCK_SIMILAR_CASES.title}
        </h2>
        <ul className="flex flex-col gap-2 pt-3">
          {DESIGN_MOCK_SIMILAR_CASES.items.map((item) => (
            <li key={item.tag} className="rounded-xl bg-background-alt p-3">
              <div className="flex items-start justify-between gap-2">
                <span className="min-w-0 truncate text-[11px] font-bold text-primary-dark">{item.tag}</span>
                <span className={cn("shrink-0 rounded px-1.5 py-0.5 text-[10px] font-bold", TONE_CHIP[item.badgeTone])}>
                  {item.badge}
                </span>
              </div>
              <p className="pt-1 text-[12px] font-semibold leading-snug text-text-primary">{item.title}</p>
              <p className="flex flex-wrap items-center gap-1.5 pt-1.5 text-[10px] text-text-tertiary">
                {item.meta}
                <span className="size-1 rounded-full bg-border-strong" aria-hidden="true" />
                {item.interest}
              </p>
            </li>
          ))}
        </ul>
        <p className="pt-3 text-[11px] font-bold text-primary-dark">{DESIGN_MOCK_SIMILAR_CASES.libraryCta}</p>
      </section>

      <section className="rounded-2xl bg-primary-dark p-4">
        <h2 className="flex items-center gap-2 text-[15px] font-bold leading-tight text-white">
          <Siren size={16} className="shrink-0 text-secondary" aria-hidden="true" />
          {DESIGN_MOCK_EMERGENCY_CARD.title}
        </h2>
        <p className="pt-2 text-[11px] leading-relaxed text-on-primary-subtle">{DESIGN_MOCK_EMERGENCY_CARD.body}</p>
        <p className="mt-3 flex items-center justify-center gap-2 rounded-xl bg-secondary px-3 py-2.5 text-center text-[12px] font-bold text-secondary-text-on">
          <PhoneCall size={14} className="shrink-0" aria-hidden="true" />
          {DESIGN_MOCK_EMERGENCY_CARD.hotline}
        </p>
      </section>

      <p className="sr-only">{t("detail.pinnedByVet")}</p>
    </div>
  );
}

function WebThread() {
  const { t } = useTranslation("community");
  const thread = DESIGN_MOCK_THREAD;

  return (
    <div className="flex flex-col gap-5">
      <nav className="flex flex-wrap items-center gap-3 text-[12px] text-text-secondary">
        <Link to="/community" className="flex items-center gap-1.5 font-semibold hover:text-primary-dark">
          <Home size={13} aria-hidden="true" />
          {thread.breadcrumb[0]}
        </Link>
        <span aria-hidden="true">{"\u203A"}</span>
        <span>{thread.breadcrumb[1]}</span>
        <span aria-hidden="true">{"\u203A"}</span>
        <span className="rounded-md bg-chip-bg px-2 py-1 text-[11px] font-bold text-primary-dark">
          {thread.caseCode}
        </span>

        <span className="ml-auto flex items-center gap-2 rounded-full bg-success-bg px-3 py-1.5 text-[11px] font-semibold text-success-text">
          <span className="size-2 rounded-full bg-success" aria-hidden="true" />
          {thread.onlineBadge}
        </span>
        <span className="flex items-center gap-1.5 rounded-xl bg-surface px-3 py-1.5 text-[11px] font-semibold text-text-secondary">
          <Bookmark size={13} aria-hidden="true" />
          {t("detail.savePost")}
        </span>
        <span className="flex items-center gap-1.5 rounded-xl bg-surface px-3 py-1.5 text-[11px] font-semibold text-text-secondary">
          <Share2 size={13} aria-hidden="true" />
          {t("detail.sharePost")}
        </span>
      </nav>

      <div className="flex items-start gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          <WebPostCard />
          <WebVetOpinion />
          <WebComments />
        </div>
        <div className="w-[296px] shrink-0">
          <WebRail />
        </div>
      </div>
    </div>
  );
}

export function CommunityPostDetailPage() {
  return (
    <>
      <div className="lg:hidden">
        <MobileThread />
      </div>
      <div className="hidden lg:block">
        <WebThread />
      </div>
    </>
  );
}

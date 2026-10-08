import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { PawPrint, Star } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { formatNumber } from "@/shared/lib/format/formatNumber";
import { formatRelative } from "@/shared/lib/format/formatRelative";
import { createPlaceReview, placeErrorStatus, type PlaceApi, type PlaceReviewApi } from "@/features/place";
import { Stars } from "./placeParts";

/**
 * Đánh giá cơ sở: `GET /places/{id}/reviews` (id, rating, body, createdAt — CỐ Ý không có danh
 * tính người viết) + `POST /places/{id}/reviews` (upsert một đánh giá / tài khoản / cơ sở).
 * Không có tên người viết, tên mèo, nhãn "đã khám thật" hay "bệnh nhân xác thực" — API không có.
 */

const BODY_MAX = 2000;
export const REVIEW_LIMIT = 20;

type SubmitState = "idle" | "submitting" | "success" | "auth" | "error";

function ReviewForm({ placeId }: { placeId: string }) {
  const { t } = useTranslation("map");
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(0);
  const [body, setBody] = useState("");
  const [state, setState] = useState<SubmitState>("idle");
  const [needRating, setNeedRating] = useState(false);

  async function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    if (state === "submitting") return;
    if (rating < 1) {
      setNeedRating(true);
      return;
    }
    setState("submitting");
    try {
      await createPlaceReview(placeId, { rating, body: body.trim() || undefined });
      setState("success");
      setBody("");
      // Điểm trung bình + số đánh giá ở chi tiết và danh sách bản đồ đều đổi theo.
      void queryClient.invalidateQueries({ queryKey: ["place"] });
    } catch (error) {
      const status = placeErrorStatus(error);
      setState(status === 401 || status === 403 ? "auth" : "error");
    }
  }

  return (
    <form
      noValidate
      onSubmit={(event) => {
        void submit(event);
      }}
      className="rounded-xl bg-background-alt p-4"
    >
      <p className="text-[13px] font-bold text-text-primary">{t("reviews.formTitle")}</p>
      <div className="flex items-center gap-1 pt-2" role="group" aria-label={t("reviews.ratingLabel")}>
        {[1, 2, 3, 4, 5].map((value) => (
          <button
            key={value}
            type="button"
            aria-label={t("reviews.ratingValue", { value })}
            aria-pressed={rating === value}
            onClick={() => {
              setRating(value);
              setNeedRating(false);
            }}
            className="flex size-10 items-center justify-center rounded-lg hover:bg-chip-bg"
          >
            <Star
              size={22}
              fill={value <= rating ? "currentColor" : "none"}
              className={value <= rating ? "text-secondary" : "text-border-strong"}
              aria-hidden="true"
            />
          </button>
        ))}
        {rating > 0 ? (
          <span className="pl-1 text-[12px] font-semibold text-text-secondary">
            {t("reviews.ratingValue", { value: rating })}
          </span>
        ) : null}
      </div>
      {needRating ? (
        <p role="alert" className="pt-1 text-[12px] font-semibold text-danger-text">
          {t("reviews.ratingRequired")}
        </p>
      ) : null}
      <label className="block pt-3 text-[12px] font-semibold text-text-secondary">
        {t("reviews.bodyLabel")}
        <textarea
          value={body}
          maxLength={BODY_MAX}
          rows={3}
          onChange={(event) => {
            setBody(event.target.value);
          }}
          placeholder={t("reviews.bodyPlaceholder")}
          className="mt-1.5 block w-full resize-y rounded-lg border border-border bg-surface px-3 py-2 text-[12px] font-normal leading-relaxed text-text-primary outline-none placeholder:text-text-tertiary focus:border-primary"
        />
      </label>
      <p className="pt-2 text-[11px] leading-relaxed text-text-tertiary">{t("reviews.upsertNote")}</p>
      <button
        type="submit"
        disabled={state === "submitting"}
        className="mt-3 min-h-11 rounded-xl bg-primary-dark px-5 text-[13px] font-bold text-white hover:bg-primary disabled:opacity-60"
      >
        {state === "submitting" ? t("reviews.submitting") : t("reviews.submit")}
      </button>
      {state === "success" || state === "auth" || state === "error" ? (
        <p
          role="status"
          className={cn(
            "pt-2 text-[12px] font-semibold",
            state === "success" ? "text-success-text" : "text-danger-text",
          )}
        >
          {t(state === "success" ? "reviews.sent" : state === "auth" ? "reviews.authError" : "reviews.error")}
        </p>
      ) : null}
    </form>
  );
}

function ReviewItem({ review }: { review: PlaceReviewApi }) {
  const { t } = useTranslation("map");
  return (
    <li className="rounded-xl bg-background-alt/70 p-4">
      <div className="flex items-center gap-3">
        <span
          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-chip-bg text-primary-dark"
          aria-hidden="true"
        >
          <PawPrint size={16} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[12px] font-bold text-text-primary">{t("reviews.anonymous")}</p>
          <div className="flex items-center gap-2 pt-0.5">
            <Stars value={review.rating} />
            <span className="sr-only">{t("reviews.ratingValue", { value: review.rating })}</span>
          </div>
        </div>
        <time className="shrink-0 text-[11px] text-text-tertiary" dateTime={review.createdAt}>
          {formatRelative(review.createdAt)}
        </time>
      </div>
      {review.body ? (
        <p className="whitespace-pre-line break-words pt-2.5 text-[13px] leading-relaxed text-text-secondary">
          {review.body}
        </p>
      ) : null}
    </li>
  );
}

interface ReviewsSectionProps {
  place: PlaceApi;
  reviews: PlaceReviewApi[] | undefined;
  loading: boolean;
  failed: boolean;
  onRetry: () => void;
  headingClassName?: string;
}

export function ReviewsSection({ place, reviews, loading, failed, onRetry, headingClassName }: ReviewsSectionProps) {
  const { t } = useTranslation("map");
  const hasReviews = place.reviewCount > 0;
  const list = reviews ?? [];

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 pb-3">
        <h2 className={cn("font-bold", headingClassName)}>{t("reviews.title")}</h2>
        {hasReviews ? (
          <div className="flex items-center gap-2 rounded-full bg-background-alt px-3 py-1.5">
            <span className="text-[14px] font-bold text-text-primary">
              {formatNumber(place.rating, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}
            </span>
            <Stars value={place.rating} size={12} />
            <span className="text-[11px] font-semibold text-text-secondary">
              {t("rating.count", { count: place.reviewCount })}
            </span>
          </div>
        ) : null}
      </div>

      {loading ? (
        <p className="py-3 text-[12px] text-text-tertiary">{t("reviews.loading")}</p>
      ) : failed ? (
        <div role="alert" className="flex flex-wrap items-center gap-3 py-3">
          <p className="text-[12px] text-danger-text">{t("reviews.loadError")}</p>
          <button
            type="button"
            onClick={onRetry}
            className="min-h-9 rounded-lg bg-chip-bg px-3 text-[12px] font-bold text-primary-dark hover:bg-info"
          >
            {t("reviews.retry")}
          </button>
        </div>
      ) : list.length === 0 ? (
        <p className="pb-4 text-[12px] text-text-tertiary">{t("reviews.empty")}</p>
      ) : (
        <>
          <ul className="flex flex-col gap-2.5 pb-4">
            {list.map((review) => (
              <ReviewItem key={review.id} review={review} />
            ))}
          </ul>
          {place.reviewCount > list.length ? (
            <p className="-mt-2 pb-4 text-[11px] text-text-tertiary">
              {t("reviews.showingLatest", { shown: list.length, total: place.reviewCount })}
            </p>
          ) : null}
        </>
      )}

      <ReviewForm placeId={place.id} />
    </>
  );
}

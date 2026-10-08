import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { Info } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api/errors";
import { Button, Input } from "@/shared/ui";
import {
  COMMUNITY_CATEGORIES,
  COMMUNITY_LIMITS,
  useCreateCommunityPost,
  type CommunityCategory,
} from "@/features/community";
import { RulesCard, TagChips } from "./parts";

/**
 * `/community/new` — soạn bài viết mới.
 *
 * Form gửi thật qua `POST /api/v1/community/posts` với đúng bốn field payload backend nhận:
 * `category` (enum QA/TIP/EXPERIENCE), `title` (≤180), `body` (≤10.000), `tags` (≤8 thẻ, mỗi thẻ
 * ≤48 ký tự) — giới hạn lấy từ `@Size` của `CreateCommunityPostRequest`, kiểm ngay ở form.
 * KHÔNG có nút đính kèm ảnh / kết quả quét / hồ sơ khám như ô soạn bài trong design: chưa có
 * endpoint upload nào cho bài viết. Không có nhãn bệnh lý gợi ý — người dùng tự nhập thẻ.
 *
 * Route này nằm trong `TaskLayout` (hộp nội dung 944px đã có padding ngang ở MỌI breakpoint)
 * nên trang tuyệt đối KHÔNG tự thêm padding ngang.
 */

/** Tách chuỗi thẻ "a, #b, a" ⇒ ["a", "b"] (bỏ #, trùng, rỗng). Chưa cắt số lượng — để form báo lỗi. */
function parseTags(raw: string): string[] {
  return [...new Set(raw.split(",").map((tag) => tag.trim().replace(/^#+/, "").trim()))].filter(Boolean);
}

interface FieldErrors {
  title?: string;
  body?: string;
  tags?: string;
}

export function CommunityNewPostPage() {
  const { t } = useTranslation("community");
  const navigate = useNavigate();
  const create = useCreateCommunityPost();
  const bodyId = useId();
  const [category, setCategory] = useState<CommunityCategory>("QA");
  const [tagsRaw, setTagsRaw] = useState("");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [showErrors, setShowErrors] = useState(false);

  const tags = parseTags(tagsRaw);
  const errors: FieldErrors = {
    title: title.trim() ? undefined : t("newPost.errors.titleRequired"),
    body: body.trim() ? undefined : t("newPost.errors.bodyRequired"),
    tags:
      tags.length > COMMUNITY_LIMITS.tags
        ? t("newPost.errors.tooManyTags", { max: COMMUNITY_LIMITS.tags })
        : tags.some((tag) => tag.length > COMMUNITY_LIMITS.tag)
          ? t("newPost.errors.tagTooLong", { max: COMMUNITY_LIMITS.tag })
          : undefined,
  };
  const visible = (key: keyof FieldErrors) => (showErrors ? errors[key] : undefined);

  const submit = () => {
    setShowErrors(true);
    if (errors.title || errors.body || errors.tags || create.isPending) return;
    create.mutate(
      { category, title: title.trim(), body: body.trim(), tags },
      {
        onSuccess: (post) => {
          void navigate(`/community/posts/${post.id}`);
        },
      },
    );
  };

  const submitError = create.isError
    ? isApiError(create.error) && (create.error.status === 401 || create.error.status === 403)
      ? t("newPost.errors.session")
      : t("newPost.errors.submit")
    : null;

  const bodyLength = body.trim().length;

  return (
    <div className="flex flex-col gap-5 pb-6 lg:flex-row lg:items-start lg:gap-6">
      <form
        noValidate
        className="flex min-w-0 flex-1 flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        <header>
          <h1 className="text-[22px] font-bold leading-tight text-primary-dark lg:text-[28px]">{t("newPost.title")}</h1>
          <p className="pt-1.5 text-[13px] leading-relaxed text-text-secondary lg:text-[14px]">
            {t("newPost.subtitle")}
          </p>
        </header>

        <p className="flex gap-2.5 rounded-xl bg-background-alt p-3.5 text-[12px] leading-relaxed text-text-secondary lg:text-[13px]">
          <Info size={16} className="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
          {t("newPost.publishNotice")}
        </p>

        <section className="flex flex-col gap-5 rounded-2xl bg-surface p-5 shadow-brand-md lg:p-6">
          <fieldset className="min-w-0">
            <legend className="text-caption font-semibold text-text-secondary">{t("newPost.categoryLabel")}</legend>
            <div className="grid grid-cols-3 gap-2 pt-2.5 lg:flex lg:flex-wrap">
              {COMMUNITY_CATEGORIES.map((item) => (
                <label
                  key={item}
                  className={cn(
                    "flex min-h-11 cursor-pointer items-center justify-center whitespace-nowrap rounded-full px-2 text-[12px] font-semibold transition-colors lg:px-4 lg:text-[13px]",
                    "has-[:focus-visible]:outline has-[:focus-visible]:outline-[var(--focus-ring-width)] has-[:focus-visible]:outline-offset-[var(--focus-ring-offset)] has-[:focus-visible]:outline-[var(--focus-ring-color)]",
                    category === item
                      ? "bg-primary-dark text-white shadow-brand-md"
                      : "bg-deco-backdrop text-primary-dark hover:bg-chip-bg",
                  )}
                >
                  <input
                    type="radio"
                    name="community-category"
                    value={item}
                    checked={category === item}
                    onChange={() => {
                      setCategory(item);
                    }}
                    className="sr-only"
                  />
                  {t(`category.${item}`)}
                </label>
              ))}
            </div>
          </fieldset>

          <Input
            label={t("newPost.titleLabel")}
            value={title}
            maxLength={COMMUNITY_LIMITS.title}
            onChange={(event) => {
              setTitle(event.target.value);
            }}
            placeholder={t("newPost.titlePlaceholder")}
            error={visible("title")}
            helperText={t("newPost.counter", { count: title.length, max: COMMUNITY_LIMITS.title })}
            required
          />

          <div className="flex flex-col gap-1.5">
            <label htmlFor={bodyId} className="text-caption font-semibold text-text-secondary">
              {t("newPost.bodyLabel")}
            </label>
            <textarea
              id={bodyId}
              rows={8}
              value={body}
              maxLength={COMMUNITY_LIMITS.body}
              onChange={(event) => {
                setBody(event.target.value);
              }}
              placeholder={t("newPost.bodyPlaceholder")}
              required
              aria-invalid={visible("body") ? true : undefined}
              aria-describedby={`${bodyId}-help`}
              className={cn(
                "w-full resize-y rounded-xl border border-border-strong bg-surface px-4 py-3 text-body leading-relaxed text-text-primary placeholder:text-text-tertiary",
                "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
                visible("body") && "border-2 border-danger",
              )}
            />
            <p id={`${bodyId}-help`} className="flex justify-between gap-3 text-small">
              <span className="font-medium text-danger-text">{visible("body")}</span>
              <span className="shrink-0 text-text-tertiary">
                {t("newPost.counter", { count: bodyLength, max: COMMUNITY_LIMITS.body })}
              </span>
            </p>
          </div>

          <div className="flex flex-col gap-2">
            <Input
              label={t("newPost.tagsLabel")}
              value={tagsRaw}
              onChange={(event) => {
                setTagsRaw(event.target.value);
              }}
              placeholder={t("newPost.tagsPlaceholder")}
              error={visible("tags")}
              helperText={t("newPost.tagsHint", { max: COMMUNITY_LIMITS.tags })}
            />
            <TagChips tags={tags} />
          </div>
        </section>

        {submitError ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-[13px] font-medium text-danger-text">
            {submitError}
          </p>
        ) : null}

        <div className="flex flex-col-reverse gap-2.5 sm:flex-row sm:justify-end">
          <Link
            to="/community"
            className="flex min-h-[var(--touch-target-min)] items-center justify-center rounded-xl border border-border px-5 text-body font-semibold text-primary hover:bg-background-alt"
          >
            {t("newPost.cancel")}
          </Link>
          <Button type="submit" loading={create.isPending} className="px-6">
            {create.isPending ? t("newPost.submitting") : t("newPost.submit")}
          </Button>
        </div>
      </form>

      <aside className="w-full lg:sticky lg:top-[88px] lg:w-[296px] lg:shrink-0">
        <RulesCard />
      </aside>
    </div>
  );
}

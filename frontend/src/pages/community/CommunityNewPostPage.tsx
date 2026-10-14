import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { Hash, ImagePlus, Info, Plus, X } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api/errors";
import { Button } from "@/shared/ui";
import {
  COMMUNITY_CATEGORIES,
  COMMUNITY_IMAGE,
  COMMUNITY_LIMITS,
  uploadCommunityPostImage,
  useCommunityTagSuggestions,
  useCreateCommunityPost,
  type CommunityCategory,
} from "@/features/community";
import { RulesCard } from "./parts";

const MAX_IMAGE_MB = Math.round(COMMUNITY_IMAGE.maxBytes / (1024 * 1024));

function normalizeTag(raw: string): string {
  return raw.replace(/^#+/, "").replace(/\s+/g, " ").trim().toLowerCase();
}

interface FieldErrors {
  title?: string;
  body?: string;
  tags?: string;
}

const fieldFocus =
  "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]";

export function CommunityNewPostPage() {
  const { t } = useTranslation("community");
  const navigate = useNavigate();
  const create = useCreateCommunityPost();
  const titleId = useId();
  const bodyId = useId();
  const [category, setCategory] = useState<CommunityCategory>("QA");
  const [tags, setTags] = useState<string[]>([]);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [image, setImage] = useState<File | null>(null);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [showErrors, setShowErrors] = useState(false);

  const errors: FieldErrors = {
    title: title.trim() ? undefined : t("newPost.errors.titleRequired"),
    body: body.trim() ? undefined : t("newPost.errors.bodyRequired"),
    tags: tags.some((tag) => tag.length > COMMUNITY_LIMITS.tag)
      ? t("newPost.errors.tagTooLong", { max: COMMUNITY_LIMITS.tag })
      : undefined,
  };
  const visible = (key: keyof FieldErrors) => (showErrors ? errors[key] : undefined);
  const busy = create.isPending || uploadProgress !== null;

  const submit = () => {
    setShowErrors(true);
    if (errors.title || errors.body || errors.tags || busy) return;
    create.mutate(
      { category, title: title.trim(), body: body.trim(), tags },
      {
        onSuccess: (post) => {
          const goToPost = (imageFailed: boolean) => {
            void navigate(`/community/posts/${post.id}`, { state: imageFailed ? { imageUploadFailed: true } : null });
          };
          if (!image) {
            goToPost(false);
            return;
          }
          setUploadProgress(0);
          uploadCommunityPostImage(post.id, image, setUploadProgress).then(
            () => {
              goToPost(false);
            },
            () => {
              goToPost(true);
            },
          );
        },
      },
    );
  };

  const submitError = create.isError
    ? isApiError(create.error) && (create.error.status === 401 || create.error.status === 403)
      ? t("newPost.errors.session")
      : t("newPost.errors.submit")
    : null;

  const titleNearLimit = title.length > COMMUNITY_LIMITS.title * 0.8;
  const bodyLength = body.trim().length;

  return (
    <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
      <form
        noValidate
        className="flex min-w-0 flex-1 flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        <header className="flex flex-col gap-1">
          <h1 className="text-[22px] font-bold leading-tight text-primary-dark lg:text-[26px]">{t("newPost.title")}</h1>
          <p className="text-[13px] leading-relaxed text-text-secondary">{t("newPost.subtitle")}</p>
        </header>

        <p className="flex items-start gap-2 rounded-lg bg-background-alt px-3 py-2 text-[12px] leading-snug text-text-secondary">
          <Info size={14} className="mt-px shrink-0 text-primary" aria-hidden="true" />
          {t("newPost.publishNotice")}
        </p>

        <section className="flex flex-col rounded-2xl border border-border/60 bg-surface shadow-xs">
          <fieldset className="min-w-0 border-b border-border/60 p-3">
            <legend className="sr-only">{t("newPost.categoryLabel")}</legend>
            <div className="flex gap-1 rounded-xl bg-background-alt p-1">
              {COMMUNITY_CATEGORIES.map((item) => (
                <label
                  key={item}
                  className={cn(
                    "flex min-h-9 flex-1 cursor-pointer items-center justify-center whitespace-nowrap rounded-lg px-2 text-[12px] font-semibold transition-colors duration-200 sm:text-[13px]",
                    "has-[:focus-visible]:outline has-[:focus-visible]:outline-[var(--focus-ring-width)] has-[:focus-visible]:outline-offset-[var(--focus-ring-offset)] has-[:focus-visible]:outline-[var(--focus-ring-color)]",
                    category === item
                      ? "bg-surface text-primary-dark shadow-xs"
                      : "text-text-secondary hover:text-text-primary",
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

          <div className="flex flex-col gap-1 px-4 pt-4">
            <label htmlFor={titleId} className="sr-only">
              {t("newPost.titleLabel")}
            </label>
            <input
              id={titleId}
              value={title}
              maxLength={COMMUNITY_LIMITS.title}
              onChange={(event) => {
                setTitle(event.target.value);
              }}
              placeholder={t("newPost.titlePlaceholder")}
              required
              aria-invalid={visible("title") ? true : undefined}
              className={cn(
                "w-full rounded-md bg-transparent text-[17px] font-bold leading-snug text-text-primary placeholder:font-semibold placeholder:text-text-tertiary",
                fieldFocus,
              )}
            />
            <p className="flex min-h-4 justify-between gap-3 text-[11px]">
              <span className="font-medium text-danger-text">{visible("title")}</span>
              <span className={cn("shrink-0 tabular-nums transition-opacity", titleNearLimit ? "text-warning-text opacity-100" : "text-text-tertiary opacity-0")}>
                {t("newPost.counter", { count: title.length, max: COMMUNITY_LIMITS.title })}
              </span>
            </p>
          </div>

          <div className="flex flex-col gap-1 px-4">
            <label htmlFor={bodyId} className="sr-only">
              {t("newPost.bodyLabel")}
            </label>
            <textarea
              id={bodyId}
              value={body}
              maxLength={COMMUNITY_LIMITS.body}
              onChange={(event) => {
                setBody(event.target.value);
              }}
              placeholder={t("newPost.bodyPlaceholder")}
              required
              aria-invalid={visible("body") ? true : undefined}
              className={cn(
                "field-sizing-content min-h-36 w-full resize-none rounded-md bg-transparent text-[15px] leading-relaxed text-text-primary placeholder:text-text-tertiary",
                fieldFocus,
              )}
            />
            <p className="flex min-h-4 justify-between gap-3 text-[11px]">
              <span className="font-medium text-danger-text">{visible("body")}</span>
              <span className="shrink-0 tabular-nums text-text-tertiary">
                {t("newPost.counter", { count: bodyLength, max: COMMUNITY_LIMITS.body })}
              </span>
            </p>
          </div>

          <div className="px-4 pb-4 pt-2">
            <ImagePicker file={image} onChange={setImage} progress={uploadProgress} disabled={busy} />
          </div>

          <div className="border-t border-border/60 p-4">
            <HashtagField category={category} tags={tags} onChange={setTags} error={visible("tags")} />
          </div>
        </section>

        {submitError ? (
          <p role="alert" className="content-enter rounded-xl bg-danger-bg px-4 py-3 text-[13px] font-medium text-danger-text">
            {submitError}
          </p>
        ) : null}

        <div className="sticky bottom-0 z-10 -mx-4 flex gap-2.5 border-t border-border/60 bg-background/95 px-4 pb-[calc(env(safe-area-inset-bottom)+12px)] pt-3 sm:static sm:mx-0 sm:justify-end sm:border-0 sm:bg-transparent sm:p-0">
          <Link
            to="/community"
            className="flex min-h-[var(--touch-target-min)] flex-1 items-center justify-center rounded-xl border border-border bg-surface px-5 text-body font-semibold text-text-secondary transition-colors hover:bg-background-alt sm:flex-none"
          >
            {t("newPost.cancel")}
          </Link>
          <Button type="submit" loading={busy} className="flex-[2] px-6 sm:flex-none">
            {busy ? t("newPost.submitting") : t("newPost.submit")}
          </Button>
        </div>
      </form>

      <aside className="w-full lg:sticky lg:top-[88px] lg:w-[296px] lg:shrink-0">
        <RulesCard />
      </aside>
    </div>
  );
}

interface ImagePickerProps {
  file: File | null;
  onChange: (file: File | null) => void;
  progress: number | null;
  disabled: boolean;
}

function ImagePicker({ file, onChange, progress, disabled }: ImagePickerProps) {
  const { t } = useTranslation("community");
  const inputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const previewUrl = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);

  useEffect(
    () => () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    },
    [previewUrl],
  );

  const accept = (candidate: File | undefined) => {
    if (!candidate) return;
    if (!COMMUNITY_IMAGE.types.includes(candidate.type)) {
      setError(t("newPost.image.errors.type"));
      return;
    }
    if (candidate.size > COMMUNITY_IMAGE.maxBytes) {
      setError(t("newPost.image.errors.size", { max: MAX_IMAGE_MB }));
      return;
    }
    setError(null);
    onChange(candidate);
  };

  const input = (
    <input
      ref={inputRef}
      type="file"
      accept={COMMUNITY_IMAGE.types.join(",")}
      className="sr-only"
      tabIndex={-1}
      aria-hidden="true"
      onChange={(event) => {
        accept(event.target.files?.[0]);
        event.target.value = "";
      }}
    />
  );

  if (file && previewUrl) {
    return (
      <div className="content-enter relative overflow-hidden rounded-xl border border-border/60 bg-background-alt">
        {input}
        <img src={previewUrl} alt={t("newPost.image.previewAlt")} className="aspect-[16/10] w-full object-cover" />
        {progress !== null ? (
          <div className="absolute inset-x-0 bottom-0 bg-surface/90 px-3 py-2">
            <p className="text-[11px] font-semibold text-text-secondary">
              {t("newPost.image.uploading", { percent: Math.round(progress * 100) })}
            </p>
            <div className="mt-1 h-1 overflow-hidden rounded-full bg-border/60">
              <div
                className="h-full origin-left rounded-full bg-primary transition-transform duration-200"
                style={{ transform: `scaleX(${String(progress)})` }}
              />
            </div>
          </div>
        ) : (
          <div className="absolute right-2 top-2 flex gap-1.5">
            <button
              type="button"
              disabled={disabled}
              onClick={() => inputRef.current?.click()}
              className="rounded-full bg-surface/90 px-3 py-1.5 text-[12px] font-semibold text-text-primary shadow-xs"
            >
              {t("newPost.image.change")}
            </button>
            <button
              type="button"
              disabled={disabled}
              aria-label={t("newPost.image.remove")}
              onClick={() => {
                onChange(null);
              }}
              className="flex size-8 items-center justify-center rounded-full bg-surface/90 text-text-primary shadow-xs"
            >
              <X size={16} aria-hidden="true" />
            </button>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5">
      {input}
      <button
        type="button"
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => {
          setDragging(false);
        }}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          accept(event.dataTransfer.files[0]);
        }}
        className={cn(
          "flex w-full items-center gap-3 rounded-xl border border-dashed px-3 py-3 text-left transition-colors duration-200",
          dragging ? "border-primary bg-chip-bg" : "border-border-strong hover:border-primary hover:bg-background-alt",
          fieldFocus,
        )}
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary">
          <ImagePlus size={18} aria-hidden="true" />
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="text-[13px] font-semibold text-text-primary">
            {t("newPost.image.add")}{" "}
            <span className="font-normal text-text-tertiary">({t("newPost.image.optional")})</span>
          </span>
          <span className="text-[11px] text-text-tertiary">{t("newPost.image.dropHint", { max: MAX_IMAGE_MB })}</span>
        </span>
      </button>
      {error ? <p className="text-[12px] font-medium text-danger-text">{error}</p> : null}
    </div>
  );
}

interface HashtagFieldProps {
  category: CommunityCategory;
  tags: string[];
  onChange: (tags: string[]) => void;
  error?: string;
}

function HashtagField({ category, tags, onChange, error }: HashtagFieldProps) {
  const { t } = useTranslation("community");
  const inputId = useId();
  const [draft, setDraft] = useState("");
  const popular = useCommunityTagSuggestions();
  const full = tags.length >= COMMUNITY_LIMITS.tags;

  const suggestions = useMemo(() => {
    const fromCategory = t(`newPost.suggestedTags.${category}`).split(",").map(normalizeTag);
    const query = normalizeTag(draft);
    return [...new Set([...popular, ...fromCategory])]
      .filter((tag) => tag && !tags.includes(tag) && (!query || tag.includes(query)))
      .slice(0, 6);
  }, [category, draft, popular, t, tags]);

  const add = (raw: string) => {
    const tag = normalizeTag(raw).slice(0, COMMUNITY_LIMITS.tag);
    setDraft("");
    if (!tag || tags.includes(tag) || full) return;
    onChange([...tags, tag]);
  };

  const remove = (tag: string) => {
    onChange(tags.filter((item) => item !== tag));
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      add(draft);
    } else if (event.key === "Backspace" && !draft && tags.length > 0) {
      remove(tags[tags.length - 1]);
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-baseline justify-between gap-2">
        <label htmlFor={inputId} className="flex items-center gap-1.5 text-caption font-semibold text-text-primary">
          <Hash size={14} className="text-primary" aria-hidden="true" />
          {t("newPost.hashtag.label")}
        </label>
        <span className="text-[11px] tabular-nums text-text-tertiary">
          {t("newPost.hashtag.count", { count: tags.length, max: COMMUNITY_LIMITS.tags })}
        </span>
      </div>

      <div
        className={cn(
          "flex min-h-11 flex-wrap items-center gap-1.5 rounded-xl border bg-surface px-2 py-1.5 transition-colors focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/15",
          error ? "border-danger" : "border-border-strong",
        )}
      >
        {tags.map((tag) => (
          <span
            key={tag}
            className="content-enter inline-flex max-w-full items-center gap-1 rounded-lg bg-chip-bg py-1 pl-2 pr-1 text-[12px] font-semibold text-primary-dark"
          >
            <span className="truncate">#{tag}</span>
            <button
              type="button"
              aria-label={t("newPost.hashtag.remove", { tag })}
              onClick={() => {
                remove(tag);
              }}
              className="flex size-5 items-center justify-center rounded-md text-primary-dark/70 hover:bg-surface hover:text-primary-dark"
            >
              <X size={12} aria-hidden="true" />
            </button>
          </span>
        ))}
        {full ? null : (
          <input
            id={inputId}
            value={draft}
            onChange={(event) => {
              const value = event.target.value;
              if (value.endsWith(",")) add(value.slice(0, -1));
              else setDraft(value);
            }}
            onKeyDown={onKeyDown}
            onBlur={() => {
              if (draft.trim()) add(draft);
            }}
            maxLength={COMMUNITY_LIMITS.tag + 1}
            placeholder={tags.length === 0 ? t("newPost.hashtag.placeholder") : ""}
            enterKeyHint="done"
            className="min-w-[8rem] flex-1 bg-transparent px-1 text-[14px] text-text-primary outline-none placeholder:text-text-tertiary"
          />
        )}
      </div>

      {suggestions.length > 0 && !full ? (
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-[11px] font-medium text-text-tertiary">{t("newPost.hashtag.suggested")}</span>
          {suggestions.map((tag) => (
            <button
              key={tag}
              type="button"
              aria-label={t("newPost.hashtag.add", { tag })}
              onMouseDown={(event) => {
                event.preventDefault();
              }}
              onClick={() => {
                add(tag);
              }}
              className="inline-flex items-center gap-0.5 rounded-lg border border-border/80 px-2 py-1 text-[12px] font-medium text-text-secondary transition-colors hover:border-primary hover:text-primary-dark"
            >
              <Plus size={12} aria-hidden="true" />#{tag}
            </button>
          ))}
        </div>
      ) : null}

      <p className={cn("text-[11px] leading-snug", error ? "font-medium text-danger-text" : "text-text-tertiary")}>
        {error ?? t("newPost.hashtag.hint", { max: COMMUNITY_LIMITS.tags })}
      </p>
    </div>
  );
}

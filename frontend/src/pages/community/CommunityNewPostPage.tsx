import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { Camera, Check, FileText, Info, ScanLine, ShieldCheck } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { createCommunityPost } from "@/features/community";
import { DESIGN_MOCK_NEW_POST } from "./mockData";

/**
 * `/community/new` — soạn thảo luận mới.
 *
 * Figma KHÔNG có frame riêng cho màn này: bản web đặt ô soạn bài inline trên bảng tin
 * (`Web - 15`, khối "Đăng thảo luận" — placeholder, dải "NHÃN BỆNH LÝ", ba nút đính kèm) và
 * bản mobile chỉ có FAB "+ Đăng bài viết". Trang này dựng lại đúng các thành phần đó ở dạng
 * trang đầy đủ, cộng danh sách chuyên mục lấy từ 4 tab chuyên đề của bảng tin web và khối
 * quy chuẩn ISFM — KHÔNG bịa thêm thành phần nào ngoài thiết kế.
 *
 * Route này nằm trong `TaskLayout` (header back + hộp nội dung 944px đã có padding ngang ở
 * MỌI breakpoint) nên trang tuyệt đối KHÔNG tự thêm padding ngang.
 *
 * Form gửi bài thật qua `POST /api/v1/community/posts`; phần file đính kèm vẫn để dành cho
 * media upload riêng, không giả lập URL ảnh trong request.
 */
export function CommunityNewPostPage() {
  const { t } = useTranslation("community");
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [category, setCategory] = useState(DESIGN_MOCK_NEW_POST.categories[0].id);
  const [tags, setTags] = useState<string[]>([]);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(false);

  const toggleTag = (tag: string) => {
    setTags((prev) => (prev.includes(tag) ? prev.filter((x) => x !== tag) : [...prev, tag]));
  };

  const submit = async () => {
    if (!title.trim() || !body.trim() || isSubmitting) return;
    setIsSubmitting(true);
    setSubmitError(false);
    try {
      const apiCategory = category === "question" ? "QA" : category === "experience" ? "EXPERIENCE" : "TIP";
      const post = await createCommunityPost({ category: apiCategory, title: title.trim(), body: body.trim(), tags });
      await queryClient.invalidateQueries({ queryKey: ["community", "posts"] });
      await navigate(`/community/posts/${post.id}`);
    } catch {
      setSubmitError(true);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col gap-5 pb-6 lg:flex-row lg:items-start lg:gap-6">
      <div className="flex min-w-0 flex-1 flex-col gap-4">
        <header>
          <h1 className="text-[22px] font-bold leading-tight text-text-primary lg:text-[26px]">{t("newPost.title")}</h1>
          <p className="pt-1.5 text-[13px] leading-relaxed text-text-secondary">{t("newPost.subtitle")}</p>
        </header>

        <p className="flex gap-2.5 rounded-xl bg-secondary/20 p-3.5 text-[12px] leading-relaxed text-secondary-text-on">
          <Info size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
          {t("newPost.phase2Notice")}
        </p>

        <section className="flex flex-col gap-4 rounded-2xl bg-surface p-5 shadow-brand-md">
          <fieldset className="min-w-0">
            <legend className="text-[12px] font-bold text-text-primary">{t("newPost.categoryLabel")}</legend>
            <div className="flex flex-wrap gap-2 pt-2.5">
              {DESIGN_MOCK_NEW_POST.categories.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => {
                    setCategory(item.id);
                  }}
                  aria-pressed={category === item.id}
                  className={cn(
                    "rounded-xl px-3.5 py-2 text-[12px] font-semibold transition-colors",
                    category === item.id
                      ? "bg-primary-dark text-white"
                      : "bg-background-alt text-text-secondary hover:bg-chip-bg",
                  )}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </fieldset>

          <label className="flex flex-col gap-2">
            <span className="text-[12px] font-bold text-text-primary">{t("newPost.titleLabel")}</span>
            <input
              type="text"
              value={title}
              onChange={(event) => {
                setTitle(event.target.value);
              }}
              placeholder={t("newPost.titlePlaceholder")}
              className="h-11 w-full rounded-xl bg-background-alt px-4 text-[13px] text-text-primary outline-none placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
            />
          </label>

          <label className="flex flex-col gap-2">
            <span className="text-[12px] font-bold text-text-primary">{t("newPost.bodyLabel")}</span>
            <textarea
              rows={7}
              value={body}
              onChange={(event) => {
                setBody(event.target.value);
              }}
              placeholder={t("newPost.bodyPlaceholder")}
              className="w-full resize-none rounded-xl bg-background-alt px-4 py-3 text-[13px] leading-relaxed text-text-primary outline-none placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
            />
          </label>

          <fieldset className="min-w-0">
            <legend className="text-[10px] font-bold tracking-[0.5px] text-text-tertiary">
              {t("newPost.tagsLabel")}
            </legend>
            <div className="flex flex-wrap gap-2 pt-2.5">
              {DESIGN_MOCK_NEW_POST.tags.map((tag) => (
                <button
                  key={tag}
                  type="button"
                  onClick={() => {
                    toggleTag(tag);
                  }}
                  aria-pressed={tags.includes(tag)}
                  className={cn(
                    "rounded-full px-3 py-1.5 text-[11px] font-semibold transition-colors",
                    tags.includes(tag) ? "bg-primary text-white" : "bg-chip-bg text-primary-dark hover:bg-info",
                  )}
                >
                  {tag}
                </button>
              ))}
            </div>
          </fieldset>

          <div className="min-w-0">
            <p className="text-[12px] font-bold text-text-primary">{t("newPost.attachLabel")}</p>
            <div className="flex flex-wrap gap-2 pt-2.5">
              {DESIGN_MOCK_NEW_POST.attachments.map((item) => {
                const Icon = item.id === "photo" ? Camera : item.id === "scan" ? ScanLine : FileText;
                return (
                  <button
                    key={item.id}
                    type="button"
                    className="flex items-center gap-2 rounded-xl bg-background-alt px-3.5 py-2.5 text-[12px] font-semibold text-text-secondary hover:bg-chip-bg"
                  >
                    <Icon size={14} aria-hidden="true" />
                    {item.label}
                  </button>
                );
              })}
            </div>
          </div>
        </section>

        <div className="flex flex-col gap-2.5 sm:flex-row sm:justify-end">
          {submitError ? (
            <p role="alert" className="self-center text-[12px] font-medium text-danger-text">{t("api.error")}</p>
          ) : null}
          <Link
            to="/community"
            className="flex items-center justify-center rounded-xl bg-background-alt px-5 py-3 text-[13px] font-semibold text-text-secondary hover:bg-chip-bg"
          >
            {t("newPost.cancel")}
          </Link>
          <button
            type="button"
            onClick={() => {
              void submit();
            }}
            disabled={!title.trim() || !body.trim() || isSubmitting}
            className="flex items-center justify-center rounded-xl bg-primary-dark px-6 py-3 text-[13px] font-bold text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isSubmitting ? t("newPost.submitting") : t("newPost.submit")}
          </button>
        </div>
      </div>

      <aside className="w-full lg:w-[296px] lg:shrink-0">
        <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
          <h2 className="flex items-center gap-2 text-[14px] font-bold text-text-primary">
            <ShieldCheck size={15} className="text-primary" aria-hidden="true" />
            {t("newPost.guidelinesTitle")}
          </h2>
          <ul className="flex flex-col gap-2 pt-3">
            {DESIGN_MOCK_NEW_POST.guidelines.map((item) => (
              <li key={item} className="flex gap-2 text-[11px] leading-relaxed text-text-secondary">
                <Check size={12} className="mt-0.5 shrink-0 text-success-text" aria-hidden="true" />
                {item}
              </li>
            ))}
          </ul>
        </section>
      </aside>
    </div>
  );
}

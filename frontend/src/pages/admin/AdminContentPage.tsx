import { useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, Loader2, Plus, X } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import { formatDate } from "@/shared/lib/format/formatDate";
import {
  AdminPageHeader,
  AdminSection,
  AdminSelect,
  AdminTableScroll,
  ApiErrorNote,
  CARE_TIP_CATEGORIES,
  CARE_TIP_KINDS,
  CARE_TIP_STATUSES,
  CLAIM_TYPES,
  Pager,
  ReasonField,
  SavedNote,
  adminTableClass,
  adminTdClass,
  adminThClass,
  isReasonValid,
  useAdminCareTips,
  useArchiveCareTip,
  useCreateCareTip,
  usePublishCareTip,
  useSubmitCareTipReview,
  useUpdateCareTip,
  type CareTipCategory,
  type CareTipDetail,
  type CareTipKind,
  type CareTipStatus,
  type ClaimType,
} from "@/features/admin";

/**
 * `/admin/content` — quản trị nội dung (`care_tip`).
 *
 * NỐI API THẬT, toàn bộ `content/api/AdminContentController`:
 * L40 `GET /admin/care-tips` · L41 `POST /admin/care-tips` ·
 * L42 `PATCH /admin/care-tips/{tipId}` · L43 `POST …/submit-review` ·
 * L44 `POST …/publish` · L45 `POST …/archive`.
 *
 * `reason` chỉ xuất hiện ở L44 vì đó là endpoint DUY NHẤT trong nhóm được p8 §8.4.12 (c)
 * đánh cờ `Rsn`, và cũng là nơi DTO backend (`PublishCareTipRequest`) thật sự có trường
 * `reason` với `@Size(min = 10)` — khớp p17 §17.3.8 AD11.
 *
 * `RequireRole` ở router đã chặn vai trò; trang không lặp lại logic đó.
 */

const STATUS_CLASS: Record<CareTipStatus, string> = {
  DRAFT: "bg-background-alt text-text-secondary",
  IN_REVIEW: "bg-warning-bg text-warning-text",
  PUBLISHED: "bg-success-bg text-success-text",
  ARCHIVED: "bg-chip-bg text-text-tertiary",
};

function StatusChip({ status, label }: { status: CareTipStatus; label: string }) {
  return (
    <span className={`inline-block rounded-md px-2 py-0.5 text-small font-semibold ${STATUS_CLASS[status]}`}>
      {label}
    </span>
  );
}

// ------------------------------------------------------------------ tạo nháp

function CreateCareTipForm({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation("admin");
  const create = useCreateCareTip();
  const [slug, setSlug] = useState("");
  const [locale, setLocale] = useState("vi");
  const [kind, setKind] = useState<CareTipKind>("TIP");
  const [category, setCategory] = useState<CareTipCategory>("GENERAL");
  const [title, setTitle] = useState("");
  const [claimType, setClaimType] = useState<ClaimType>("NONE");

  const canSubmit = slug.trim().length > 0 && title.trim().length > 0;

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        create.mutate(
          { slug: slug.trim(), locale, kind, category, title: title.trim(), claimType },
          {
            onSuccess: () => {
              setSlug("");
              setTitle("");
              onDone();
            },
          },
        );
      }}
    >
      <div className="grid gap-4 md:grid-cols-2">
        <Input
          label={t("content.fieldSlug")}
          helperText={t("content.fieldSlugHelp")}
          value={slug}
          onChange={(event) => {
            setSlug(event.target.value);
          }}
        />
        <Input
          label={t("content.fieldTitle")}
          value={title}
          onChange={(event) => {
            setTitle(event.target.value);
          }}
        />
        <AdminSelect
          label={t("content.fieldLocale")}
          value={locale}
          onChange={(event) => {
            setLocale(event.target.value);
          }}
        >
          <option value="vi">{t("content.localeVi")}</option>
          <option value="en">{t("content.localeEn")}</option>
        </AdminSelect>
        <AdminSelect
          label={t("content.fieldKind")}
          value={kind}
          onChange={(event) => {
            setKind(event.target.value as CareTipKind);
          }}
        >
          {CARE_TIP_KINDS.map((value) => (
            <option key={value} value={value}>
              {t(`content.kind.${value}`)}
            </option>
          ))}
        </AdminSelect>
        <AdminSelect
          label={t("content.fieldCategory")}
          value={category}
          onChange={(event) => {
            setCategory(event.target.value as CareTipCategory);
          }}
        >
          {CARE_TIP_CATEGORIES.map((value) => (
            <option key={value} value={value}>
              {t(`content.category.${value}`)}
            </option>
          ))}
        </AdminSelect>
        <AdminSelect
          label={t("content.fieldClaimType")}
          value={claimType}
          onChange={(event) => {
            setClaimType(event.target.value as ClaimType);
          }}
        >
          {CLAIM_TYPES.map((value) => (
            <option key={value} value={value}>
              {t(`content.claim.${value}`)}
            </option>
          ))}
        </AdminSelect>
      </div>

      <ApiErrorNote error={create.error} />

      <div className="flex gap-2">
        <Button type="submit" size="md" disabled={!canSubmit} loading={create.isPending}>
          {t("content.createSubmit")}
        </Button>
        <Button type="button" variant="tertiary" size="md" onClick={onDone}>
          {t("actions.cancel")}
        </Button>
      </div>
    </form>
  );
}

// ------------------------------------------------------------------ sửa nháp

function CareTipEditor({ tip, onClose }: { tip: CareTipDetail; onClose: () => void }) {
  const { t } = useTranslation("admin");
  const update = useUpdateCareTip();
  const submitReview = useSubmitCareTipReview();
  const publish = usePublishCareTip();
  const archive = useArchiveCareTip();

  const [title, setTitle] = useState(tip.title);
  const [summary, setSummary] = useState(tip.summary ?? "");
  const [bodyMd, setBodyMd] = useState(tip.bodyMd ?? "");
  const [claimType, setClaimType] = useState<ClaimType>(tip.claimType);
  const [sourceReference, setSourceReference] = useState(tip.sourceReference ?? "");
  const [reason, setReason] = useState("");
  const [reasonTouched, setReasonTouched] = useState(false);

  const editable = tip.status === "DRAFT" || tip.status === "IN_REVIEW";
  /** AD20 — `claimType` khác NONE mà thiếu nguồn thì server trả 422 lúc publish. */
  const sourceMissing = claimType !== "NONE" && sourceReference.trim().length === 0;

  return (
    <AdminSection
      title={t("content.editorTitle", { title: tip.title })}
      description={t("content.editorSubtitle", { slug: tip.slug, locale: tip.locale })}
      actions={
        <Button type="button" variant="tertiary" size="sm" leftIcon={<X size={15} />} onClick={onClose}>
          {t("actions.close")}
        </Button>
      }
    >
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault();
          update.mutate({
            tipId: tip.id,
            payload: {
              title: title.trim(),
              summary,
              bodyMd,
              claimType,
              sourceReference,
            },
          });
        }}
      >
        <div className="grid gap-4 md:grid-cols-2">
          <Input
            label={t("content.fieldTitle")}
            value={title}
            disabled={!editable}
            onChange={(event) => {
              setTitle(event.target.value);
            }}
          />
          <AdminSelect
            label={t("content.fieldClaimType")}
            value={claimType}
            disabled={!editable}
            onChange={(event) => {
              setClaimType(event.target.value as ClaimType);
            }}
          >
            {CLAIM_TYPES.map((value) => (
              <option key={value} value={value}>
                {t(`content.claim.${value}`)}
              </option>
            ))}
          </AdminSelect>
        </div>

        <Input
          label={t("content.fieldSummary")}
          value={summary}
          disabled={!editable}
          onChange={(event) => {
            setSummary(event.target.value);
          }}
        />

        <Input
          label={t("content.fieldSource")}
          helperText={t("content.fieldSourceHelp")}
          error={sourceMissing ? t("content.sourceRequired") : undefined}
          value={sourceReference}
          disabled={!editable}
          onChange={(event) => {
            setSourceReference(event.target.value);
          }}
        />

        <div className="flex flex-col gap-1.5">
          <label htmlFor={`body-${tip.id}`} className="text-caption font-semibold text-text-secondary">
            {t("content.fieldBody")}
          </label>
          <textarea
            id={`body-${tip.id}`}
            rows={8}
            value={bodyMd}
            disabled={!editable}
            onChange={(event) => {
              setBodyMd(event.target.value);
            }}
            className="w-full resize-y rounded-xl bg-surface px-4 py-3 font-mono text-caption text-text-primary shadow-xs focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)] disabled:cursor-not-allowed disabled:opacity-50"
          />
        </div>

        <ApiErrorNote error={update.error} />
        <SavedNote visible={update.isSuccess} />

        <div className="flex flex-wrap gap-2">
          <Button type="submit" size="md" disabled={!editable} loading={update.isPending}>
            {t("content.saveDraft")}
          </Button>
          {tip.status === "DRAFT" ? (
            <Button
              type="button"
              variant="tertiary"
              size="md"
              loading={submitReview.isPending}
              onClick={() => {
                submitReview.mutate(tip.id);
              }}
            >
              {t("content.submitReview")}
            </Button>
          ) : null}
          {tip.status === "PUBLISHED" ? (
            <Button
              type="button"
              variant="tertiary"
              size="md"
              loading={archive.isPending}
              onClick={() => {
                archive.mutate(tip.id);
              }}
            >
              {t("content.archive")}
            </Button>
          ) : null}
        </div>
        <ApiErrorNote error={submitReview.error ?? archive.error} />
      </form>

      {tip.status === "IN_REVIEW" ? (
        <div className="flex flex-col gap-3 rounded-xl bg-background-alt/70 p-4">
          <p className="text-body font-semibold text-text-primary">{t("content.publishTitle")}</p>
          <p className="text-caption text-text-secondary">{t("content.publishBody")}</p>
          <ReasonField value={reason} onChange={setReason} showError={reasonTouched} />
          <ApiErrorNote error={publish.error} />
          <Button
            type="button"
            size="md"
            className="self-start"
            loading={publish.isPending}
            onClick={() => {
              setReasonTouched(true);
              if (!isReasonValid(reason)) {
                return;
              }
              publish.mutate(
                { tipId: tip.id, reason: reason.trim() },
                {
                  onSuccess: () => {
                    setReason("");
                    setReasonTouched(false);
                  },
                },
              );
            }}
          >
            {t("content.publishCta")}
          </Button>
        </div>
      ) : null}
    </AdminSection>
  );
}

// -------------------------------------------------------------------- trang

export function AdminContentPage() {
  const { t } = useTranslation("admin");
  const [locale, setLocale] = useState("");
  const [status, setStatus] = useState<CareTipStatus | "">("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const list = useAdminCareTips(locale, status, page);
  const items = list.data?.items ?? [];
  const selected = items.find((item) => item.id === selectedId) ?? null;

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.content.title")}
        description={t("pages.content.description")}
        specRef="/api/v1/admin/care-tips — L40–L45 (p8 §8.4.12 c)"
        actions={
          <Button
            type="button"
            size="md"
            leftIcon={<Plus size={16} />}
            onClick={() => {
              setCreating((value) => !value);
            }}
          >
            {t("content.createCta")}
          </Button>
        }
      />

      {creating ? (
        <AdminSection title={t("content.createTitle")} description={t("content.createDescription")}>
          <CreateCareTipForm
            onDone={() => {
              setCreating(false);
            }}
          />
        </AdminSection>
      ) : null}

      <AdminSection title={t("content.listTitle")} description={t("content.listDescription")}>
        <div className="flex flex-wrap gap-4">
          <AdminSelect
            label={t("content.filterLocale")}
            value={locale}
            onChange={(event) => {
              setLocale(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("content.filterAll")}</option>
            <option value="vi">{t("content.localeVi")}</option>
            <option value="en">{t("content.localeEn")}</option>
          </AdminSelect>
          <AdminSelect
            label={t("content.filterStatus")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as CareTipStatus | "");
              setPage(0);
            }}
          >
            <option value="">{t("content.filterAll")}</option>
            {CARE_TIP_STATUSES.map((value) => (
              <option key={value} value={value}>
                {t(`content.status.${value}`)}
              </option>
            ))}
          </AdminSelect>
        </div>

        {list.isPending ? (
          <p className="flex items-center gap-2 text-caption text-text-secondary">
            <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            {t("feedback.loading")}
          </p>
        ) : list.isError ? (
          <ApiErrorNote error={list.error} />
        ) : items.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : (
          <>
            <AdminTableScroll>
              <table className={adminTableClass}>
                <thead>
                  <tr>
                    <th className={adminThClass}>{t("content.colTitle")}</th>
                    <th className={adminThClass}>{t("content.colLocale")}</th>
                    <th className={adminThClass}>{t("content.colKind")}</th>
                    <th className={adminThClass}>{t("content.colStatus")}</th>
                    <th className={adminThClass}>{t("content.colClaim")}</th>
                    <th className={adminThClass}>{t("content.colUpdatedAt")}</th>
                    <th className={adminThClass}>{t("content.colActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((tip) => (
                    <tr key={tip.id}>
                      <td className={adminTdClass}>
                        <span className="font-semibold">{tip.title}</span>
                        <span className="block font-mono text-small text-text-tertiary">{tip.slug}</span>
                      </td>
                      <td className={adminTdClass}>
                        <span className="font-mono text-small uppercase">{tip.locale}</span>
                      </td>
                      <td className={adminTdClass}>{t(`content.kind.${tip.kind}`)}</td>
                      <td className={adminTdClass}>
                        <StatusChip status={tip.status} label={t(`content.status.${tip.status}`)} />
                      </td>
                      <td className={adminTdClass}>
                        <span>{t(`content.claim.${tip.claimType}`)}</span>
                        {tip.claimType !== "NONE" &&
                        (tip.sourceReference === null || tip.sourceReference.length === 0) ? (
                          <span className="flex items-center gap-1 pt-0.5 text-small text-warning-text">
                            <AlertTriangle size={12} aria-hidden="true" />
                            {t("content.sourceMissingShort")}
                          </span>
                        ) : null}
                      </td>
                      <td className={adminTdClass}>
                        <span className="whitespace-nowrap">{formatDate(tip.updatedAt, "dd/MM/yyyy HH:mm")}</span>
                      </td>
                      <td className={adminTdClass}>
                        <button
                          type="button"
                          className="font-semibold text-primary-dark hover:underline"
                          onClick={() => {
                            setSelectedId(tip.id);
                          }}
                        >
                          {t("actions.open")}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <Pager
              page={list.data.number}
              totalPages={list.data.totalPages}
              totalElements={list.data.totalElements}
              onPageChange={setPage}
            />
          </>
        )}
      </AdminSection>

      {selected !== null ? (
        <CareTipEditor
          key={selected.id}
          tip={selected}
          onClose={() => {
            setSelectedId(null);
          }}
        />
      ) : null}
    </div>
  );
}

import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, FileText, Plus } from "lucide-react";
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Input,
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
  SkeletonLoader,
  Tabs,
  TabsList,
  TabsTrigger,
} from "@/shared/ui";
import { formatDate } from "@/shared/lib/format/formatDate";
import { hasRole, useSessionStore } from "@/entities/user";
import { ROLES } from "@/shared/auth/roles";
import {
  ApiErrorNote,
  CARE_TIP_CATEGORIES,
  CARE_TIP_KINDS,
  CARE_TIP_STATUSES,
  CLAIM_TYPES,
  Pager,
  ReasonField,
  SavedNote,
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
import { useListReveal } from "@/shared/lib/motion/usePageReveal";

type StatusFilter = CareTipStatus | "";

const STATUS_TONE: Record<CareTipStatus, "neutral" | "success" | "warning" | "info"> = {
  DRAFT: "neutral",
  IN_REVIEW: "warning",
  PUBLISHED: "success",
  ARCHIVED: "info",
};

const FILTERS: readonly StatusFilter[] = ["", ...CARE_TIP_STATUSES];

const controlClass =
  "w-full rounded-xl border border-border bg-surface px-3 text-body text-text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)] disabled:cursor-not-allowed disabled:opacity-50";

const thClass =
  "sticky top-0 z-10 bg-background-alt px-3 py-2 text-left text-caption font-semibold whitespace-nowrap text-text-secondary";
const tdClass = "h-10 px-3 py-1.5 text-caption text-text-primary";

function useCareTipAccess(): { canWrite: boolean; canPublish: boolean } {
  const user = useSessionStore((state) => state.user);
  return { canWrite: hasRole(user, ROLES.careTipsWrite), canPublish: hasRole(user, ROLES.careTipsPublish) };
}

interface SelectFieldProps {
  id: string;
  label: string;
  value: string;
  disabled?: boolean;
  onChange: (value: string) => void;
  children: React.ReactNode;
}

function SelectField({ id, label, value, disabled = false, onChange, children }: SelectFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-caption font-semibold text-text-secondary">
        {label}
      </label>
      <select
        id={id}
        className={`${controlClass} h-12`}
        value={value}
        disabled={disabled}
        onChange={(event) => {
          onChange(event.target.value);
        }}
      >
        {children}
      </select>
    </div>
  );
}

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
      className="mt-4 flex flex-col gap-4"
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
        <SelectField
          id="seller-content-create-locale"
          label={t("content.fieldLocale")}
          value={locale}
          onChange={setLocale}
        >
          <option value="vi">{t("content.localeVi")}</option>
          <option value="en">{t("content.localeEn")}</option>
        </SelectField>
        <SelectField
          id="seller-content-create-kind"
          label={t("content.fieldKind")}
          value={kind}
          onChange={(value) => {
            setKind(value as CareTipKind);
          }}
        >
          {CARE_TIP_KINDS.map((value) => (
            <option key={value} value={value}>
              {t(`content.kind.${value}`)}
            </option>
          ))}
        </SelectField>
        <SelectField
          id="seller-content-create-category"
          label={t("content.fieldCategory")}
          value={category}
          onChange={(value) => {
            setCategory(value as CareTipCategory);
          }}
        >
          {CARE_TIP_CATEGORIES.map((value) => (
            <option key={value} value={value}>
              {t(`content.category.${value}`)}
            </option>
          ))}
        </SelectField>
        <SelectField
          id="seller-content-create-claim"
          label={t("content.fieldClaimType")}
          value={claimType}
          onChange={(value) => {
            setClaimType(value as ClaimType);
          }}
        >
          {CLAIM_TYPES.map((value) => (
            <option key={value} value={value}>
              {t(`content.claim.${value}`)}
            </option>
          ))}
        </SelectField>
      </div>

      <ApiErrorNote error={create.error} />

      <div className="flex justify-end gap-2">
        <Button type="button" variant="tertiary" onClick={onDone}>
          {t("actions.cancel")}
        </Button>
        <Button type="submit" variant="primary" disabled={!canSubmit} loading={create.isPending}>
          {t("content.createSubmit")}
        </Button>
      </div>
    </form>
  );
}

function CareTipEditor({ tip, onClose }: { tip: CareTipDetail; onClose: () => void }) {
  const { t } = useTranslation("admin");
  const { t: tSeller } = useTranslation("seller");
  const update = useUpdateCareTip();
  const submitReview = useSubmitCareTipReview();
  const publish = usePublishCareTip();
  const archive = useArchiveCareTip();
  const { canWrite, canPublish } = useCareTipAccess();

  const [title, setTitle] = useState(tip.title);
  const [summary, setSummary] = useState(tip.summary ?? "");
  const [bodyMd, setBodyMd] = useState(tip.bodyMd ?? "");
  const [claimType, setClaimType] = useState<ClaimType>(tip.claimType);
  const [sourceReference, setSourceReference] = useState(tip.sourceReference ?? "");
  const [reason, setReason] = useState("");
  const [reasonTouched, setReasonTouched] = useState(false);
  const [confirmingArchive, setConfirmingArchive] = useState(false);

  const sessionUserId = useSessionStore((state) => state.user?.id);

  const editable = (tip.status === "DRAFT" || tip.status === "IN_REVIEW") && canWrite;
  const sourceMissing = claimType !== "NONE" && sourceReference.trim().length === 0;
  // Backend chặn tự duyệt bài của chính mình (CONTENT_SELF_APPROVAL_FORBIDDEN).
  const isOwnTip = tip.authorId != null && tip.authorId === sessionUserId;
  const dirty =
    title.trim() !== tip.title ||
    summary !== (tip.summary ?? "") ||
    bodyMd !== (tip.bodyMd ?? "") ||
    claimType !== tip.claimType ||
    sourceReference !== (tip.sourceReference ?? "");
  const savePayload = () => ({
    tipId: tip.id,
    payload: { title: title.trim(), summary, bodyMd, claimType, sourceReference },
  });

  // Gửi duyệt phải gửi đúng nội dung đang sửa: lưu trước nếu còn thay đổi chưa lưu.
  const handleSubmitReview = async () => {
    if (dirty) {
      try {
        await update.mutateAsync(savePayload());
      } catch {
        return;
      }
    }
    submitReview.mutate(tip.id);
  };

  return (
    <>
      <SheetTitle>{t("content.editorTitle", { title: tip.title })}</SheetTitle>
      <SheetDescription>{t("content.editorSubtitle", { slug: tip.slug, locale: tip.locale })}</SheetDescription>
      <div className="mt-2">
        <Badge tone={STATUS_TONE[tip.status]}>{t(`content.status.${tip.status}`)}</Badge>
      </div>

      <form
        className="mt-4 flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault();
          update.mutate(savePayload());
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
          <SelectField
            id={`seller-content-claim-${tip.id}`}
            label={t("content.fieldClaimType")}
            value={claimType}
            disabled={!editable}
            onChange={(value) => {
              setClaimType(value as ClaimType);
            }}
          >
            {CLAIM_TYPES.map((value) => (
              <option key={value} value={value}>
                {t(`content.claim.${value}`)}
              </option>
            ))}
          </SelectField>
          <div className="md:col-span-2">
            <Input
              label={t("content.fieldSummary")}
              value={summary}
              disabled={!editable}
              onChange={(event) => {
                setSummary(event.target.value);
              }}
            />
          </div>
          <div className="md:col-span-2">
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
          </div>
          <div className="flex flex-col gap-1.5 md:col-span-2">
            <label htmlFor={`seller-content-body-${tip.id}`} className="text-caption font-semibold text-text-secondary">
              {t("content.fieldBody")}
            </label>
            <textarea
              id={`seller-content-body-${tip.id}`}
              className={`${controlClass} min-h-40 py-2 font-mono text-caption`}
              value={bodyMd}
              disabled={!editable}
              onChange={(event) => {
                setBodyMd(event.target.value);
              }}
            />
          </div>
        </div>

        <ApiErrorNote error={update.error} />
        <SavedNote visible={update.isSuccess} />
        <ApiErrorNote error={submitReview.error ?? archive.error} />

        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="tertiary" onClick={onClose}>
            {t("actions.close")}
          </Button>
          {canWrite && tip.status === "DRAFT" ? (
            <Button
              type="button"
              variant="secondary"
              loading={submitReview.isPending}
              disabled={update.isPending}
              onClick={() => {
                void handleSubmitReview();
              }}
            >
              {t("content.submitReview")}
            </Button>
          ) : null}
          {canPublish && tip.status === "PUBLISHED" && !confirmingArchive ? (
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setConfirmingArchive(true);
              }}
            >
              {t("content.archive")}
            </Button>
          ) : null}
          <Button type="submit" variant="primary" disabled={!editable} loading={update.isPending}>
            {t("content.saveDraft")}
          </Button>
        </div>
      </form>

      {canPublish && tip.status === "PUBLISHED" && confirmingArchive ? (
        <div
          role="alertdialog"
          aria-labelledby={`seller-content-archive-${tip.id}`}
          className="mt-4 flex flex-col gap-3 rounded-xl border border-border bg-warning-bg px-4 py-3"
        >
          <p id={`seller-content-archive-${tip.id}`} className="text-caption text-warning-text">
            {tSeller("content.archiveConfirm.body")}
          </p>
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="tertiary"
              disabled={archive.isPending}
              onClick={() => {
                setConfirmingArchive(false);
              }}
            >
              {tSeller("content.archiveConfirm.cancel")}
            </Button>
            <Button
              type="button"
              variant="primary"
              loading={archive.isPending}
              onClick={() => {
                archive.mutate(tip.id, {
                  onSettled: () => {
                    setConfirmingArchive(false);
                  },
                });
              }}
            >
              {tSeller("content.archiveConfirm.confirm")}
            </Button>
          </div>
        </div>
      ) : null}

      {canPublish && tip.status === "IN_REVIEW" && !isOwnTip ? (
        <div className="mt-4 flex flex-col gap-3 rounded-xl border border-border bg-background-alt px-4 py-3">
          <p className="text-body font-semibold text-text-primary">{t("content.publishTitle")}</p>
          <p className="text-caption text-text-secondary">{t("content.publishBody")}</p>
          <ReasonField value={reason} onChange={setReason} showError={reasonTouched} />
          <ApiErrorNote error={publish.error} />
          <div className="flex justify-end">
            <Button
              type="button"
              variant="primary"
              loading={publish.isPending}
              onClick={() => {
                setReasonTouched(true);
                if (!isReasonValid(reason)) return;
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
        </div>
      ) : null}
    </>
  );
}

export function SellerContentPage() {
  const { t } = useTranslation("admin");
  const { canWrite } = useCareTipAccess();
  const [locale, setLocale] = useState("");
  const [status, setStatus] = useState<StatusFilter>("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const list = useAdminCareTips(locale, status, page);
  const rowsRef = useRef<HTMLTableSectionElement>(null);
  useListReveal(rowsRef, `${status}:${locale}:${String(page)}:${String(list.data !== undefined)}`);
  const items = list.data?.items ?? [];
  const selected = items.find((item) => item.id === selectedId) ?? null;

  return (
    <div className="flex flex-col gap-4">
      <header data-reveal className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h1 className="text-h2 text-text-primary">{t("pages.content.title")}</h1>
          <p className="text-caption text-text-secondary">{t("pages.content.description")}</p>
        </div>
        {canWrite ? (
          <Button
            variant="primary"
            size="sm"
            onClick={() => {
              setCreating(true);
            }}
          >
            <Plus className="size-4" aria-hidden="true" />
            {t("content.createCta")}
          </Button>
        ) : null}
      </header>

      <div data-reveal className="flex flex-wrap items-end justify-between gap-3">
        <Tabs
          value={status === "" ? "ALL" : status}
          onValueChange={(v) => {
            setStatus(v === "ALL" ? "" : (v as CareTipStatus));
            setPage(0);
          }}
        >
          <TabsList className="max-w-full overflow-x-auto">
            {FILTERS.map((f) => (
              <TabsTrigger key={f === "" ? "ALL" : f} value={f === "" ? "ALL" : f}>
                {f === "" ? t("content.filterAll") : t(`content.status.${f}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <div className="w-full sm:w-56">
          <SelectField
            id="seller-content-filter-locale"
            label={t("content.filterLocale")}
            value={locale}
            onChange={(value) => {
              setLocale(value);
              setPage(0);
            }}
          >
            <option value="">{t("content.filterAll")}</option>
            <option value="vi">{t("content.localeVi")}</option>
            <option value="en">{t("content.localeEn")}</option>
          </SelectField>
        </div>
      </div>

      {list.isPending ? (
        <div className="flex flex-col gap-2" role="status" aria-label={t("feedback.loading")}>
          {[0, 1, 2, 3, 4].map((i) => (
            <SkeletonLoader key={i} className="h-10" />
          ))}
        </div>
      ) : null}
      {list.isError ? (
        <ErrorState
          title={list.error.message || t("feedback.unknownError")}
          onRetry={() => {
            void list.refetch();
          }}
        />
      ) : null}
      {list.data && items.length === 0 ? (
        <EmptyState icon={<FileText className="size-6" />} title={t("feedback.empty")} />
      ) : null}

      {items.length > 0 && list.data ? (
        <>
          <div className="max-h-[70vh] overflow-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-[820px] border-collapse" aria-label={t("content.listTitle")}>
              <thead>
                <tr>
                  <th className={thClass} scope="col">
                    {t("content.colTitle")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("content.colLocale")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("content.colKind")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("content.colStatus")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("content.colClaim")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("content.colUpdatedAt")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("content.colActions")}
                  </th>
                </tr>
              </thead>
              <tbody ref={rowsRef}>
                {items.map((tip) => (
                  <tr data-reveal-item key={tip.id} className="border-t border-border">
                    <td className={tdClass}>
                      <span className="font-semibold">{tip.title}</span>
                      <code className="block text-small text-text-tertiary">{tip.slug}</code>
                    </td>
                    <td className={tdClass}>
                      <code className="uppercase">{tip.locale}</code>
                    </td>
                    <td className={tdClass}>{t(`content.kind.${tip.kind}`)}</td>
                    <td className={tdClass}>
                      <Badge tone={STATUS_TONE[tip.status]}>{t(`content.status.${tip.status}`)}</Badge>
                    </td>
                    <td className={tdClass}>
                      <span>{t(`content.claim.${tip.claimType}`)}</span>
                      {tip.claimType !== "NONE" && (tip.sourceReference == null || tip.sourceReference.length === 0) ? (
                        <span className="flex items-center gap-1 pt-0.5 text-small text-warning-text">
                          <AlertTriangle className="size-3" aria-hidden="true" />
                          {t("content.sourceMissingShort")}
                        </span>
                      ) : null}
                    </td>
                    <td className={tdClass}>
                      <span className="whitespace-nowrap">{formatDate(tip.updatedAt, "dd/MM/yyyy HH:mm")}</span>
                    </td>
                    <td className={tdClass}>
                      <Button
                        variant="tertiary"
                        size="sm"
                        onClick={() => {
                          setSelectedId(tip.id);
                        }}
                      >
                        {t("actions.open")}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={list.data.number}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            onPageChange={setPage}
          />
        </>
      ) : null}

      <Sheet
        open={creating && canWrite}
        onOpenChange={(open) => {
          if (!open) setCreating(false);
        }}
      >
        <SheetContent className="mx-auto max-w-2xl">
          <SheetTitle>{t("content.createTitle")}</SheetTitle>
          <SheetDescription>{t("content.createDescription")}</SheetDescription>
          <CreateCareTipForm
            onDone={() => {
              setCreating(false);
            }}
          />
        </SheetContent>
      </Sheet>

      <Sheet
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedId(null);
        }}
      >
        <SheetContent className="mx-auto max-w-2xl">
          {selected !== null ? (
            <CareTipEditor
              key={selected.id}
              tip={selected}
              onClose={() => {
                setSelectedId(null);
              }}
            />
          ) : null}
        </SheetContent>
      </Sheet>
    </div>
  );
}

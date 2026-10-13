import { useId, useRef, useState, type ReactNode, type SelectHTMLAttributes } from "react";
import { useTranslation } from "react-i18next";
import { FlaskConical, Palette, Plus, Trash2 } from "lucide-react";
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
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/shared/ui";
import { formatPh } from "@/shared/lib/format/formatPh";
import { cn } from "@/shared/lib/cn";
import { ROLES } from "@/shared/auth/roles";
import { hasRole, useSessionStore } from "@/entities/user";
import { phTokenStyle, type PhBand } from "@/entities/ph-bands";
import {
  ApiErrorNote,
  CHART_STATUSES,
  PRODUCT_LINES,
  Pager,
  ReasonField,
  SavedNote,
  isHexColor,
  isReasonValid,
  useAdminColorChart,
  useAdminColorCharts,
  useAdminPhBands,
  useCreateColorChart,
  usePublishColorChart,
  useReplaceChartPoints,
  useUpdateColorChart,
  useUpdatePhBand,
  type ChartStatus,
  type ColorChartPoint,
  type ColorChartPointInput,
  type ProductLine,
} from "@/features/admin";
import { useListReveal } from "@/shared/lib/motion/usePageReveal";

const STATUS_TONE: Record<ChartStatus, "neutral" | "success" | "warning"> = {
  DRAFT: "warning",
  ACTIVE: "success",
  ARCHIVED: "neutral",
};

const controlClass =
  "w-full rounded-xl border border-border bg-surface px-3 text-body text-text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)] disabled:cursor-not-allowed disabled:opacity-50";

const thClass =
  "sticky top-0 z-10 bg-background-alt px-3 py-2 text-left text-caption font-semibold whitespace-nowrap text-text-secondary";
const tdClass = "h-10 px-3 py-1.5 text-caption text-text-primary";
const cellInputClass =
  "h-9 rounded-lg border border-border bg-surface px-2 text-caption text-text-primary disabled:opacity-50 focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-[var(--focus-ring-color)]";

function useCanEditColorChart(): boolean {
  const user = useSessionStore((state) => state.user);
  return hasRole(user, ROLES.colorChart);
}

function LoadingRows() {
  const { t } = useTranslation("admin");
  return (
    <div className="flex flex-col gap-2" role="status" aria-label={t("feedback.loading")}>
      {[0, 1, 2, 3, 4].map((i) => (
        <SkeletonLoader key={i} className="h-10" />
      ))}
    </div>
  );
}

function TableShell({ children }: { children: ReactNode }) {
  return <div className="max-h-[70vh] overflow-auto rounded-xl border border-border bg-surface">{children}</div>;
}

function LabeledSelect({
  label,
  children,
  ...props
}: { label: string; children: ReactNode } & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <div className="flex min-w-[160px] flex-col gap-1.5">
      <label htmlFor={id} className="text-caption font-semibold text-text-secondary">
        {label}
      </label>
      <select id={id} className={`${controlClass} h-12`} {...props}>
        {children}
      </select>
    </div>
  );
}

function Swatch({ hex }: { hex: string }) {
  const { t } = useTranslation("admin");
  if (!isHexColor(hex)) {
    return (
      <span
        className="inline-block size-6 shrink-0 rounded-md border border-dashed border-border align-middle"
        title={t("chart.invalidHex")}
      />
    );
  }
  return (
    <span
      className="inline-block size-6 shrink-0 rounded-md border border-border align-middle"
      style={{ backgroundColor: hex }}
      title={hex}
    />
  );
}

function CreateChartForm({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation("admin");
  const create = useCreateColorChart();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [productLine, setProductLine] = useState<ProductLine>("STANDARD");
  const [productionBatch, setProductionBatch] = useState("");

  const canSubmit = code.trim().length > 0 && name.trim().length > 0;

  return (
    <form
      className="mt-4 flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        create.mutate(
          {
            code: code.trim(),
            name: name.trim(),
            productLine,
            ...(productionBatch.trim().length > 0 ? { productionBatch: productionBatch.trim() } : {}),
          },
          {
            onSuccess: () => {
              setCode("");
              setName("");
              setProductionBatch("");
              onDone();
            },
          },
        );
      }}
    >
      <div className="grid gap-4 md:grid-cols-2">
        <Input
          label={t("chart.fieldCode")}
          helperText={t("chart.fieldCodeHelp")}
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
          }}
        />
        <Input
          label={t("chart.fieldName")}
          value={name}
          onChange={(event) => {
            setName(event.target.value);
          }}
        />
        <LabeledSelect
          label={t("chart.fieldProductLine")}
          value={productLine}
          onChange={(event) => {
            setProductLine(event.target.value as ProductLine);
          }}
        >
          {PRODUCT_LINES.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </LabeledSelect>
        <Input
          label={t("chart.fieldBatch")}
          value={productionBatch}
          onChange={(event) => {
            setProductionBatch(event.target.value);
          }}
        />
      </div>

      <ApiErrorNote error={create.error} />

      <div className="flex justify-end gap-2">
        <Button type="button" variant="tertiary" onClick={onDone}>
          {t("actions.cancel")}
        </Button>
        <Button type="submit" variant="primary" disabled={!canSubmit} loading={create.isPending}>
          {t("chart.createSubmit")}
        </Button>
      </div>
    </form>
  );
}

interface PointDraft {
  key: string;
  phValue: string;
  labL: string;
  labA: string;
  labB: string;
  toleranceDeltaE: string;
  hexSrgb: string;
  displayHex: string;
  displayNameVi: string;
  displayNameEn: string;
}

function toDraft(point: ColorChartPoint): PointDraft {
  return {
    key: point.id,
    phValue: String(point.phValue),
    labL: String(point.labL),
    labA: String(point.labA),
    labB: String(point.labB),
    toleranceDeltaE: String(point.toleranceDeltaE),
    hexSrgb: point.hexSrgb ?? "",
    displayHex: point.displayHex,
    displayNameVi: point.displayNameVi,
    displayNameEn: point.displayNameEn ?? "",
  };
}

function emptyDraft(): PointDraft {
  return {
    key: `new-${String(Date.now())}-${String(Math.random()).slice(2, 8)}`,
    phValue: "",
    labL: "",
    labA: "",
    labB: "",
    toleranceDeltaE: "",
    hexSrgb: "",
    displayHex: "",
    displayNameVi: "",
    displayNameEn: "",
  };
}

function draftToInput(draft: PointDraft): ColorChartPointInput {
  return {
    phValue: Number(draft.phValue),
    labL: Number(draft.labL),
    labA: Number(draft.labA),
    labB: Number(draft.labB),
    toleranceDeltaE: Number(draft.toleranceDeltaE),
    hexSrgb: draft.hexSrgb.trim().length > 0 ? draft.hexSrgb.trim() : null,
    displayHex: draft.displayHex.trim(),
    displayNameVi: draft.displayNameVi.trim(),
    displayNameEn: draft.displayNameEn.trim().length > 0 ? draft.displayNameEn.trim() : null,
  };
}

function isDraftComplete(draft: PointDraft): boolean {
  const numbers = [draft.phValue, draft.labL, draft.labA, draft.labB, draft.toleranceDeltaE];
  return (
    numbers.every((value) => value.trim().length > 0 && !Number.isNaN(Number(value))) &&
    isHexColor(draft.displayHex.trim()) &&
    draft.displayNameVi.trim().length > 0 &&
    (draft.hexSrgb.trim().length === 0 || isHexColor(draft.hexSrgb.trim()))
  );
}

const DRAFT_NUMBER_FIELDS = ["phValue", "labL", "labA", "labB", "toleranceDeltaE"] as const;
const DRAFT_TEXT_FIELDS = ["hexSrgb", "displayHex", "displayNameVi", "displayNameEn"] as const;

const DRAFT_FIELD_LABEL_KEY: Record<(typeof DRAFT_NUMBER_FIELDS)[number] | (typeof DRAFT_TEXT_FIELDS)[number], string> =
  {
    phValue: "chart.colPh",
    labL: "chart.colLabL",
    labA: "chart.colLabA",
    labB: "chart.colLabB",
    toleranceDeltaE: "chart.colTolerance",
    hexSrgb: "chart.colHexSrgb",
    displayHex: "chart.colDisplayHex",
    displayNameVi: "chart.colNameVi",
    displayNameEn: "chart.colNameEn",
  };

function PointsEditor({
  chartId,
  etag,
  points,
  editable,
}: {
  chartId: string;
  etag: string | null;
  points: ColorChartPoint[];
  editable: boolean;
}) {
  const { t } = useTranslation("admin");
  const replace = useReplaceChartPoints();
  const [drafts, setDrafts] = useState<PointDraft[]>(() => points.map(toDraft));
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);

  const allComplete = drafts.length > 0 && drafts.every(isDraftComplete);

  const patch = (key: string, field: keyof PointDraft, value: string) => {
    setDrafts((current) => current.map((draft) => (draft.key === key ? { ...draft, [field]: value } : draft)));
  };

  return (
    <div className="flex flex-col gap-4">
      <TableShell>
        <table className="w-full min-w-[980px] border-collapse" aria-label={t("chart.pointsTitle")}>
          <thead>
            <tr>
              {[...DRAFT_NUMBER_FIELDS, ...DRAFT_TEXT_FIELDS].map((field) => (
                <th key={field} className={thClass} scope="col">
                  {t(DRAFT_FIELD_LABEL_KEY[field])}
                </th>
              ))}
              <th className={thClass} scope="col">
                {t("chart.colRemove")}
              </th>
            </tr>
          </thead>
          <tbody>
            {drafts.map((draft) => (
              <tr
                key={draft.key}
                className={cn("border-t border-border", isDraftComplete(draft) ? undefined : "bg-warning-bg/40")}
              >
                {DRAFT_NUMBER_FIELDS.map((field) => (
                  <td key={field} className={tdClass}>
                    <input
                      type="number"
                      step="any"
                      disabled={!editable}
                      aria-label={t(DRAFT_FIELD_LABEL_KEY[field])}
                      value={draft[field]}
                      onChange={(event) => {
                        patch(draft.key, field, event.target.value);
                      }}
                      className={cn(cellInputClass, "w-24")}
                    />
                  </td>
                ))}
                {DRAFT_TEXT_FIELDS.map((field) => (
                  <td key={field} className={tdClass}>
                    <span className="flex items-center gap-1.5">
                      {field === "displayHex" ? <Swatch hex={draft.displayHex} /> : null}
                      <input
                        type="text"
                        disabled={!editable}
                        aria-label={t(DRAFT_FIELD_LABEL_KEY[field])}
                        value={draft[field]}
                        onChange={(event) => {
                          patch(draft.key, field, event.target.value);
                        }}
                        className={cn(cellInputClass, "w-32 font-mono")}
                      />
                    </span>
                  </td>
                ))}
                <td className={tdClass}>
                  <Button
                    type="button"
                    variant="tertiary"
                    size="sm"
                    disabled={!editable}
                    aria-label={t("chart.colRemove")}
                    onClick={() => {
                      setDrafts((current) => current.filter((item) => item.key !== draft.key));
                    }}
                  >
                    <Trash2 className="size-4 text-danger" aria-hidden="true" />
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableShell>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={!editable}
          onClick={() => {
            setDrafts((current) => [...current, emptyDraft()]);
          }}
        >
          <Plus className="size-4" aria-hidden="true" />
          {t("chart.addPoint")}
        </Button>
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          disabled={!editable}
          onClick={() => {
            setDrafts(points.map(toDraft));
          }}
        >
          {t("chart.resetPoints")}
        </Button>
        <p className="text-caption text-text-tertiary">{t("chart.pointsCount", { count: drafts.length })}</p>
      </div>

      {!allComplete && drafts.length > 0 ? (
        <p className="rounded-xl bg-warning-bg px-3 py-2 text-caption text-warning-text">
          {t("chart.pointsIncomplete")}
        </p>
      ) : null}

      <ReasonField value={reason} onChange={setReason} showError={touched} disabled={!editable} />
      <ApiErrorNote error={replace.error} />
      <SavedNote visible={replace.isSuccess} />

      <div className="flex justify-end">
        <Button
          type="button"
          variant="primary"
          disabled={!editable || !allComplete}
          loading={replace.isPending}
          onClick={() => {
            setTouched(true);
            if (!isReasonValid(reason)) {
              return;
            }
            replace.mutate(
              { chartId, etag, points: drafts.map(draftToInput), reason: reason.trim() },
              {
                onSuccess: () => {
                  setReason("");
                  setTouched(false);
                },
              },
            );
          }}
        >
          {t("chart.savePoints")}
        </Button>
      </div>
    </div>
  );
}

function ChartDetail({ chartId, canEdit }: { chartId: string; canEdit: boolean }) {
  const { t } = useTranslation("admin");
  const detail = useAdminColorChart(chartId);
  const update = useUpdateColorChart();
  const publish = usePublishColorChart();

  const [name, setName] = useState<string | null>(null);
  const [productLine, setProductLine] = useState<ProductLine | null>(null);
  const [productionBatch, setProductionBatch] = useState<string | null>(null);
  const [metaReason, setMetaReason] = useState("");
  const [metaTouched, setMetaTouched] = useState(false);
  const [publishReason, setPublishReason] = useState("");
  const [publishTouched, setPublishTouched] = useState(false);

  if (detail.isPending) {
    return (
      <>
        <SheetTitle>{t("chart.detailTitle")}</SheetTitle>
        <div className="mt-4">
          <LoadingRows />
        </div>
      </>
    );
  }

  if (detail.isError) {
    return (
      <>
        <SheetTitle>{t("chart.detailTitle")}</SheetTitle>
        <div className="mt-4">
          <ApiErrorNote error={detail.error} />
        </div>
      </>
    );
  }

  const chart = detail.data.data;
  const etag = detail.data.etag;
  const editable = chart.status === "DRAFT" && canEdit;

  return (
    <>
      <SheetTitle>{t("chart.detailOf", { code: chart.code, version: chart.version })}</SheetTitle>
      <SheetDescription>
        {t("chart.detailSubtitle", { status: t(`chart.status.${chart.status}`), source: chart.source })}
      </SheetDescription>

      <div className="mt-4 flex flex-col gap-5">
        {!editable ? (
          <p className="rounded-xl bg-warning-bg px-3 py-2 text-caption text-warning-text">
            {t("chart.readOnlyNote")}
          </p>
        ) : null}

        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            setMetaTouched(true);
            if (!isReasonValid(metaReason)) {
              return;
            }
            update.mutate(
              {
                chartId,
                etag,
                payload: {
                  name: (name ?? chart.name).trim(),
                  productLine: productLine ?? chart.productLine ?? undefined,
                  productionBatch: (productionBatch ?? chart.productionBatch ?? "").trim(),
                },
                reason: metaReason.trim(),
              },
              {
                onSuccess: () => {
                  setMetaReason("");
                  setMetaTouched(false);
                },
              },
            );
          }}
        >
          <div className="grid gap-4 md:grid-cols-3">
            <Input
              label={t("chart.fieldName")}
              value={name ?? chart.name}
              disabled={!editable}
              onChange={(event) => {
                setName(event.target.value);
              }}
            />
            <LabeledSelect
              label={t("chart.fieldProductLine")}
              value={productLine ?? chart.productLine ?? "STANDARD"}
              disabled={!editable}
              onChange={(event) => {
                setProductLine(event.target.value as ProductLine);
              }}
            >
              {PRODUCT_LINES.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </LabeledSelect>
            <Input
              label={t("chart.fieldBatch")}
              value={productionBatch ?? chart.productionBatch ?? ""}
              disabled={!editable}
              onChange={(event) => {
                setProductionBatch(event.target.value);
              }}
            />
          </div>

          <ReasonField value={metaReason} onChange={setMetaReason} showError={metaTouched} disabled={!editable} />
          <ApiErrorNote error={update.error} />
          <SavedNote visible={update.isSuccess} />

          <div className="flex justify-end">
            <Button type="submit" variant="primary" disabled={!editable} loading={update.isPending}>
              {t("chart.saveMeta")}
            </Button>
          </div>
        </form>

        <section className="flex flex-col gap-3 border-t border-border pt-4">
          <h3 className="text-h3 text-text-primary">{t("chart.pointsTitle")}</h3>
          <PointsEditor
            key={chart.points.map((point) => point.id).join(",")}
            chartId={chartId}
            etag={etag}
            points={chart.points}
            editable={editable}
          />
        </section>

        {editable ? (
          <section className="flex flex-col gap-3 rounded-xl border border-border bg-background-alt p-4">
            <h3 className="text-body font-semibold text-text-primary">{t("chart.publishTitle")}</h3>
            <p className="text-caption text-text-secondary">{t("chart.publishBody")}</p>
            <ReasonField value={publishReason} onChange={setPublishReason} showError={publishTouched} />
            <ApiErrorNote error={publish.error} />
            <div className="flex justify-end">
              <Button
                type="button"
                variant="primary"
                loading={publish.isPending}
                onClick={() => {
                  setPublishTouched(true);
                  if (!isReasonValid(publishReason)) {
                    return;
                  }
                  publish.mutate(
                    { chartId, reason: publishReason.trim() },
                    {
                      onSuccess: () => {
                        setPublishReason("");
                        setPublishTouched(false);
                      },
                    },
                  );
                }}
              >
                {t("chart.publishCta")}
              </Button>
            </div>
          </section>
        ) : null}
      </div>
    </>
  );
}

function BandEditor({ band, etag, onClose }: { band: PhBand; etag: string | null; onClose: () => void }) {
  const { t, i18n } = useTranslation("admin");
  const update = useUpdatePhBand();
  const isEnglish = i18n.language.startsWith("en");

  const [label, setLabel] = useState(band.label);
  const [description, setDescription] = useState(band.description);
  const [iconName, setIconName] = useState(band.iconName);
  const [triggersAlert, setTriggersAlert] = useState(band.triggersAlert);
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);

  return (
    <>
      <SheetTitle>{t("band.editorTitle", { code: band.code })}</SheetTitle>
      <SheetDescription>
        {t("band.localeNote", { locale: isEnglish ? t("content.localeEn") : t("content.localeVi") })}
      </SheetDescription>

      <div className="mt-4 flex flex-col gap-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Input
            label={t("band.fieldLabel")}
            value={label}
            onChange={(event) => {
              setLabel(event.target.value);
            }}
          />
          <Input
            label={t("band.fieldIcon")}
            helperText={t("band.fieldIconHelp")}
            value={iconName}
            onChange={(event) => {
              setIconName(event.target.value);
            }}
          />
          <div className="md:col-span-2">
            <Input
              label={t("band.fieldDescription")}
              value={description}
              onChange={(event) => {
                setDescription(event.target.value);
              }}
            />
          </div>
        </div>

        <label className="flex items-center gap-2 text-caption text-text-secondary">
          <input
            type="checkbox"
            checked={triggersAlert}
            onChange={(event) => {
              setTriggersAlert(event.target.checked);
            }}
            className="size-4"
          />
          {t("band.fieldTriggersAlert")}
        </label>

        <p className="text-caption text-text-tertiary">{t("band.thresholdLocked")}</p>

        <ReasonField value={reason} onChange={setReason} showError={touched} />
        <ApiErrorNote error={update.error} />
        <SavedNote visible={update.isSuccess} />

        <div className="flex justify-end gap-2">
          <Button type="button" variant="tertiary" onClick={onClose}>
            {t("actions.close")}
          </Button>
          <Button
            type="button"
            variant="primary"
            loading={update.isPending}
            onClick={() => {
              setTouched(true);
              if (!isReasonValid(reason)) {
                return;
              }
              update.mutate(
                {
                  code: band.code,
                  etag,
                  payload: {
                    ...(isEnglish
                      ? { labelEn: label.trim(), descriptionEn: description.trim() }
                      : { labelVi: label.trim(), descriptionVi: description.trim() }),
                    iconName: iconName.trim(),
                    triggersAlert,
                  },
                  reason: reason.trim(),
                },
                {
                  onSuccess: () => {
                    setReason("");
                    setTouched(false);
                  },
                },
              );
            }}
          >
            {t("band.saveCta")}
          </Button>
        </div>
      </div>
    </>
  );
}

function BandsPanel({ canEdit }: { canEdit: boolean }) {
  const { t } = useTranslation("admin");
  const bands = useAdminPhBands();
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  const items = bands.data?.data ?? [];
  const etag = bands.data?.etag ?? null;
  const rowsRef = useRef<HTMLTableSectionElement>(null);
  useListReveal(rowsRef, bands.data !== undefined);
  const selected = items.find((band) => band.code === selectedCode) ?? null;

  return (
    <div className="flex flex-col gap-4">
      <p className="text-caption text-text-secondary">{t("band.description")}</p>

      {bands.isPending ? <LoadingRows /> : null}
      {bands.isError ? (
        <ErrorState
          title={t("band.title")}
          description={<ApiErrorNote error={bands.error} />}
          onRetry={() => {
            void bands.refetch();
          }}
        />
      ) : null}
      {bands.data && items.length === 0 ? (
        <EmptyState icon={<Palette className="size-6" />} title={t("feedback.empty")} />
      ) : null}

      {items.length > 0 ? (
        <TableShell>
          <table className="w-full min-w-[820px] border-collapse" aria-label={t("band.title")}>
            <thead>
              <tr>
                <th className={thClass} scope="col">
                  {t("band.colCode")}
                </th>
                <th className={thClass} scope="col">
                  {t("band.colRange")}
                </th>
                <th className={thClass} scope="col">
                  {t("band.colSeverity")}
                </th>
                <th className={thClass} scope="col">
                  {t("band.colLabel")}
                </th>
                <th className={thClass} scope="col">
                  {t("band.colToken")}
                </th>
                <th className={thClass} scope="col">
                  {t("band.colIcon")}
                </th>
                <th className={thClass} scope="col">
                  {t("band.colAlert")}
                </th>
                <th className={thClass} scope="col">
                  {t("content.colActions")}
                </th>
              </tr>
            </thead>
            <tbody ref={rowsRef}>
              {items.map((band) => {
                const tokenStyle = phTokenStyle(band.colorToken);
                return (
                  <tr data-reveal-item key={band.code} className="border-t border-border">
                    <td className={tdClass}>
                      <code className="font-bold">{band.code}</code>
                    </td>
                    <td className={tdClass}>
                      <span className="whitespace-nowrap font-mono">
                        {t("band.rangeValue", {
                          min: band.phMin == null ? "−∞" : formatPh(band.phMin),
                          max: band.phMax == null ? "+∞" : formatPh(band.phMax),
                        })}
                      </span>
                    </td>
                    <td className={tdClass}>
                      <code>{band.severity}</code>
                    </td>
                    <td className={tdClass}>
                      <span className="font-semibold">{band.label}</span>
                      <span className="block max-w-[40ch] pt-0.5 text-text-tertiary">{band.description}</span>
                    </td>
                    <td className={tdClass}>
                      <span className="flex items-center gap-2">
                        <span className={`inline-block size-4 rounded-full ${tokenStyle.solid}`} aria-hidden="true" />
                        <code>{band.colorToken}</code>
                      </span>
                    </td>
                    <td className={tdClass}>
                      <code>{band.iconName}</code>
                    </td>
                    <td className={tdClass}>
                      <Badge tone={band.triggersAlert ? "warning" : "neutral"}>
                        {band.triggersAlert ? t("retention.flagYes") : t("retention.flagNo")}
                      </Badge>
                    </td>
                    <td className={tdClass}>
                      {canEdit ? (
                        <Button
                          variant="tertiary"
                          size="sm"
                          onClick={() => {
                            setSelectedCode(band.code);
                          }}
                        >
                          {t("actions.edit")}
                        </Button>
                      ) : null}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </TableShell>
      ) : null}

      <Sheet
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedCode(null);
        }}
      >
        <SheetContent className="mx-auto max-w-2xl">
          {selected !== null ? (
            <BandEditor
              key={selected.code}
              band={selected}
              etag={etag}
              onClose={() => {
                setSelectedCode(null);
              }}
            />
          ) : null}
        </SheetContent>
      </Sheet>
    </div>
  );
}

function ChartsPanel({ canEdit }: { canEdit: boolean }) {
  const { t } = useTranslation("admin");
  const [status, setStatus] = useState<ChartStatus | "">("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selectedChartId, setSelectedChartId] = useState<string | null>(null);

  const list = useAdminColorCharts(status, page);
  const rowsRef = useRef<HTMLTableSectionElement>(null);
  useListReveal(rowsRef, `${status}:${String(page)}:${String(list.data !== undefined)}`);
  const items = list.data?.items ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <p className="max-w-prose text-caption text-text-secondary">{t("chart.listDescription")}</p>
        <div className="flex flex-wrap items-end gap-3">
          <LabeledSelect
            label={t("chart.filterStatus")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as ChartStatus | "");
              setPage(0);
            }}
          >
            <option value="">{t("content.filterAll")}</option>
            {CHART_STATUSES.map((value) => (
              <option key={value} value={value}>
                {t(`chart.status.${value}`)}
              </option>
            ))}
          </LabeledSelect>
          {canEdit ? (
            <Button
              variant="primary"
              size="sm"
              onClick={() => {
                setCreating(true);
              }}
            >
              <Plus className="size-4" aria-hidden="true" />
              {t("chart.createCta")}
            </Button>
          ) : null}
        </div>
      </div>

      {list.isPending ? <LoadingRows /> : null}
      {list.isError ? (
        <ErrorState
          title={t("chart.listTitle")}
          description={<ApiErrorNote error={list.error} />}
          onRetry={() => {
            void list.refetch();
          }}
        />
      ) : null}
      {list.data && items.length === 0 ? (
        <EmptyState icon={<FlaskConical className="size-6" />} title={t("feedback.empty")} />
      ) : null}

      {list.data && items.length > 0 ? (
        <>
          <TableShell>
            <table className="w-full min-w-[820px] border-collapse" aria-label={t("chart.listTitle")}>
              <thead>
                <tr>
                  <th className={thClass} scope="col">
                    {t("chart.colCode")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("chart.colName")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("chart.colProductLine")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("chart.colBatch")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("chart.colStatus")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("chart.colSource")}
                  </th>
                  <th className={thClass} scope="col">
                    {t("content.colActions")}
                  </th>
                </tr>
              </thead>
              <tbody ref={rowsRef}>
                {items.map((chart) => (
                  <tr data-reveal-item key={chart.id} className="border-t border-border">
                    <td className={tdClass}>
                      <code className="font-bold">{chart.code}</code>
                      <span className="block text-text-tertiary">
                        {t("chart.versionLabel", { version: chart.version })}
                      </span>
                    </td>
                    <td className={tdClass}>
                      <span className="font-semibold">{chart.name}</span>
                      {chart.placeholder ? (
                        <span className="block text-warning-text">{t("chart.placeholder")}</span>
                      ) : null}
                    </td>
                    <td className={tdClass}>
                      <code>{chart.productLine ?? "—"}</code>
                    </td>
                    <td className={tdClass}>
                      <code>{chart.productionBatch ?? "—"}</code>
                    </td>
                    <td className={tdClass}>
                      <Badge tone={STATUS_TONE[chart.status]}>{t(`chart.status.${chart.status}`)}</Badge>
                    </td>
                    <td className={tdClass}>
                      <code>{chart.source}</code>
                    </td>
                    <td className={tdClass}>
                      <Button
                        variant="tertiary"
                        size="sm"
                        onClick={() => {
                          setSelectedChartId(chart.id);
                        }}
                      >
                        {t("actions.open")}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableShell>
          <Pager
            page={list.data.number}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            onPageChange={setPage}
          />
        </>
      ) : null}

      <Sheet
        open={creating && canEdit}
        onOpenChange={(open) => {
          if (!open) setCreating(false);
        }}
      >
        <SheetContent className="mx-auto max-w-2xl">
          <SheetTitle>{t("chart.createTitle")}</SheetTitle>
          <SheetDescription>{t("chart.createDescription")}</SheetDescription>
          {creating ? (
            <CreateChartForm
              onDone={() => {
                setCreating(false);
              }}
            />
          ) : null}
        </SheetContent>
      </Sheet>

      <Sheet
        open={selectedChartId !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedChartId(null);
        }}
      >
        <SheetContent className="mx-auto max-h-[90dvh] max-w-5xl overflow-y-auto">
          {selectedChartId !== null ? (
            <ChartDetail key={selectedChartId} chartId={selectedChartId} canEdit={canEdit} />
          ) : null}
        </SheetContent>
      </Sheet>
    </div>
  );
}

export function SellerColorChartPage() {
  const { t } = useTranslation("seller");
  const { t: tAdmin } = useTranslation("admin");
  const canEdit = useCanEditColorChart();

  return (
    <div className="flex flex-col gap-4">
      <header data-reveal className="flex flex-col gap-1">
        <h1 className="text-h2 text-text-primary">{t("colorChart.title")}</h1>
        <p className="text-caption text-text-secondary">{tAdmin("pages.phColorChart.description")}</p>
      </header>

      <Tabs defaultValue="charts" data-reveal>
        <TabsList className="max-w-full overflow-x-auto">
          <TabsTrigger value="charts">{tAdmin("chart.tabCharts")}</TabsTrigger>
          <TabsTrigger value="bands">{tAdmin("chart.tabBands")}</TabsTrigger>
        </TabsList>

        <TabsContent value="charts" className="tab-content-enter pt-4">
          <ChartsPanel canEdit={canEdit} />
        </TabsContent>

        <TabsContent value="bands" className="tab-content-enter pt-4">
          <BandsPanel canEdit={canEdit} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

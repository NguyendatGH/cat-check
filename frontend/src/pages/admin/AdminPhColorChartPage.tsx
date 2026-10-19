import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, CheckCircle2, Circle, Copy, Loader2, Plus, ScanLine, Trash2, X } from "lucide-react";
import { Button, Input, Tabs, TabsContent, TabsList, TabsTrigger } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import {
  AdminPageHeader,
  AdminSection,
  AdminSelect,
  ApiErrorNote,
  CHART_STATUSES,
  DetailGrid,
  PRODUCT_LINES,
  Pager,
  ReasonConfirmDialog,
  SavedNote,
  isHexColor,
  useAdminColorChart,
  useAdminColorCharts,
  useCloneColorChart,
  useCreateColorChart,
  usePublishColorChart,
  useReplaceChartPoints,
  useUpdateColorChart,
  type ChartStatus,
  type ColorChartDetail,
  type ColorChartPoint,
  type ColorChartSummary,
  type ProductLine,
} from "@/features/admin";
import { useGroup2Can } from "./group2Access";
import { BandsSection } from "./phBandsSection";
import {
  DRAFT_NUMBER_FIELDS,
  draftToInput,
  emptyDraft,
  findActiveConflict,
  isDraftComplete,
  isTechComplete,
  isUsedByScans,
  patchDraft,
  publishChecks,
  toDraft,
  type DraftTextField,
  type PointDraft,
} from "./phChartModel";
import {
  ChartBadges,
  ChartExplainer,
  ChartMetaLine,
  PlaceholderBadge,
  RowSwatchStrip,
  SwatchGrid,
  SwatchStrip,
  useChartLabels,
} from "./phChartView";

function ProductLineSelect({
  value,
  onChange,
}: {
  value: ProductLine;
  onChange: (value: ProductLine) => void;
}) {
  const { t } = useTranslation("admin");
  const labels = useChartLabels();
  return (
    <div className="flex flex-col gap-1">
      <AdminSelect
        label={t("chart.fieldProductLine")}
        value={value}
        onChange={(event) => {
          onChange(event.target.value as ProductLine);
        }}
      >
        {PRODUCT_LINES.map((line) => (
          <option key={line} value={line}>
            {labels.productLine(line)}
          </option>
        ))}
      </AdminSelect>
      <p className="text-small text-text-tertiary">{t("chart.fieldProductLineHelp")}</p>
    </div>
  );
}

function CreateChartForm({ onDone }: { onDone: (createdId: string | null) => void }) {
  const { t } = useTranslation("admin");
  const create = useCreateColorChart();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [productLine, setProductLine] = useState<ProductLine>("STANDARD");
  const [productionBatch, setProductionBatch] = useState("");

  const canSubmit = code.trim().length > 0 && name.trim().length > 0;

  return (
    <form
      className="flex flex-col gap-4"
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
            onSuccess: (created) => {
              setCode("");
              setName("");
              setProductionBatch("");
              onDone(created.id);
            },
          },
        );
      }}
    >
      <div className="grid gap-4 md:grid-cols-2">
        <Input
          label={t("chart.fieldName")}
          helperText={t("chart.fieldNameHelp")}
          value={name}
          onChange={(event) => {
            setName(event.target.value);
          }}
        />
        <Input
          label={t("chart.fieldCode")}
          helperText={t("chart.fieldCodeHelpVersion")}
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
          }}
        />
        <ProductLineSelect value={productLine} onChange={setProductLine} />
        <Input
          label={t("chart.fieldBatch")}
          helperText={t("chart.fieldBatchHelp")}
          value={productionBatch}
          onChange={(event) => {
            setProductionBatch(event.target.value);
          }}
        />
      </div>

      <ApiErrorNote error={create.error} />

      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="md" disabled={!canSubmit} loading={create.isPending}>
          {t("chart.createSubmit")}
        </Button>
        <Button
          type="button"
          variant="tertiary"
          size="md"
          onClick={() => {
            onDone(null);
          }}
        >
          {t("actions.cancel")}
        </Button>
      </div>
    </form>
  );
}

function SmallField({
  label,
  value,
  onChange,
  numeric = false,
  mono = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  numeric?: boolean;
  mono?: boolean;
}) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-small text-text-secondary">
      {label}
      <input
        type={numeric ? "number" : "text"}
        step={numeric ? "any" : undefined}
        inputMode={numeric ? "decimal" : undefined}
        value={value}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        className={cn(
          "h-10 w-full min-w-0 rounded-lg border border-border bg-surface px-2.5 text-caption text-text-primary",
          mono && "font-mono",
        )}
      />
    </label>
  );
}

function PointCard({
  draft,
  onPatch,
  onRemove,
}: {
  draft: PointDraft;
  onPatch: (field: DraftTextField, value: string) => void;
  onRemove: () => void;
}) {
  const { t } = useTranslation("admin");
  const complete = isDraftComplete(draft);
  const hex = draft.displayHex.trim();
  const valid = isHexColor(hex);
  return (
    <li
      className={cn(
        "flex flex-col gap-3 rounded-xl border bg-surface p-3",
        complete ? "border-border" : "border-warning-text/40 bg-warning-bg/30",
      )}
    >
      <div className="flex items-start gap-3">
        <span
          className={cn(
            "size-12 shrink-0 rounded-lg border",
            valid ? "border-black/10" : "border-dashed border-border",
          )}
          style={valid ? { backgroundColor: hex } : undefined}
          title={valid ? hex : t("chart.invalidHex")}
          aria-hidden="true"
        />
        <div className="grid min-w-0 flex-1 grid-cols-2 gap-2">
          <SmallField
            label={t("chart.colPh")}
            value={draft.phValue}
            numeric
            onChange={(value) => {
              onPatch("phValue", value);
            }}
          />
          <SmallField
            label={t("chart.colDisplayHex")}
            value={draft.displayHex}
            mono
            onChange={(value) => {
              onPatch("displayHex", value);
            }}
          />
        </div>
        <button
          type="button"
          aria-label={t("chart.colRemove")}
          title={t("chart.colRemove")}
          onClick={onRemove}
          className="flex size-9 shrink-0 items-center justify-center rounded-lg text-danger hover:bg-danger-bg"
        >
          <Trash2 size={16} aria-hidden="true" />
        </button>
      </div>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <SmallField
          label={t("chart.colNameVi")}
          value={draft.displayNameVi}
          onChange={(value) => {
            onPatch("displayNameVi", value);
          }}
        />
        <SmallField
          label={t("chart.colNameEn")}
          value={draft.displayNameEn}
          onChange={(value) => {
            onPatch("displayNameEn", value);
          }}
        />
      </div>
      <details open={!isTechComplete(draft)} className="rounded-lg bg-background-alt/60 p-2.5">
        <summary className="cursor-pointer text-small font-semibold text-text-secondary">
          {t("chart.techTitle")}
        </summary>
        <p className="pt-1 text-small text-text-tertiary">{t("chart.techHelp")}</p>
        <div className="grid grid-cols-2 gap-2 pt-2 sm:grid-cols-5">
          {DRAFT_NUMBER_FIELDS.filter((field) => field !== "phValue").map((field) => (
            <SmallField
              key={field}
              label={t(`chart.field.${field}`)}
              value={draft[field]}
              numeric
              onChange={(value) => {
                onPatch(field, value);
              }}
            />
          ))}
          <SmallField
            label={t("chart.colHexSrgb")}
            value={draft.hexSrgb}
            mono
            onChange={(value) => {
              onPatch("hexSrgb", value);
            }}
          />
        </div>
      </details>
    </li>
  );
}

function PointsEditor({ chart, etag }: { chart: ColorChartDetail; etag: string | null }) {
  const { t } = useTranslation("admin");
  const replace = useReplaceChartPoints();
  const [drafts, setDrafts] = useState<PointDraft[]>(() => chart.points.map(toDraft));
  const [confirming, setConfirming] = useState(false);

  const allComplete = drafts.length > 0 && drafts.every(isDraftComplete);
  const previewStops = drafts
    .filter((draft) => draft.phValue.trim() !== "" && !Number.isNaN(Number(draft.phValue)))
    .map((draft) => ({ key: draft.key, phValue: Number(draft.phValue), hex: draft.displayHex.trim() }))
    .sort((a, b) => a.phValue - b.phValue);

  const patch = (key: string, field: DraftTextField, value: string) => {
    setDrafts((current) => current.map((draft) => (draft.key === key ? patchDraft(draft, field, value) : draft)));
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <p className="text-small font-semibold text-text-secondary">{t("chart.previewTitle")}</p>
        <SwatchStrip stops={previewStops} />
      </div>

      <ul className="grid gap-3 lg:grid-cols-2">
        {drafts.map((draft) => (
          <PointCard
            key={draft.key}
            draft={draft}
            onPatch={(field, value) => {
              patch(draft.key, field, value);
            }}
            onRemove={() => {
              setDrafts((current) => current.filter((item) => item.key !== draft.key));
            }}
          />
        ))}
      </ul>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          leftIcon={<Plus size={15} />}
          onClick={() => {
            setDrafts((current) => [...current, emptyDraft()]);
          }}
        >
          {t("chart.addPoint")}
        </Button>
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          onClick={() => {
            setDrafts(chart.points.map(toDraft));
          }}
        >
          {t("chart.resetPoints")}
        </Button>
        <p className="text-small text-text-tertiary">{t("chart.pointsCount", { count: drafts.length })}</p>
      </div>

      {!allComplete && drafts.length > 0 ? (
        <p className="text-caption text-warning-text">{t("chart.pointsIncomplete")}</p>
      ) : null}
      <SavedNote visible={replace.isSuccess} />

      <Button
        type="button"
        size="md"
        className="self-start"
        disabled={!allComplete}
        onClick={() => {
          replace.reset();
          setConfirming(true);
        }}
      >
        {t("chart.savePoints")}
      </Button>

      <ReasonConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("chart.savePointsConfirmTitle")}
        target={`${chart.name} · ${t("chart.versionLabel", { version: chart.version })}`}
        consequence={t("chart.savePointsConsequence", { count: drafts.length })}
        confirmLabel={t("chart.savePoints")}
        pending={replace.isPending}
        error={replace.error}
        onConfirm={(reason) => {
          replace.mutate(
            { chartId: chart.id, etag, points: drafts.map(draftToInput), reason },
            {
              onSuccess: () => {
                setConfirming(false);
              },
            },
          );
        }}
      />
    </div>
  );
}

function MetaEditor({ chart, etag }: { chart: ColorChartDetail; etag: string | null }) {
  const { t } = useTranslation("admin");
  const update = useUpdateColorChart();
  const [name, setName] = useState(chart.name);
  const [productLine, setProductLine] = useState<ProductLine>(chart.productLine ?? "STANDARD");
  const [productionBatch, setProductionBatch] = useState(chart.productionBatch ?? "");
  const [confirming, setConfirming] = useState(false);

  const dirty =
    name.trim() !== chart.name ||
    productLine !== (chart.productLine ?? "STANDARD") ||
    productionBatch.trim() !== (chart.productionBatch ?? "");

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        update.reset();
        setConfirming(true);
      }}
    >
      <div className="grid gap-4 md:grid-cols-3">
        <Input
          label={t("chart.fieldName")}
          helperText={t("chart.fieldNameHelp")}
          value={name}
          onChange={(event) => {
            setName(event.target.value);
          }}
        />
        <ProductLineSelect value={productLine} onChange={setProductLine} />
        <Input
          label={t("chart.fieldBatch")}
          helperText={t("chart.fieldBatchHelp")}
          value={productionBatch}
          onChange={(event) => {
            setProductionBatch(event.target.value);
          }}
        />
      </div>
      <SavedNote visible={update.isSuccess} />
      <Button type="submit" size="md" className="self-start" disabled={!dirty || name.trim().length === 0}>
        {t("chart.saveMeta")}
      </Button>

      <ReasonConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("chart.saveMetaConfirmTitle")}
        target={`${chart.name} · ${t("chart.versionLabel", { version: chart.version })}`}
        consequence={t("chart.saveMetaConsequence")}
        confirmLabel={t("chart.saveMeta")}
        pending={update.isPending}
        error={update.error}
        onConfirm={(reason) => {
          update.mutate(
            {
              chartId: chart.id,
              etag,
              payload: { name: name.trim(), productLine, productionBatch: productionBatch.trim() },
              reason,
            },
            {
              onSuccess: () => {
                setConfirming(false);
              },
            },
          );
        }}
      />
    </form>
  );
}

function PublishPanel({ chart, activeCharts }: { chart: ColorChartDetail; activeCharts: readonly ColorChartSummary[] }) {
  const { t } = useTranslation("admin");
  const publish = usePublishColorChart();
  const labels = useChartLabels();
  const [confirming, setConfirming] = useState(false);
  const checks = publishChecks(chart.points);
  const ready = checks.every((check) => check.ok);
  const conflict = findActiveConflict(chart, activeCharts);
  const willBeUsedByScans = isUsedByScans({ ...chart, status: "ACTIVE" });

  return (
    <div className="flex flex-col gap-3 rounded-xl bg-background-alt/70 p-4">
      <p className="text-body font-semibold text-text-primary">{t("chart.publishSection")}</p>
      <p className="text-caption text-text-secondary">
        {willBeUsedByScans ? t("chart.publishBodyScan") : t("chart.publishBodyOther")}
      </p>
      <ul className="flex flex-col gap-1" aria-label={t("chart.checksLabel")}>
        {checks.map((check) => (
          <li
            key={check.key}
            className={cn("flex items-center gap-2 text-caption", check.ok ? "text-success-text" : "text-text-secondary")}
          >
            {check.ok ? <CheckCircle2 size={15} aria-hidden="true" /> : <Circle size={15} aria-hidden="true" />}
            {t(`chart.check.${check.key}`)}
          </li>
        ))}
      </ul>
      <p className="text-small text-text-tertiary">{t("chart.checksNote")}</p>
      {conflict !== undefined ? (
        <p className="flex items-start gap-2 rounded-lg bg-warning-bg p-2.5 text-caption text-warning-text">
          <AlertTriangle size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
          {t("chart.publishConflict", {
            name: conflict.name,
            version: conflict.version,
            line: labels.productLine(conflict.productLine),
            batch: labels.batch(conflict),
          })}
        </p>
      ) : null}
      <Button
        type="button"
        size="md"
        className="self-start"
        disabled={!ready}
        onClick={() => {
          publish.reset();
          setConfirming(true);
        }}
      >
        {t("chart.publishAction")}
      </Button>

      <ReasonConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("chart.publishConfirmTitle")}
        target={`${chart.name} · ${t("chart.versionLabel", { version: chart.version })}`}
        consequence={
          conflict !== undefined
            ? t("chart.publishConsequenceReplace", { name: conflict.name, version: conflict.version })
            : willBeUsedByScans
              ? t("chart.publishConsequenceScan")
              : t("chart.publishConsequence")
        }
        confirmLabel={t("chart.publishAction")}
        pending={publish.isPending}
        error={publish.error}
        onConfirm={(reason) => {
          publish.mutate(
            { chartId: chart.id, reason },
            {
              onSuccess: () => {
                setConfirming(false);
              },
            },
          );
        }}
      />
    </div>
  );
}

function CloneAction({ chart, onCreated }: { chart: ColorChartDetail; onCreated: (id: string) => void }) {
  const { t } = useTranslation("admin");
  const clone = useCloneColorChart();
  const [confirming, setConfirming] = useState(false);
  return (
    <>
      <Button
        type="button"
        size="md"
        className="self-start"
        leftIcon={<Copy size={15} />}
        onClick={() => {
          clone.reset();
          setConfirming(true);
        }}
      >
        {t("chart.cloneCta")}
      </Button>
      <ReasonConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("chart.cloneConfirmTitle")}
        target={`${chart.name} · ${t("chart.versionLabel", { version: chart.version })}`}
        consequence={t("chart.cloneConsequence", { count: chart.points.length })}
        confirmLabel={t("chart.cloneCta")}
        pending={clone.isPending}
        error={clone.error}
        onConfirm={(reason) => {
          clone.mutate(
            { source: chart, reason },
            {
              onSuccess: (created) => {
                setConfirming(false);
                onCreated(created.id);
              },
            },
          );
        }}
      />
    </>
  );
}

function ChartDetailPanel({
  chartId,
  activeCharts,
  onClose,
  onSelect,
}: {
  chartId: string;
  activeCharts: readonly ColorChartSummary[];
  onClose: () => void;
  onSelect: (id: string) => void;
}) {
  const { t } = useTranslation("admin");
  const detail = useAdminColorChart(chartId);
  const canEdit = useGroup2Can()("colorChart");
  const labels = useChartLabels();
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (el !== null && "scrollIntoView" in el) {
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [chartId]);

  const closeButton = (
    <Button type="button" variant="tertiary" size="sm" leftIcon={<X size={15} />} onClick={onClose}>
      {t("actions.close")}
    </Button>
  );

  if (detail.isPending || detail.isError) {
    return (
      <div ref={ref}>
        <AdminSection title={t("chart.detailTitle")} actions={closeButton}>
          {detail.isPending ? (
            <p className="flex items-center gap-2 text-caption text-text-secondary">
              <Loader2 size={15} className="animate-spin" aria-hidden="true" />
              {t("feedback.loading")}
            </p>
          ) : (
            <ApiErrorNote error={detail.error} />
          )}
        </AdminSection>
      </div>
    );
  }

  const chart = detail.data.data;
  const etag = detail.data.etag;
  const isDraft = chart.status === "DRAFT";
  const editable = isDraft && canEdit;
  const pointsKey = chart.points.map((point: ColorChartPoint) => point.id).join(",");

  return (
    <div ref={ref} className="scroll-mt-4">
      <AdminSection
        title={chart.name}
        description={t("chart.detailOf", { code: chart.code, version: chart.version })}
        actions={closeButton}
      >
        <ChartBadges chart={chart} />
        {chart.placeholder ? (
          <p className="flex items-start gap-2 rounded-lg bg-warning-bg p-3 text-caption text-warning-text">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
            {t("chart.placeholderHelp")}
          </p>
        ) : null}

        <DetailGrid
          items={[
            [t("chart.metaProductLine"), labels.productLine(chart.productLine)],
            [
              t("chart.metaBatch"),
              <span key="batch">
                {labels.batch(chart)}
                <span className="block text-small text-text-tertiary">
                  {chart.productionBatch ? t("chart.batchSpecificHelp") : t("chart.batchDefaultHelp")}
                </span>
              </span>,
            ],
            [
              t("chart.metaSource"),
              <span key="source">
                {labels.source(chart.source)}
                <span className="block text-small text-text-tertiary">
                  {t(`chart.sourceHelp.${chart.source}`, "")}
                </span>
              </span>,
            ],
            [
              t("chart.metaScan"),
              isUsedByScans(chart) ? t("chart.metaScanYes") : t("chart.metaScanNo"),
            ],
          ]}
        />

        {editable ? (
          <>
            <section className="flex flex-col gap-3 border-t border-border pt-4">
              <div>
                <h3 className="text-body font-bold text-text-primary">{t("chart.metaTitle")}</h3>
                <p className="text-small text-text-tertiary">{t("chart.metaHelp")}</p>
              </div>
              <MetaEditor key={`${chart.name}|${chart.productLine ?? ""}|${chart.productionBatch ?? ""}`} chart={chart} etag={etag} />
            </section>
            <section className="flex flex-col gap-3 border-t border-border pt-4">
              <div>
                <h3 className="text-body font-bold text-text-primary">{t("chart.pointsTitle")}</h3>
                <p className="text-small text-text-tertiary">{t("chart.pointsHelp")}</p>
              </div>
              <PointsEditor key={pointsKey} chart={chart} etag={etag} />
            </section>
            <PublishPanel chart={chart} activeCharts={activeCharts} />
          </>
        ) : (
          <section className="flex flex-col gap-3 border-t border-border pt-4">
            <h3 className="text-body font-bold text-text-primary">{t("chart.pointsTitle")}</h3>
            <SwatchGrid points={chart.points} />
            <p className="text-caption text-text-secondary">
              {isDraft ? t("chart.readOnlyNoPermission") : t("chart.readOnlyPublished")}
            </p>
            {canEdit && !isDraft ? <CloneAction chart={chart} onCreated={onSelect} /> : null}
          </section>
        )}
      </AdminSection>
    </div>
  );
}

function ScanChartSummary({
  activeCharts,
  loading,
  onOpen,
}: {
  activeCharts: readonly ColorChartSummary[];
  loading: boolean;
  onOpen: (id: string) => void;
}) {
  const { t } = useTranslation("admin");
  const current = activeCharts.find(isUsedByScans);
  if (loading) {
    return null;
  }
  if (current === undefined) {
    return (
      <p className="flex items-start gap-2 rounded-2xl bg-danger-bg p-4 text-caption text-danger-text">
        <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
        {t("chart.scanNone")}
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-info-text/20 bg-info/40 p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="flex items-center gap-1.5 text-small font-semibold text-info-text">
            <ScanLine size={14} aria-hidden="true" />
            {t("chart.scanCurrentLabel")}
          </p>
          <p className="text-body font-bold text-text-primary">
            {current.name}{" "}
            <span className="text-caption font-normal text-text-secondary">
              {t("chart.versionLabel", { version: current.version })}
            </span>
          </p>
        </div>
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          onClick={() => {
            onOpen(current.id);
          }}
        >
          {t("chart.viewCta")}
        </Button>
      </div>
      {current.placeholder ? (
        <span className="self-start">
          <PlaceholderBadge />
        </span>
      ) : null}
      <RowSwatchStrip chartId={current.id} />
    </div>
  );
}

function ChartRow({
  chart,
  selected,
  onOpen,
}: {
  chart: ColorChartSummary;
  selected: boolean;
  onOpen: () => void;
}) {
  const { t } = useTranslation("admin");
  return (
    <li
      className={cn(
        "flex flex-col gap-2 border-b border-border/60 px-1 py-3 last:border-b-0 md:flex-row md:items-center md:gap-4",
        selected && "rounded-lg bg-info/40",
        isUsedByScans(chart) && !selected && "rounded-lg bg-success-bg/40",
      )}
    >
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <div className="flex flex-wrap items-baseline gap-x-2">
          <span className="text-body font-bold text-text-primary">{chart.name}</span>
          <span className="text-small text-text-tertiary">
            {chart.code} · {t("chart.versionLabel", { version: chart.version })}
          </span>
        </div>
        <ChartBadges chart={chart} />
        <ChartMetaLine chart={chart} />
      </div>
      <div className="md:w-[300px] md:shrink-0">
        <RowSwatchStrip chartId={chart.id} />
      </div>
      <Button type="button" variant="tertiary" size="sm" className="self-start md:self-center" onClick={onOpen}>
        {chart.status === "DRAFT" ? t("chart.editCta") : t("chart.viewCta")}
      </Button>
    </li>
  );
}

export function AdminPhColorChartPage() {
  const { t } = useTranslation("admin");
  const [status, setStatus] = useState<ChartStatus | "">("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selectedChartId, setSelectedChartId] = useState<string | null>(null);
  const canEdit = useGroup2Can()("colorChart");

  const list = useAdminColorCharts(status, page);
  const activeQuery = useAdminColorCharts("ACTIVE", 0, 100);
  const activeCharts = activeQuery.data?.items ?? [];
  const items = list.data?.items ?? [];

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader title={t("pages.phColorChart.title")} description={t("pages.phColorChart.description")} />

      <Tabs defaultValue="charts">
        <TabsList>
          <TabsTrigger value="charts">{t("chart.tabCharts")}</TabsTrigger>
          <TabsTrigger value="bands">{t("chart.tabBands")}</TabsTrigger>
        </TabsList>

        <TabsContent value="charts">
          <div className="flex flex-col gap-5">
            <ChartExplainer />

            <ScanChartSummary
              activeCharts={activeCharts}
              loading={activeQuery.isPending || activeQuery.isError}
              onOpen={setSelectedChartId}
            />

            {creating && canEdit ? (
              <AdminSection title={t("chart.createTitle")} description={t("chart.createDescription")}>
                <CreateChartForm
                  onDone={(createdId) => {
                    setCreating(false);
                    if (createdId !== null) {
                      setSelectedChartId(createdId);
                    }
                  }}
                />
              </AdminSection>
            ) : null}

            <AdminSection
              title={t("chart.listTitle")}
              description={t("chart.listDescription")}
              actions={
                canEdit ? (
                  <Button
                    type="button"
                    size="md"
                    leftIcon={<Plus size={16} />}
                    onClick={() => {
                      setCreating((value) => !value);
                    }}
                  >
                    {t("chart.createCta")}
                  </Button>
                ) : undefined
              }
            >
              <div className="flex flex-wrap gap-4">
                <AdminSelect
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
                  <ul aria-label={t("chart.listTitle")}>
                    {items.map((chart) => (
                      <ChartRow
                        key={chart.id}
                        chart={chart}
                        selected={chart.id === selectedChartId}
                        onOpen={() => {
                          setSelectedChartId(chart.id);
                        }}
                      />
                    ))}
                  </ul>
                  <Pager
                    page={list.data.number}
                    totalPages={list.data.totalPages}
                    totalElements={list.data.totalElements}
                    onPageChange={setPage}
                  />
                </>
              )}
            </AdminSection>

            {selectedChartId !== null ? (
              <ChartDetailPanel
                key={selectedChartId}
                chartId={selectedChartId}
                activeCharts={activeCharts}
                onSelect={setSelectedChartId}
                onClose={() => {
                  setSelectedChartId(null);
                }}
              />
            ) : null}
          </div>
        </TabsContent>

        <TabsContent value="bands">
          <BandsSection />
        </TabsContent>
      </Tabs>
    </div>
  );
}

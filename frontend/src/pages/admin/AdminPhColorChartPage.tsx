import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Loader2, Plus, Trash2, X } from "lucide-react";
import { Button, Input, Tabs, TabsContent, TabsList, TabsTrigger } from "@/shared/ui";
import { formatPh } from "@/shared/lib/format/formatPh";
import { cn } from "@/shared/lib/cn";
import { phTokenStyle, type PhBand } from "@/entities/ph-bands";
import {
  AdminPageHeader,
  AdminSection,
  AdminSelect,
  AdminTableScroll,
  ApiErrorNote,
  CHART_STATUSES,
  PRODUCT_LINES,
  Pager,
  ReasonField,
  SavedNote,
  adminTableClass,
  adminTdClass,
  adminThClass,
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

/**
 * `/admin/ph-color-chart` — quản trị bảng màu pH và dải phân loại.
 *
 * NỐI API THẬT, toàn bộ `colorchart/api/AdminColorChartController`:
 * L27 `GET /admin/color-charts` · L28 `POST /admin/color-charts` ·
 * L29 `GET /admin/color-charts/{chartId}` · L30 `PATCH …` ·
 * L31 `PUT …/points` · L32 `POST …/publish` ·
 * L36 `GET /admin/ph-classification-bands` · L37 `PATCH …/{code}`.
 *
 * Hai điều phải giữ đúng ở màn này:
 *
 * 1. **`If-Match`** — L30/L31/L37 bắt buộc; giá trị lấy nguyên văn từ header `ETag` của
 *    lần GET gần nhất (`useAdminColorChart`/`useAdminPhBands` trả cả body lẫn ETag).
 *    Sửa khi ETag cũ ⇒ server trả 412, đúng ý đồ chống ghi đè.
 * 2. **KHÔNG hard-code giá trị pH** — mọi `phValue`, `phMin`, `phMax` hiển thị đều lấy
 *    từ response. Màu swatch cũng vậy: `displayHex` là dữ liệu, không phải literal trong
 *    mã (ESLint chặn literal hex trong `.tsx`).
 *
 * Ngưỡng `minPh`/`maxPh` của dải phân loại CHỈ ĐỌC ở đây: p8 L37 ghi "đổi ngưỡng ⇒
 * step-up", mà lớp step-up (`S1:TOTP`) chưa được nối — javadoc controller nói rõ. Mở ô
 * nhập ngưỡng lúc này là mở đúng thứ spec bắt phải khoá.
 */

const CHART_STATUS_CLASS: Record<ChartStatus, string> = {
  DRAFT: "bg-background-alt text-text-secondary",
  ACTIVE: "bg-success-bg text-success-text",
  ARCHIVED: "bg-chip-bg text-text-tertiary",
};

/** Ô màu lấy thẳng từ dữ liệu server — không có literal màu nào trong file này. */
function Swatch({ hex }: { hex: string }) {
  const { t } = useTranslation("admin");
  if (!isHexColor(hex)) {
    return (
      <span
        className="inline-block size-6 rounded-md border border-dashed border-border align-middle"
        title={t("chart.invalidHex")}
      />
    );
  }
  return (
    <span
      className="inline-block size-6 rounded-md border border-border align-middle"
      style={{ backgroundColor: hex }}
      title={hex}
    />
  );
}

// ------------------------------------------------------------- tạo bản DRAFT

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
        <AdminSelect
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
        </AdminSelect>
        <Input
          label={t("chart.fieldBatch")}
          value={productionBatch}
          onChange={(event) => {
            setProductionBatch(event.target.value);
          }}
        />
      </div>

      <ApiErrorNote error={create.error} />

      <div className="flex gap-2">
        <Button type="submit" size="md" disabled={!canSubmit} loading={create.isPending}>
          {t("chart.createSubmit")}
        </Button>
        <Button type="button" variant="tertiary" size="md" onClick={onDone}>
          {t("actions.cancel")}
        </Button>
      </div>
    </form>
  );
}

// ------------------------------------------------------------- soạn các điểm

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

/** Khoá i18n cho nhãn cột — dùng lại làm `aria-label` của ô nhập trong cùng cột. */
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
      <AdminTableScroll>
        <table className={cn(adminTableClass, "min-w-[980px]")}>
          <thead>
            <tr>
              <th className={adminThClass}>{t("chart.colPh")}</th>
              <th className={adminThClass}>{t("chart.colLabL")}</th>
              <th className={adminThClass}>{t("chart.colLabA")}</th>
              <th className={adminThClass}>{t("chart.colLabB")}</th>
              <th className={adminThClass}>{t("chart.colTolerance")}</th>
              <th className={adminThClass}>{t("chart.colHexSrgb")}</th>
              <th className={adminThClass}>{t("chart.colDisplayHex")}</th>
              <th className={adminThClass}>{t("chart.colNameVi")}</th>
              <th className={adminThClass}>{t("chart.colNameEn")}</th>
              <th className={adminThClass}>{t("chart.colRemove")}</th>
            </tr>
          </thead>
          <tbody>
            {drafts.map((draft) => (
              <tr key={draft.key} className={isDraftComplete(draft) ? undefined : "bg-warning-bg/40"}>
                {DRAFT_NUMBER_FIELDS.map((field) => (
                  <td key={field} className={adminTdClass}>
                    <input
                      type="number"
                      step="any"
                      disabled={!editable}
                      aria-label={t(DRAFT_FIELD_LABEL_KEY[field])}
                      value={draft[field]}
                      onChange={(event) => {
                        patch(draft.key, field, event.target.value);
                      }}
                      className="h-9 w-24 rounded-lg bg-background-alt/70 px-2 text-caption text-text-primary disabled:opacity-50"
                    />
                  </td>
                ))}
                {DRAFT_TEXT_FIELDS.map((field) => (
                  <td key={field} className={adminTdClass}>
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
                        className="h-9 w-32 rounded-lg bg-background-alt/70 px-2 font-mono text-caption text-text-primary disabled:opacity-50"
                      />
                    </span>
                  </td>
                ))}
                <td className={adminTdClass}>
                  <button
                    type="button"
                    disabled={!editable}
                    aria-label={t("chart.colRemove")}
                    onClick={() => {
                      setDrafts((current) => current.filter((item) => item.key !== draft.key));
                    }}
                    className="text-danger disabled:opacity-40"
                  >
                    <Trash2 size={15} aria-hidden="true" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </AdminTableScroll>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          disabled={!editable}
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
          disabled={!editable}
          onClick={() => {
            setDrafts(points.map(toDraft));
          }}
        >
          {t("chart.resetPoints")}
        </Button>
        <p className="text-small text-text-tertiary">{t("chart.pointsCount", { count: drafts.length })}</p>
      </div>

      {!allComplete && drafts.length > 0 ? (
        <p className="text-caption text-warning-text">{t("chart.pointsIncomplete")}</p>
      ) : null}

      <ReasonField value={reason} onChange={setReason} showError={touched} disabled={!editable} />
      <ApiErrorNote error={replace.error} />
      <SavedNote visible={replace.isSuccess} />

      <Button
        type="button"
        size="md"
        className="self-start"
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
  );
}

// ------------------------------------------------------------- chi tiết bảng

function ChartDetailPanel({ chartId, onClose }: { chartId: string; onClose: () => void }) {
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
      <AdminSection title={t("chart.detailTitle")}>
        <p className="flex items-center gap-2 text-caption text-text-secondary">
          <Loader2 size={15} className="animate-spin" aria-hidden="true" />
          {t("feedback.loading")}
        </p>
      </AdminSection>
    );
  }

  if (detail.isError) {
    return (
      <AdminSection title={t("chart.detailTitle")}>
        <ApiErrorNote error={detail.error} />
      </AdminSection>
    );
  }

  const chart = detail.data.data;
  const etag = detail.data.etag;
  /** L30 — sửa bản ACTIVE ⇒ 409 COLOR_CHART_IN_USE, nên chỉ DRAFT mới mở ô nhập. */
  const editable = chart.status === "DRAFT";

  return (
    <AdminSection
      title={t("chart.detailOf", { code: chart.code, version: chart.version })}
      description={t("chart.detailSubtitle", { status: t(`chart.status.${chart.status}`), source: chart.source })}
      actions={
        <Button type="button" variant="tertiary" size="sm" leftIcon={<X size={15} />} onClick={onClose}>
          {t("actions.close")}
        </Button>
      }
    >
      {!editable ? <p className="text-caption text-warning-text">{t("chart.readOnlyNote")}</p> : null}

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
          <AdminSelect
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
          </AdminSelect>
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

        <Button type="submit" size="md" className="self-start" disabled={!editable} loading={update.isPending}>
          {t("chart.saveMeta")}
        </Button>
      </form>

      <div className="border-t border-border pt-4">
        <h3 className="pb-3 text-body font-bold text-text-primary">{t("chart.pointsTitle")}</h3>
        <PointsEditor
          key={chart.points.map((point) => point.id).join(",")}
          chartId={chartId}
          etag={etag}
          points={chart.points}
          editable={editable}
        />
      </div>

      {editable ? (
        <div className="flex flex-col gap-3 rounded-xl bg-background-alt/70 p-4">
          <p className="text-body font-semibold text-text-primary">{t("chart.publishTitle")}</p>
          <p className="text-caption text-text-secondary">{t("chart.publishBody")}</p>
          <ReasonField value={publishReason} onChange={setPublishReason} showError={publishTouched} />
          <ApiErrorNote error={publish.error} />
          <Button
            type="button"
            size="md"
            className="self-start"
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
      ) : null}
    </AdminSection>
  );
}

// ------------------------------------------------------------- dải phân loại

function BandEditor({ band, etag, onClose }: { band: PhBand; etag: string | null; onClose: () => void }) {
  const { t, i18n } = useTranslation("admin");
  const update = useUpdatePhBand();
  /**
   * L36 trả `label`/`description` ĐÃ resolve theo `Accept-Language`, không trả riêng
   * cặp vi/en. Nên chỉ ghi lại đúng cặp của ngôn ngữ đang dùng — ghi nhãn tiếng Anh vào
   * cột tiếng Việt là làm hỏng dữ liệu của locale kia.
   */
  const isEnglish = i18n.language.startsWith("en");

  const [label, setLabel] = useState(band.label);
  const [description, setDescription] = useState(band.description);
  const [iconName, setIconName] = useState(band.iconName);
  const [triggersAlert, setTriggersAlert] = useState(band.triggersAlert);
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);

  return (
    <div className="flex flex-col gap-4 rounded-xl bg-background-alt/70 p-4">
      <div className="flex items-start justify-between gap-3">
        <p className="text-body font-semibold text-text-primary">{t("band.editorTitle", { code: band.code })}</p>
        <Button type="button" variant="tertiary" size="sm" leftIcon={<X size={15} />} onClick={onClose}>
          {t("actions.close")}
        </Button>
      </div>

      <p className="text-caption text-text-secondary">
        {t("band.localeNote", { locale: isEnglish ? t("content.localeEn") : t("content.localeVi") })}
      </p>

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
      </div>

      <Input
        label={t("band.fieldDescription")}
        value={description}
        onChange={(event) => {
          setDescription(event.target.value);
        }}
      />

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

      <p className="text-small text-text-tertiary">{t("band.thresholdLocked")}</p>

      <ReasonField value={reason} onChange={setReason} showError={touched} />
      <ApiErrorNote error={update.error} />
      <SavedNote visible={update.isSuccess} />

      <Button
        type="button"
        size="md"
        className="self-start"
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
  );
}

function BandsSection() {
  const { t } = useTranslation("admin");
  const bands = useAdminPhBands();
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  const items = bands.data?.data ?? [];
  const etag = bands.data?.etag ?? null;
  const selected = items.find((band) => band.code === selectedCode) ?? null;

  return (
    <AdminSection title={t("band.title")} description={t("band.description")}>
      {bands.isPending ? (
        <p className="flex items-center gap-2 text-caption text-text-secondary">
          <Loader2 size={15} className="animate-spin" aria-hidden="true" />
          {t("feedback.loading")}
        </p>
      ) : bands.isError ? (
        <ApiErrorNote error={bands.error} />
      ) : items.length === 0 ? (
        <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
      ) : (
        <AdminTableScroll>
          <table className={adminTableClass}>
            <thead>
              <tr>
                <th className={adminThClass}>{t("band.colCode")}</th>
                <th className={adminThClass}>{t("band.colRange")}</th>
                <th className={adminThClass}>{t("band.colSeverity")}</th>
                <th className={adminThClass}>{t("band.colLabel")}</th>
                <th className={adminThClass}>{t("band.colToken")}</th>
                <th className={adminThClass}>{t("band.colIcon")}</th>
                <th className={adminThClass}>{t("band.colAlert")}</th>
                <th className={adminThClass}>{t("content.colActions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((band) => {
                const tokenStyle = phTokenStyle(band.colorToken);
                return (
                  <tr key={band.code}>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small font-bold">{band.code}</span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="whitespace-nowrap font-mono text-small">
                        {t("band.rangeValue", {
                          min: band.phMin === null ? "−∞" : formatPh(band.phMin),
                          max: band.phMax === null ? "+∞" : formatPh(band.phMax),
                        })}
                      </span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small">{band.severity}</span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-semibold">{band.label}</span>
                      <span className="block max-w-[40ch] pt-0.5 text-small text-text-tertiary">
                        {band.description}
                      </span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="flex items-center gap-2">
                        <span className={`inline-block size-4 rounded-full ${tokenStyle.solid}`} aria-hidden="true" />
                        <span className="font-mono text-small">{band.colorToken}</span>
                      </span>
                    </td>
                    <td className={adminTdClass}>
                      <span className="font-mono text-small">{band.iconName}</span>
                    </td>
                    <td className={adminTdClass}>
                      {band.triggersAlert ? t("retention.flagYes") : t("retention.flagNo")}
                    </td>
                    <td className={adminTdClass}>
                      <button
                        type="button"
                        className="font-semibold text-primary-dark hover:underline"
                        onClick={() => {
                          setSelectedCode(band.code);
                        }}
                      >
                        {t("actions.edit")}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </AdminTableScroll>
      )}

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
    </AdminSection>
  );
}

// -------------------------------------------------------------------- trang

export function AdminPhColorChartPage() {
  const { t } = useTranslation("admin");
  const [status, setStatus] = useState<ChartStatus | "">("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selectedChartId, setSelectedChartId] = useState<string | null>(null);

  const list = useAdminColorCharts(status, page);
  const items = list.data?.items ?? [];

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.phColorChart.title")}
        description={t("pages.phColorChart.description")}
        specRef="/api/v1/admin/color-charts · /api/v1/admin/ph-classification-bands — L27–L32, L36, L37"
      />

      <Tabs defaultValue="charts">
        <TabsList>
          <TabsTrigger value="charts">{t("chart.tabCharts")}</TabsTrigger>
          <TabsTrigger value="bands">{t("chart.tabBands")}</TabsTrigger>
        </TabsList>

        <TabsContent value="charts">
          <div className="flex flex-col gap-5">
            {creating ? (
              <AdminSection title={t("chart.createTitle")} description={t("chart.createDescription")}>
                <CreateChartForm
                  onDone={() => {
                    setCreating(false);
                  }}
                />
              </AdminSection>
            ) : null}

            <AdminSection
              title={t("chart.listTitle")}
              description={t("chart.listDescription")}
              actions={
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
                  <AdminTableScroll>
                    <table className={adminTableClass}>
                      <thead>
                        <tr>
                          <th className={adminThClass}>{t("chart.colCode")}</th>
                          <th className={adminThClass}>{t("chart.colName")}</th>
                          <th className={adminThClass}>{t("chart.colProductLine")}</th>
                          <th className={adminThClass}>{t("chart.colBatch")}</th>
                          <th className={adminThClass}>{t("chart.colStatus")}</th>
                          <th className={adminThClass}>{t("chart.colSource")}</th>
                          <th className={adminThClass}>{t("content.colActions")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {items.map((chart) => (
                          <tr key={chart.id}>
                            <td className={adminTdClass}>
                              <span className="font-mono text-small font-bold">{chart.code}</span>
                              <span className="block text-small text-text-tertiary">
                                {t("chart.versionLabel", { version: chart.version })}
                              </span>
                            </td>
                            <td className={adminTdClass}>
                              <span className="font-semibold">{chart.name}</span>
                              {chart.placeholder ? (
                                <span className="block text-small text-warning-text">{t("chart.placeholder")}</span>
                              ) : null}
                            </td>
                            <td className={adminTdClass}>
                              <span className="font-mono text-small">{chart.productLine ?? "—"}</span>
                            </td>
                            <td className={adminTdClass}>
                              <span className="font-mono text-small">{chart.productionBatch ?? "—"}</span>
                            </td>
                            <td className={adminTdClass}>
                              <span
                                className={`inline-block rounded-md px-2 py-0.5 text-small font-semibold ${CHART_STATUS_CLASS[chart.status]}`}
                              >
                                {t(`chart.status.${chart.status}`)}
                              </span>
                            </td>
                            <td className={adminTdClass}>
                              <span className="font-mono text-small">{chart.source}</span>
                            </td>
                            <td className={adminTdClass}>
                              <button
                                type="button"
                                className="font-semibold text-primary-dark hover:underline"
                                onClick={() => {
                                  setSelectedChartId(chart.id);
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

            {selectedChartId !== null ? (
              <ChartDetailPanel
                key={selectedChartId}
                chartId={selectedChartId}
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

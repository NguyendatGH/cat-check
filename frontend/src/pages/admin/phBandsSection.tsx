import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Bell, BellOff, Loader2, Pencil, X } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { phTokenStyle, type PhBand } from "@/entities/ph-bands";
import {
  AdminSection,
  ApiErrorNote,
  ReasonConfirmDialog,
  SavedNote,
  useAdminPhBands,
  useUpdatePhBand,
} from "@/features/admin";
import { useGroup2Can } from "./group2Access";
import { bandRange, formatPhShort, scaleBands } from "./phChartModel";

export function useBandRangeText(): (band: PhBand) => string {
  const { t } = useTranslation("admin");
  return (band) => {
    const range = bandRange(band);
    const min = range.min === null ? "" : formatPhShort(range.min);
    const max = range.max === null ? "" : formatPhShort(range.max);
    switch (range.kind) {
      case "none":
        return t("band.range.none");
      case "below":
        return range.maxInclusive ? t("band.range.belowIncl", { max }) : t("band.range.below", { max });
      case "above":
        return range.minInclusive ? t("band.range.aboveIncl", { min }) : t("band.range.above", { min });
      case "between": {
        const from = range.minInclusive ? t("band.range.fromIncl", { min }) : t("band.range.from", { min });
        const to = range.maxInclusive ? t("band.range.toIncl", { max }) : t("band.range.to", { max });
        return `${from} ${to}`;
      }
    }
  };
}

/** Thang pH: mỗi dải là một đoạn màu, độ rộng theo khoảng pH (dải mở hai đầu được cắt bớt cho dễ nhìn). */
export function PhScale({ bands }: { bands: readonly PhBand[] }) {
  const { t } = useTranslation("admin");
  const ordered = scaleBands(bands);
  const bounds = ordered.flatMap((band) => [band.phMin, band.phMax]).filter((v): v is number => v != null);
  if (ordered.length === 0 || bounds.length === 0) {
    return null;
  }
  const lo = Math.min(...bounds) - 0.5;
  const hi = Math.max(...bounds) + 0.5;
  const span = hi - lo;
  const ticks = Array.from(new Set(bounds)).sort((a, b) => a - b);

  return (
    <figure className="flex flex-col gap-1.5" aria-label={t("band.scaleLabel")}>
      <div className="flex h-6 overflow-hidden rounded-xl ring-1 ring-border">
        {ordered.map((band) => {
          const start = band.phMin ?? lo;
          const end = band.phMax ?? hi;
          const width = ((end - start) / span) * 100;
          return (
            <div
              key={band.code}
              className={cn("min-w-0 border-r-2 border-surface last:border-r-0", phTokenStyle(band.colorToken).solid)}
              style={{ width: `${String(width)}%` }}
              title={band.label}
            />
          );
        })}
      </div>
      <div className="relative h-4 font-mono text-[11px] text-text-secondary" aria-hidden="true">
        {ticks.map((tick) => (
          <span
            key={tick}
            className="absolute -translate-x-1/2"
            style={{ left: `${String(((tick - lo) / span) * 100)}%` }}
          >
            {formatPhShort(tick)}
          </span>
        ))}
      </div>
      <figcaption className="text-small text-text-tertiary">{t("band.scaleCaption")}</figcaption>
    </figure>
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
  const [confirming, setConfirming] = useState(false);

  const canSave = label.trim().length > 0;

  return (
    <div className="flex flex-col gap-4 rounded-xl bg-background-alt/70 p-4">
      <div className="flex items-start justify-between gap-3">
        <p className="text-body font-semibold text-text-primary">{t("band.editorTitleLabel", { label: band.label })}</p>
        <Button type="button" variant="tertiary" size="sm" leftIcon={<X size={15} />} onClick={onClose}>
          {t("actions.close")}
        </Button>
      </div>

      <p className="text-small text-text-tertiary">
        {t("band.localeNote", { locale: isEnglish ? t("content.localeEn") : t("content.localeVi") })}
      </p>

      <Input
        label={t("band.fieldLabel")}
        helperText={t("band.fieldLabelHelp")}
        value={label}
        onChange={(event) => {
          setLabel(event.target.value);
        }}
      />
      <Input
        label={t("band.fieldDescription")}
        helperText={t("band.fieldDescriptionHelp")}
        value={description}
        onChange={(event) => {
          setDescription(event.target.value);
        }}
      />

      <label className="flex items-start gap-2 text-caption text-text-primary">
        <input
          type="checkbox"
          checked={triggersAlert}
          onChange={(event) => {
            setTriggersAlert(event.target.checked);
          }}
          className="mt-0.5 size-4"
        />
        <span>
          {t("band.fieldTriggersAlert")}
          <span className="block text-small text-text-tertiary">{t("band.fieldTriggersAlertHelp")}</span>
        </span>
      </label>

      <details className="rounded-lg bg-surface p-3">
        <summary className="cursor-pointer text-small font-semibold text-text-secondary">{t("band.advanced")}</summary>
        <div className="pt-3">
          <Input
            label={t("band.fieldIcon")}
            helperText={t("band.fieldIconHelp")}
            value={iconName}
            onChange={(event) => {
              setIconName(event.target.value);
            }}
          />
        </div>
      </details>

      <p className="text-small text-text-tertiary">{t("band.thresholdLockedPlain")}</p>

      <SavedNote visible={update.isSuccess} />

      <Button
        type="button"
        size="md"
        className="self-start"
        disabled={!canSave}
        onClick={() => {
          update.reset();
          setConfirming(true);
        }}
      >
        {t("band.saveCta")}
      </Button>

      <ReasonConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("band.confirmTitle")}
        target={band.label}
        consequence={t("band.confirmConsequence")}
        confirmLabel={t("band.saveCta")}
        pending={update.isPending}
        error={update.error}
        onConfirm={(reason) => {
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
    </div>
  );
}

function BandCard({ band, canEdit, onEdit }: { band: PhBand; canEdit: boolean; onEdit: () => void }) {
  const { t } = useTranslation("admin");
  const rangeText = useBandRangeText();
  const tokenStyle = phTokenStyle(band.colorToken);
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-3">
      <div className="flex items-start gap-3">
        <span className={cn("mt-1 size-4 shrink-0 rounded-full", tokenStyle.solid)} aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="text-body font-bold text-text-primary">{band.label}</p>
          <p className="text-caption font-semibold text-text-secondary">{rangeText(band)}</p>
        </div>
        {canEdit ? (
          <Button
            type="button"
            variant="tertiary"
            size="sm"
            leftIcon={<Pencil size={14} />}
            aria-label={t("band.editAria", { label: band.label })}
            onClick={onEdit}
          >
            {t("actions.edit")}
          </Button>
        ) : null}
      </div>
      {band.description.trim() !== "" ? <p className="text-small text-text-secondary">{band.description}</p> : null}
      <div className="flex flex-wrap gap-1.5 text-small">
        <span className={cn("rounded-full px-2 py-0.5 font-semibold", tokenStyle.bg, tokenStyle.text)}>
          {t(`band.severity.${band.severity}`, band.severity)}
        </span>
        <span className="inline-flex items-center gap-1 rounded-full bg-chip-bg px-2 py-0.5 text-text-secondary">
          {band.triggersAlert ? <Bell size={12} aria-hidden="true" /> : <BellOff size={12} aria-hidden="true" />}
          {band.triggersAlert ? t("band.alertOn") : t("band.alertOff")}
        </span>
      </div>
    </li>
  );
}

export function BandsSection() {
  const { t } = useTranslation("admin");
  const bands = useAdminPhBands();
  const canEdit = useGroup2Can()("colorChart");
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  const items = [...(bands.data?.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const etag = bands.data?.etag ?? null;
  const selected = items.find((band) => band.code === selectedCode) ?? null;

  return (
    <AdminSection title={t("band.title")} description={t("band.intro")}>
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
        <>
          <PhScale bands={items} />
          <ul className="grid gap-2 md:grid-cols-2">
            {items.map((band) => (
              <BandCard
                key={band.code}
                band={band}
                canEdit={canEdit}
                onEdit={() => {
                  setSelectedCode(band.code);
                }}
              />
            ))}
          </ul>
        </>
      )}

      {selected !== null && canEdit ? (
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

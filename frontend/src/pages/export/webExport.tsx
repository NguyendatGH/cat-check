import { format } from "date-fns";
import { useTranslation } from "react-i18next";
import { Download } from "lucide-react";
import { useFormatCatAge, type Cat } from "@/entities/cat";
import { DisclaimerBanner } from "@/entities/disclaimer";
import {
  CatPickerStep,
  EXPORT_RECOMMENDED_PRESET,
  EXPORT_SECTIONS,
  ExportSectionHeading,
  RangePickerStep,
  ReportPreviewThumbnail,
  SectionsChecklist,
  type ExportRangePreset,
  type ExportSection,
} from "@/features/export";
import { resolveExportRange } from "./rangeLabel";


function Panel({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <div className={`rounded-2xl bg-surface p-5 shadow-xs ${className}`}>{children}</div>;
}

export interface WebExportScreenProps {
  cats: Cat[];
  isPending: boolean;
  isLoadError: boolean;
  onRetryLoad: () => void;

  selectedCatId: string | null;
  selectedCatName: string | null;
  onSelectCat: (cat: Cat) => void;

  rangePreset: ExportRangePreset;
  customFrom: string;
  customTo: string;
  onRangePresetChange: (preset: ExportRangePreset) => void;
  onCustomRangeChange: (from: string, to: string) => void;

  sections: ExportSection[];
  onToggleSection: (section: ExportSection) => void;
  onSelectAllSections: () => void;

  isSubmitting: boolean;
  submitErrorCode: string | undefined;
  hasSubmitError: boolean;
  onSubmit: () => void;
}

export function WebExportScreen({
  cats,
  isPending,
  isLoadError,
  onRetryLoad,
  selectedCatId,
  selectedCatName,
  onSelectCat,
  rangePreset,
  customFrom,
  customTo,
  onRangePresetChange,
  onCustomRangeChange,
  sections,
  onToggleSection,
  onSelectAllSections,
  isSubmitting,
  submitErrorCode,
  hasSubmitError,
  onSubmit,
}: WebExportScreenProps) {
  const { t } = useTranslation(["export", "scan", "common"]);
  const formatAge = useFormatCatAge();

  const header = (
    <div className="max-w-[620px]">
      <p className="text-overline font-bold uppercase tracking-[0.4px] text-primary-dark">{t("web.eyebrow")}</p>
      <h1 className="pt-1 text-[26px] font-bold leading-8 tracking-[-0.5px] text-primary-dark">{t("web.title")}</h1>
      <p className="pt-2 text-caption leading-relaxed text-text-secondary">{t("web.subtitle")}</p>
    </div>
  );

  if (isLoadError) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <Panel className="flex flex-col items-start gap-3">
          <p className="text-body text-text-secondary">{t("state.loadError")}</p>
          <button
            type="button"
            onClick={onRetryLoad}
            className="rounded-xl bg-primary px-4 py-2.5 text-caption font-bold text-white shadow-sm"
          >
            {t("actions.retry", { ns: "common" })}
          </button>
        </Panel>
      </div>
    );
  }

  if (isPending) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <div className="grid grid-cols-12 gap-4">
          <div className="col-span-12 flex flex-col gap-4 xl:col-span-7">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-40 animate-pulse rounded-2xl bg-surface" />
            ))}
          </div>
          <div className="col-span-12 xl:col-span-5">
            <div className="h-80 animate-pulse rounded-2xl bg-surface" />
          </div>
        </div>
      </div>
    );
  }

  const resolvedRange = resolveExportRange(rangePreset, customFrom, customTo);
  const rangeHint = resolvedRange
    ? t("web.rangeValue", {
        from: format(resolvedRange.from, t("web.rangeFromFormat")),
        to: format(resolvedRange.to, t("web.rangeToFormat")),
      })
    : undefined;
  const rangeLabel = t(`range.${rangePreset}`);
  const allSelected = sections.length === EXPORT_SECTIONS.length;
  const canSubmit = Boolean(selectedCatId) && sections.length > 0 && !isSubmitting;

  let submitError: string | null = null;
  if (submitErrorCode === "EXPORT_JOB_IN_PROGRESS") submitError = t("state.conflictDescription");
  else if (submitErrorCode === "EXPORT_NO_DATA") submitError = t("state.noDataDescription");
  else if (submitErrorCode === "EXPORT_RANGE_INVALID") submitError = t("state.rangeInvalidDescription");
  else if (hasSubmitError) submitError = t("state.loadError");

  return (
    <div className="flex flex-col gap-4">
      {header}

      <div className="grid grid-cols-12 gap-4">
        <div className="col-span-12 flex flex-col gap-4 xl:col-span-7">
          <Panel className="flex flex-col gap-3">
            <ExportSectionHeading index="1." title={t("web.step1")} hint={t("web.step1Hint")} />
            {cats.length === 0 ? (
              <div className="rounded-xl bg-background-alt p-5">
                <p className="text-body font-semibold text-text-primary">{t("selectCat.emptyTitle", { ns: "scan" })}</p>
                <p className="pt-1 text-caption text-text-secondary">
                  {t("selectCat.emptyDescription", { ns: "scan" })}
                </p>
              </div>
            ) : (
              <CatPickerStep
                cats={cats}
                selectedCatId={selectedCatId}
                onSelect={onSelectCat}
                primaryLabel={t("wizard.step1.primaryLabel")}
                secondaryLabel={t("wizard.step1.secondaryLabel")}
                ageLabelFor={(cat) => formatAge(cat.ageMonths) ?? undefined}
                className={cats.length > 1 ? "grid grid-cols-2 gap-3" : undefined}
              />
            )}
          </Panel>

          <Panel className="flex flex-col gap-3">
            <ExportSectionHeading index="2." title={t("web.step2")} hint={rangeHint} />
            <RangePickerStep
              value={rangePreset}
              onChange={onRangePresetChange}
              customFrom={customFrom}
              customTo={customTo}
              onCustomChange={onCustomRangeChange}
              labels={{
                "7D": t("range.7D"),
                "30D": t("range.30D"),
                "90D": t("range.90D"),
                CUSTOM: t("range.CUSTOM"),
              }}
              recommended={EXPORT_RECOMMENDED_PRESET}
              recommendedSuffix={t("web.recommendedSuffix")}
              fromLabel={t("wizard.step2.fromLabel")}
              toLabel={t("wizard.step2.toLabel")}
            />
          </Panel>

          <Panel className="flex flex-col gap-1">
            <ExportSectionHeading
              index="3."
              title={t("web.step3")}
              action={
                <button
                  type="button"
                  onClick={onSelectAllSections}
                  disabled={allSelected}
                  className="text-[11px] font-semibold text-primary-dark hover:underline disabled:text-text-tertiary disabled:no-underline"
                >
                  {t("web.selectAll", { count: EXPORT_SECTIONS.length })}
                </button>
              }
            />
            <SectionsChecklist
              items={EXPORT_SECTIONS.map((section) => ({
                value: section,
                title: t(`sections.${section}.title`),
                description: t(`sections.${section}.description`),
              }))}
              selected={sections}
              onToggle={onToggleSection}
            />
            {sections.length === 0 ? (
              <p role="alert" className="text-caption text-danger-text">
                {t("state.sectionsRequired")}
              </p>
            ) : null}
          </Panel>
        </div>

        <div className="col-span-12 xl:col-span-5">
          <div className="flex flex-col gap-4 xl:sticky xl:top-20">
            <Panel className="flex flex-col gap-3">
              <ExportSectionHeading title={t("web.previewTitle")} hint={t("web.previewFormat")} />

              <div className="rounded-xl bg-background-alt p-4">
                <ReportPreviewThumbnail watermark={t("web.previewWatermark")} />
              </div>

              <div>
                <p className="text-body font-bold leading-snug text-text-primary">{t("web.reportTitle")}</p>
                <p className="pt-1.5 text-caption leading-relaxed text-text-secondary">
                  {selectedCatName
                    ? t("web.reportDescriptionNamed", {
                        name: selectedCatName,
                        range: rangeLabel.toLowerCase(),
                        count: sections.length,
                      })
                    : t("web.reportDescription", {
                        range: rangeLabel.toLowerCase(),
                        count: sections.length,
                      })}
                </p>
              </div>
            </Panel>

            <DisclaimerBanner variant="medium" />

            <Panel className="flex flex-col gap-3">
              {submitError ? (
                <p role="alert" className="text-caption text-danger-text">
                  {submitError}
                </p>
              ) : null}
              <button
                type="button"
                onClick={onSubmit}
                disabled={!canSubmit}
                className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-5 py-3 text-caption font-bold text-white shadow-sm disabled:opacity-60"
              >
                <Download className="size-4" aria-hidden="true" />
                {isSubmitting ? t("wizard.submitting") : t("web.submit")}
              </button>
              {!selectedCatId && cats.length > 0 ? (
                <p className="text-[11px] text-text-tertiary">{t("web.submitHint")}</p>
              ) : null}
            </Panel>
          </div>
        </div>
      </div>
    </div>
  );
}

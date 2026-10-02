import type { ReactNode } from "react";
import { format } from "date-fns";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Download } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { isApiError } from "@/shared/api/errors";
import { DisclaimerBanner } from "@/entities/disclaimer";
import {
  CatPickerStep,
  EXPORT_RECOMMENDED_PRESET,
  EXPORT_SECTIONS,
  ExportSectionHeading,
  RangePickerStep,
  ReportPreviewThumbnail,
  SectionsChecklist,
  useActiveCatsForExport,
  useExportWizardStore,
  useRequestExport,
} from "@/features/export";
import { WebExportScreen } from "./webExport";
import { resolveExportRange } from "./rangeLabel";

/**
 * `/export` — tạo job xuất PDF (p8 §8.4.10 J1).
 *
 * Mockup `10. Xuất Hồ sơ Sức khỏe` là MỘT TRANG CUỘN: cả ba mục cấu hình (chọn mèo → khoảng
 * thời gian → mục nội dung) cộng khối xem trước và CTA hiện cùng lúc, KHÔNG phải wizard từng
 * bước. Bản mobile ở đây dựng đúng như vậy; `store.step` vẫn còn trong store nhưng không còn
 * điều khiển bố cục.
 */
export function ExportPage() {
  const { t } = useTranslation(["export", "common", "scan"]);
  const navigate = useNavigate();
  const { data: cats, isPending, isError, refetch } = useActiveCatsForExport();
  const wizard = useExportWizardStore();
  const requestExport = useRequestExport();

  const handleSubmit = () => {
    if (!wizard.catId) return;
    requestExport.mutate(
      {
        catId: wizard.catId,
        rangeFrom: wizard.rangePreset === "CUSTOM" ? wizard.customFrom : null,
        rangeTo: wizard.rangePreset === "CUSTOM" ? wizard.customTo : null,
        rangePreset: wizard.rangePreset,
        sections: wizard.sections,
      },
      {
        onSuccess: (job) => {
          wizard.reset();
          void navigate(`/export/${job.jobId}`, { replace: true });
        },
      },
    );
  };

  /** Store chỉ có `toggleSection` — "Chọn tất cả" bật nốt các mục còn thiếu. */
  const handleSelectAllSections = () => {
    EXPORT_SECTIONS.filter((section) => !wizard.sections.includes(section)).forEach((section) => {
      wizard.toggleSection(section);
    });
  };

  const requestErrorCode = isApiError(requestExport.error) ? requestExport.error.code : undefined;

  const resolvedRange = resolveExportRange(wizard.rangePreset, wizard.customFrom, wizard.customTo);
  const rangeHint = resolvedRange
    ? t("web.rangeValue", {
        from: format(resolvedRange.from, t("web.rangeFromFormat")),
        to: format(resolvedRange.to, t("web.rangeToFormat")),
      })
    : undefined;
  const rangeLabel = t(`range.${wizard.rangePreset}`);
  const allSelected = wizard.sections.length === EXPORT_SECTIONS.length;
  const canSubmit = Boolean(wizard.catId) && wizard.sections.length > 0 && !requestExport.isPending;

  let submitError: string | null = null;
  if (requestErrorCode === "EXPORT_JOB_IN_PROGRESS") submitError = t("state.conflictDescription");
  else if (requestErrorCode === "EXPORT_NO_DATA") submitError = t("state.noDataDescription");
  else if (requestErrorCode === "EXPORT_RANGE_INVALID") submitError = t("state.rangeInvalidDescription");
  else if (requestExport.isError) submitError = t("state.loadError");

  let mobileContent: ReactNode;
  if (isPending) {
    mobileContent = (
      <div className="flex flex-col gap-3">
        <SkeletonLoader shape="card" className="h-20" />
        <SkeletonLoader shape="card" className="h-32" />
        <SkeletonLoader shape="card" className="h-48" />
      </div>
    );
  } else if (isError) {
    mobileContent = (
      <ErrorState
        title={t("state.loadError")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("actions.retry", { ns: "common" })}
      />
    );
  } else {
    mobileContent = (
      <div className="flex flex-col gap-5 pb-28">
        {/* 1 — chọn bé mèo */}
        <section className="flex flex-col gap-3">
          <ExportSectionHeading index="1." title={t("web.step1")} hint={t("web.step1Hint")} />
          {cats.length === 0 ? (
            <EmptyState
              title={t("selectCat.emptyTitle", { ns: "scan" })}
              description={t("selectCat.emptyDescription", { ns: "scan" })}
            />
          ) : (
            <CatPickerStep
              cats={cats}
              selectedCatId={wizard.catId}
              onSelect={(cat) => {
                wizard.setCat({ id: cat.id, name: cat.name });
              }}
              primaryLabel={t("wizard.step1.primaryLabel")}
              secondaryLabel={t("wizard.step1.secondaryLabel")}
              className="grid grid-cols-2 gap-3"
            />
          )}
        </section>

        {/* 2 — giai đoạn theo dõi */}
        <section className="flex flex-col gap-3">
          <ExportSectionHeading index="2." title={t("web.step2")} hint={rangeHint} />
          <RangePickerStep
            value={wizard.rangePreset}
            onChange={wizard.setRangePreset}
            customFrom={wizard.customFrom}
            customTo={wizard.customTo}
            onCustomChange={wizard.setCustomRange}
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
        </section>

        {/* 3 — mục nội dung */}
        <section className="flex flex-col gap-1">
          <ExportSectionHeading
            index="3."
            title={t("web.step3")}
            action={
              <button
                type="button"
                onClick={handleSelectAllSections}
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
            selected={wizard.sections}
            onToggle={wizard.toggleSection}
          />
          {wizard.sections.length === 0 ? (
            <p role="alert" className="text-caption text-danger-text">
              {t("state.sectionsRequired")}
            </p>
          ) : null}
        </section>

        {/* Xem trước báo cáo */}
        <section className="flex flex-col gap-3">
          <ExportSectionHeading title={t("web.previewTitle")} hint={t("web.previewFormat")} />
          <div className="rounded-2xl bg-background-alt p-4">
            <ReportPreviewThumbnail watermark={t("web.previewWatermark")} />
          </div>
          <div>
            <p className="text-body font-bold leading-snug text-text-primary">{t("web.reportTitle")}</p>
            <p className="pt-1.5 text-caption leading-relaxed text-text-secondary">
              {wizard.catName
                ? t("web.reportDescriptionNamed", {
                    name: wizard.catName,
                    range: rangeLabel.toLowerCase(),
                    count: wizard.sections.length,
                  })
                : t("web.reportDescription", {
                    range: rangeLabel.toLowerCase(),
                    count: wizard.sections.length,
                  })}
            </p>
          </div>
        </section>

        <DisclaimerBanner variant="medium" />

        <div className="fixed inset-x-0 bottom-0 flex flex-col gap-2 border-t border-border bg-surface p-4">
          {submitError ? (
            <p role="alert" className="text-caption text-danger-text">
              {submitError}
            </p>
          ) : null}
          <Button
            variant="primary"
            className="w-full"
            leftIcon={<Download className="size-4" />}
            loading={requestExport.isPending}
            disabled={!canSubmit}
            onClick={handleSubmit}
          >
            {requestExport.isPending ? t("wizard.submitting") : t("web.submit")}
          </Button>
          {!wizard.catId && cats.length > 0 ? (
            <p className="text-center text-[11px] text-text-tertiary">{t("web.submitHint")}</p>
          ) : null}
        </div>
      </div>
    );
  }

  return (
    <>
      {/* Desktop (>= lg): cùng nội dung, bố cục 2 cột theo mockup `10`. */}
      <div className="hidden lg:block">
        <WebExportScreen
          cats={cats ?? []}
          isPending={isPending}
          isLoadError={isError}
          onRetryLoad={() => {
            void refetch();
          }}
          selectedCatId={wizard.catId}
          selectedCatName={wizard.catName}
          onSelectCat={(cat) => {
            wizard.setCat({ id: cat.id, name: cat.name });
          }}
          rangePreset={wizard.rangePreset}
          customFrom={wizard.customFrom}
          customTo={wizard.customTo}
          onRangePresetChange={wizard.setRangePreset}
          onCustomRangeChange={wizard.setCustomRange}
          sections={wizard.sections}
          onToggleSection={wizard.toggleSection}
          onSelectAllSections={handleSelectAllSections}
          isSubmitting={requestExport.isPending}
          submitErrorCode={requestErrorCode}
          hasSubmitError={requestExport.isError}
          onSubmit={handleSubmit}
        />
      </div>
      {/* Mobile (< lg): một trang cuộn đúng mockup `10`. */}
      <div className="lg:hidden">{mobileContent}</div>
    </>
  );
}

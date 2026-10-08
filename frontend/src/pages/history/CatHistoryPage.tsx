import { useCallback, useMemo, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { Camera, LineChart } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { CatAvatar, useFormatCatAge } from "@/entities/cat";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { findBandForPh, PhGaugeBar, usePhBands } from "@/entities/ph-bands";
import { displayableScanCount } from "@/entities/scan-result";
import type { ScanListItem } from "@/entities/scan-result";
import { useExportWizardStore } from "@/features/export";
import {
  CatSwitcherBar,
  ExportCtaCard,
  FilterChips,
  HistoryTipCard,
  MonthGroupHeader,
  PhBandLegendRow,
  ScanTimelineItem,
  formatScanTimestamp,
  monthGroupKey,
  parseMonthGroupKey,
  useCat,
  useScanHistory,
  useScanSummary,
  type HistoryFilterKey,
} from "@/features/history";
import tipScanFrequency from "@/shared/assets/images/web-history/tip-scan-frequency.jpg";
import { WebHistoryScreen } from "./webHistory";

/** `/cats/:catId/history` — timeline lịch sử quét theo mèo (p8 §8.5.4 E2/E3, mockup `07`
 * cho mobile, `Web - 06 & 07` cho desktop). */
export function CatHistoryPage() {
  const { t } = useTranslation(["history", "cat", "scan", "common"]);
  const { catId } = useParams<{ catId: string }>();
  const navigate = useNavigate();
  const [filter, setFilter] = useState<HistoryFilterKey>("ALL");

  const { data: cat } = useCat(catId);
  const { data: bands } = usePhBands();
  const { data: summary } = useScanSummary(catId);
  const formatAge = useFormatCatAge();
  const {
    data: history,
    isPending,
    isError,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useScanHistory(catId, filter);

  const items: ScanListItem[] = useMemo(() => history?.pages.flatMap((p) => p.items) ?? [], [history]);
  const groups = useMemo(() => {
    const map = new Map<string, ScanListItem[]>();
    items.forEach((item) => {
      const key = monthGroupKey(item.capturedAt);
      const list = map.get(key) ?? [];
      list.push(item);
      map.set(key, list);
    });
    return [...map.entries()];
  }, [items]);

  const monthLabel = useCallback(
    (key: string) => {
      const { month, year } = parseMonthGroupKey(key);
      return t("monthGroup.label", { month, year });
    },
    [t],
  );
  const timestampLabel = useCallback(
    (iso: string) => formatScanTimestamp(iso, t("timestamp.today"), t("timestamp.yesterday")),
    [t],
  );

  const setExportCat = useExportWizardStore((s) => s.setCat);
  /** Xuất hồ sơ PDF với bé đang xem đã được chọn sẵn ở bước 1. */
  const openExport = () => {
    if (cat) setExportCat({ id: cat.id, name: cat.name });
    void navigate("/export");
  };

  // Dải của trung vị 30 ngày — dùng cho dòng trạng thái ở thanh chuyển bé và nhãn cạnh
  // "Trung bình". Tra từ `bands` (API), không hard-code ngưỡng.
  const medianBand = summary?.median != null ? findBandForPh(bands ?? [], summary.median) : null;
  const catMeta = [
    formatAge(cat?.ageMonths),
    cat
      ? t(`form.sex.${cat.sex === "MALE" ? "male" : cat.sex === "FEMALE" ? "female" : "unknown"}`, { ns: "cat" })
      : null,
  ]
    .filter(Boolean)
    .join(" • ");

  let mobileContent: ReactNode;
  if (isPending) {
    mobileContent = (
      <div className="flex flex-col gap-3 p-4">
        <SkeletonLoader shape="card" className="h-24" />
        <SkeletonLoader shape="card" className="h-16" />
        <SkeletonLoader shape="card" className="h-16" />
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
      <div className="flex flex-col gap-4 p-4">
        {/* Thanh "đang xem bé nào + đổi bé" (mockup `07` đầu màn) */}
        <CatSwitcherBar
          name={cat?.name ?? t("pages.catHistory.title")}
          avatar={<CatAvatar src={cat?.avatarUrl} name={cat?.name ?? "?"} size="md" />}
          meta={catMeta || null}
          status={medianBand ? t("switcher.statusWithBand", { label: medianBand.label }) : t("switcher.statusNone")}
          statusTone={medianBand?.severity === "NORMAL" ? "normal" : "muted"}
          changeLabel={t("switcher.changeCat")}
          ariaLabel={t("switcher.ariaLabel")}
          onChange={() => {
            void navigate("/cats");
          }}
        />

        <DisclaimerBanner variant="short" />

        {summary && summary.count > 0 ? (
          <div className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-4">
            {/* Tiêu đề và chip trung bình tự xuống hàng khi chật — trước đây chip `shrink-0` ép
                "Phổ pH gần đây" vỡ thành bốn dòng ở 390px. */}
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="flex items-center gap-1.5 text-body font-bold text-text-primary">
                <LineChart className="size-4 shrink-0 text-primary" aria-hidden="true" />
                {t("spectrum.title")}
              </p>
              {summary.median !== null ? (
                <span className="rounded-full bg-chip-bg px-2.5 py-1 text-small font-semibold text-primary-dark">
                  {medianBand
                    ? t("spectrum.averageWithBand", { value: summary.median.toFixed(1), label: medianBand.label })
                    : t("spectrum.average", { value: summary.median.toFixed(1) })}
                </span>
              ) : null}
            </div>
            <PhGaugeBar bands={bands ?? []} value={summary.median} />
            <PhBandLegendRow bands={bands ?? []} />
          </div>
        ) : null}

        <FilterChips
          value={filter}
          onChange={setFilter}
          labels={{
            ALL: summary ? t("filters.allWithCount", { count: displayableScanCount(summary) }) : t("filters.ALL"),
            ABNORMAL: t("filters.ABNORMAL"),
            DISPUTED: t("filters.DISPUTED"),
          }}
        />

        {items.length === 0 ? (
          <EmptyState
            icon={<Camera className="size-6" aria-hidden="true" />}
            title={t("state.emptyTitle")}
            description={t("state.emptyDescription")}
            action={
              <Button
                variant="primary"
                onClick={() => {
                  void navigate("/scan/select-cat");
                }}
              >
                {t("state.emptyCta")}
              </Button>
            }
          />
        ) : (
          <div className="flex flex-col gap-4">
            {groups.map(([key, group]) => {
              const { month, year } = parseMonthGroupKey(key);
              return (
                <div key={key} className="flex flex-col gap-2">
                  <MonthGroupHeader
                    label={t("monthGroup.label", { month, year })}
                    count={t("monthGroup.count", { count: group.length })}
                  />
                  {group.map((scan) => (
                    <ScanTimelineItem
                      key={scan.scanId}
                      scan={scan}
                      bands={bands ?? []}
                      timestampLabel={timestampLabel(scan.capturedAt)}
                      disputedLabel={t("item.disputedBadge")}
                      detailLabel={t("item.detailLink")}
                      onOpen={(scanId) => {
                        void navigate(`/scans/${scanId}`);
                      }}
                    />
                  ))}
                </div>
              );
            })}
            {hasNextPage ? (
              <Button
                variant="tertiary"
                loading={isFetchingNextPage}
                onClick={() => {
                  void fetchNextPage();
                }}
              >
                {t("state.loadMore")}
              </Button>
            ) : null}
          </div>
        )}

        <HistoryTipCard title={t("tip.title")} body={t("tip.body")} imageSrc={tipScanFrequency} />

        <ExportCtaCard
          title={t("export.title")}
          description={t("export.description")}
          ctaLabel={t("export.cta")}
          onExport={openExport}
        />
      </div>
    );
  }

  return (
    <>
      {/* Desktop (>= lg): bố cục Figma `Web - 06 & 07`, panel dòng thời gian y tế. */}
      <div className="hidden lg:block">
        <WebHistoryScreen
          catName={cat?.name ?? null}
          catAvatarUrl={cat?.avatarUrl ?? null}
          catBreedName={cat?.breedName ?? null}
          bands={bands ?? []}
          summary={summary}
          groups={groups}
          totalLoaded={items.length}
          totalRecords={summary ? displayableScanCount(summary) : null}
          filter={filter}
          onFilterChange={setFilter}
          isPending={isPending}
          isError={isError}
          onRetry={() => {
            void refetch();
          }}
          hasNextPage={hasNextPage}
          isFetchingNextPage={isFetchingNextPage}
          onLoadMore={() => {
            void fetchNextPage();
          }}
          onOpenScan={(scanId) => {
            void navigate(`/scans/${scanId}`);
          }}
          onStartScan={() => {
            void navigate("/scan/select-cat");
          }}
          onExport={openExport}
          onOpenTrends={() => {
            void navigate(`/cats/${catId ?? ""}/trends`);
          }}
          monthLabel={monthLabel}
          timestampLabel={timestampLabel}
        />
      </div>
      {/* Mobile (< lg): giữ nguyên luồng cũ, kể cả trạng thái loading/lỗi. */}
      <div className="lg:hidden">{mobileContent}</div>
    </>
  );
}

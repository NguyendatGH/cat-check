import { useCallback, useMemo, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { Camera } from "lucide-react";
import { Skeleton as AntSkeleton } from "antd";
import { Button, EmptyState, ErrorState } from "@/shared/ui";
import { CatAvatar, useFormatCatAge } from "@/entities/cat";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { findBandForPh, usePhBands } from "@/entities/ph-bands";
import { displayableScanCount } from "@/entities/scan-result";
import type { ScanListItem } from "@/entities/scan-result";
import { useExportWizardStore } from "@/features/export";
import {
  ActivityChart,
  CatSwitcherBar,
  dayKey,
  ExportCtaCard,
  FilterChips,
  HistoryTipCard,
  MonthGroupHeader,
  HistoryToolbar,
  ScanTimelineItem,
  formatScanTimestamp,
  monthGroupKey,
  parseMonthGroupKey,
  useCat,
  useScanHistory,
  useScanSummary,
  type HistoryFilterKey,
  type HistoryRangeKey,
} from "@/features/history";
import { HistorySpectrumCard } from "./HistorySpectrumCard";
import tipScanFrequency from "@/shared/assets/images/web-history/tip-scan-frequency.jpg";
import { WebHistoryScreen } from "./webHistory";

export function CatHistoryPage() {
  const { t, i18n } = useTranslation(["history", "cat", "scan", "common"]);
  const { catId } = useParams<{ catId: string }>();
  const navigate = useNavigate();
  const [filter, setFilter] = useState<HistoryFilterKey>("ALL");
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [range, setRange] = useState<HistoryRangeKey>("3");
  const [search, setSearch] = useState("");

  const { data: cat } = useCat(catId);
  const { data: bands } = usePhBands();
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
  const rangeStart = useMemo(() => {
    if (range === "all") return null;
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    start.setDate(start.getDate() - (Number(range) - 1));
    return start;
  }, [range]);

  // Summary KPIs follow the same window as the list: the selected day, otherwise the range.
  const summaryWindow = useMemo<{ from?: string; to?: string }>(() => {
    if (selectedDay) {
      const [y, m, d] = selectedDay.split("-").map(Number);
      const start = new Date(y, m - 1, d);
      const end = new Date(y, m - 1, d, 23, 59, 59, 999);
      return { from: start.toISOString(), to: end.toISOString() };
    }
    return rangeStart ? { from: rangeStart.toISOString() } : {};
  }, [selectedDay, rangeStart]);
  const { data: summary } = useScanSummary(catId, summaryWindow.from, summaryWindow.to);

  const visibleItems = useMemo(() => {
    const query = search.trim().toLowerCase();
    const bandLabel = new Map((bands ?? []).map((b) => [b.code, b.label.toLowerCase()]));
    return items.filter((item) => {
      const captured = new Date(item.capturedAt);
      if (selectedDay) {
        if (dayKey(captured) !== selectedDay) return false;
      } else if (rangeStart && captured < rangeStart) {
        return false;
      }
      if (!query) return true;
      const haystack = [
        item.phValue?.toFixed(1),
        bandLabel.get(item.bandCode),
        item.catName?.toLowerCase(),
        captured.toLocaleDateString(i18n.language || "vi"),
      ];
      return haystack.some((value) => value?.includes(query));
    });
  }, [items, bands, selectedDay, rangeStart, search, i18n.language]);

  const oldestLoaded = items.at(-1);
  const canLoadMore =
    hasNextPage &&
    !selectedDay &&
    (rangeStart === null || (oldestLoaded !== undefined && new Date(oldestLoaded.capturedAt) >= rangeStart));

  const resetFilters = () => {
    setSelectedDay(null);
    setSearch("");
    setRange("all");
  };
  const groups = useMemo(() => {
    const map = new Map<string, ScanListItem[]>();
    visibleItems.forEach((item) => {
      const key = monthGroupKey(item.capturedAt);
      const list = map.get(key) ?? [];
      list.push(item);
      map.set(key, list);
    });
    return [...map.entries()];
  }, [visibleItems]);

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
  const openExport = () => {
    if (cat) setExportCat({ id: cat.id, name: cat.name });
    void navigate("/export");
  };

  const medianBand = summary?.median != null ? findBandForPh(bands ?? [], summary.median) : null;
  const catMeta = [
    formatAge(cat?.ageMonths),
    cat
      ? t(`form.sex.${cat.sex === "MALE" ? "male" : cat.sex === "FEMALE" ? "female" : "unknown"}`, { ns: "cat" })
      : null,
  ]
    .filter(Boolean)
    .join(" • ");

  const toolbar = (
    <HistoryToolbar
      range={range}
      onRangeChange={setRange}
      search={search}
      onSearchChange={setSearch}
      selectedDay={selectedDay}
      onSelectDay={setSelectedDay}
    />
  );

  const filteredEmpty = (
    <div className="content-enter flex flex-col items-center gap-2 rounded-2xl bg-surface px-4 py-8 text-center shadow-xs">
      <p className="text-body font-semibold text-text-primary">{t("state.filteredEmptyTitle")}</p>
      <p className="text-small text-text-secondary">{t("state.filteredEmptyBody")}</p>
      <Button variant="tertiary" onClick={resetFilters}>
        {t("state.showAll")}
      </Button>
    </div>
  );

  let mobileContent: ReactNode;
  if (isPending) {
    mobileContent = (
      <div className="flex flex-col gap-3 p-4">
        <div className="rounded-2xl bg-surface p-4" aria-hidden="true">
          <AntSkeleton active paragraph={{ rows: 2 }} />
        </div>
        {[0, 1].map((i) => (
          <div key={i} className="rounded-2xl bg-surface p-4" aria-hidden="true">
            <AntSkeleton active title={false} paragraph={{ rows: 2 }} />
          </div>
        ))}
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
          <HistorySpectrumCard bands={bands ?? []} median={summary.median} scanCount={displayableScanCount(summary)} />
        ) : null}

        {items.length > 0 ? (
          <div className="rounded-xl bg-surface p-4 shadow-xs">
            <ActivityChart items={items} selectedDay={selectedDay} onSelectDay={setSelectedDay} />
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

        {items.length > 0 ? toolbar : null}

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
        ) : visibleItems.length === 0 ? (
          filteredEmpty
        ) : (
          <div key={`${range}-${selectedDay ?? ""}`} className="content-enter flex flex-col gap-4">
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
            {canLoadMore ? (
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
    <div className="contents">
      <div data-reveal className="hidden lg:block">
        <WebHistoryScreen
          catName={cat?.name ?? null}
          catAvatarUrl={cat?.avatarUrl ?? null}
          catBreedName={cat?.breedName ?? null}
          bands={bands ?? []}
          summary={summary}
          groups={groups}
          activityItems={items}
          selectedDay={selectedDay}
          onSelectDay={setSelectedDay}
          toolbar={toolbar}
          filteredEmpty={items.length > 0 && visibleItems.length === 0 ? filteredEmpty : null}
          totalLoaded={items.length}
          totalRecords={summary ? displayableScanCount(summary) : null}
          filter={filter}
          onFilterChange={setFilter}
          isPending={isPending}
          isError={isError}
          onRetry={() => {
            void refetch();
          }}
          hasNextPage={canLoadMore}
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
      <div data-reveal className="lg:hidden">{mobileContent}</div>
    </div>
  );
}

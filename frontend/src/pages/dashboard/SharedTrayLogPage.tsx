import { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { useInfiniteQuery } from "@tanstack/react-query";
import { Users } from "lucide-react";
import { Button, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { usePhBands } from "@/entities/ph-bands";
import type { ScanListItem, ScanListPage } from "@/entities/scan-result";
import {
  MonthGroupHeader,
  ScanTimelineItem,
  formatScanTimestamp,
  historyApiFetch,
  monthGroupKey,
  parseMonthGroupKey,
} from "@/features/history";

/**
 * `/shared-tray-log` — "Nhật ký khay chung".
 *
 * ĐÃ CÓ API, KHÔNG PHẢI MÀN RỖNG. p8 §8.5.4 ghi rõ màn này **không có endpoint riêng** —
 * nó là `GET /scans?assignment=SHARED_UNKNOWN` (E2). Backend đã hiện thực bộ lọc đó
 * (`ScanController#listScans` nhận `assignment`, `JdbcScanQueryRepository` ghép
 * `AND s.assignment = ?`), nên trang gọi thẳng E2 qua `historyApiFetch` thay vì dựng dữ
 * liệu mẫu. `useScanHistory` của `features/history` chưa nhận tham số `assignment` nên
 * không dùng lại được — query dựng tại chỗ, cùng page size và cùng cách phân trang con trỏ.
 *
 * NGHIỆP VỤ (p6 §6.10.4): khi người dùng chọn "Chưa rõ / Dùng chung khay" lúc quét thì
 * `scan.cat_id = NULL`, `assignment_mode = 'SHARED_UNKNOWN'`; kết quả vẫn lưu và vẫn trừ
 * credit, nhưng KHÔNG vào lịch sử của bé nào, KHÔNG tính baseline, KHÔNG kích hoạt rule.
 * Vẫn gán lại được cho một bé cụ thể trong cửa sổ thời gian của §6.10.3 — nút "Gán cho một
 * bé" đưa sang `/scan/:scanId/reassign-cat`, nơi đã có sẵn kiểm tra hạn và số lần.
 * Con số giờ của cửa sổ đó thuộc p6 nên không chép lại ở đây (quy tắc cứng CLAUDE.md).
 *
 * Trang nằm trong `AppLayout`. Layout cấp hộp 944px + padding ở `lg`, nhưng KHÔNG cấp
 * padding ngang ở mobile — theo đúng idiom `SettingsPage`/`ShopPage`: `px-4 py-5 lg:px-0`.
 */

const PAGE_SIZE = 20;

export function SharedTrayLogPage() {
  const { t } = useTranslation(["history", "common"]);
  const navigate = useNavigate();
  const { data: bands } = usePhBands();

  const { data, isPending, isError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ["history", "shared-tray"],
    queryFn: ({ pageParam }: { pageParam: string | null }) => {
      const qs = new URLSearchParams({ assignment: "SHARED_UNKNOWN", limit: String(PAGE_SIZE) });
      if (pageParam) qs.set("cursor", pageParam);
      return historyApiFetch<ScanListPage>(`/scans?${qs.toString()}`);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage: ScanListPage) => (lastPage.hasMore ? lastPage.nextCursor : undefined),
  });

  const items: ScanListItem[] = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);

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

  let list;
  if (isPending) {
    list = (
      <div className="flex flex-col gap-3">
        <SkeletonLoader shape="card" className="h-16" />
        <SkeletonLoader shape="card" className="h-16" />
        <SkeletonLoader shape="card" className="h-16" />
      </div>
    );
  } else if (isError) {
    list = (
      <ErrorState
        title={t("sharedTray.loadError")}
        onRetry={() => {
          void refetch();
        }}
        retryLabel={t("common:actions.retry")}
      />
    );
  } else if (items.length === 0) {
    // p9 §9.4.6: empty state của màn này KHÔNG có CTA — chọn "dùng chung khay" là lựa chọn
    // trong luồng quét, không phải việc cần thúc người dùng làm.
    list = (
      <EmptyState
        icon={<Users size={22} aria-hidden="true" />}
        title={t("sharedTray.emptyTitle")}
        description={t("sharedTray.emptyDescription")}
      />
    );
  } else {
    list = (
      <div className="flex flex-col gap-4">
        {groups.map(([key, scans]) => (
          <section key={key} className="flex flex-col gap-2">
            <MonthGroupHeader label={monthLabel(key)} count={t("sharedTray.countLabel", { count: scans.length })} />
            <ul className="flex flex-col gap-3">
              {scans.map((scan) => (
                <li key={scan.scanId} className="flex flex-col gap-1.5">
                  <ScanTimelineItem
                    scan={scan}
                    bands={bands ?? []}
                    timestampLabel={formatScanTimestamp(
                      scan.capturedAt,
                      t("timestamp.today"),
                      t("timestamp.yesterday"),
                    )}
                    disputedLabel={t("item.disputedBadge")}
                    detailLabel={t("item.detailLink")}
                    onOpen={(scanId) => {
                      void navigate(`/scans/${scanId}`);
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => {
                      void navigate(`/scan/${scan.scanId}/reassign-cat`);
                    }}
                    className="self-end rounded-md px-1 text-caption font-semibold text-primary hover:underline"
                  >
                    {t("sharedTray.assignCta")}
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}

        {hasNextPage ? (
          <Button
            type="button"
            variant="tertiary"
            size="md"
            loading={isFetchingNextPage}
            onClick={() => {
              void fetchNextPage();
            }}
          >
            {t("state.loadMore")}
          </Button>
        ) : null}

        {/* p15 P5 / p9 §9.4.8: D-SHORT ở cuối danh sách. */}
        <DisclaimerBanner variant="short" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5 px-4 py-5 lg:px-0">
      <header>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("common:pages.sharedTrayLog.title")}</h1>
        <p className="pt-1 text-body text-text-secondary">{t("sharedTray.lead")}</p>
      </header>

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">{list}</div>

        <aside className="flex w-full flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md lg:w-[360px] lg:shrink-0">
          <h2 className="flex items-center gap-2 text-h3 font-bold text-text-primary">
            <Users size={18} className="text-primary-dark" aria-hidden="true" />
            {t("sharedTray.bannerTitle")}
          </h2>
          <p className="text-caption leading-relaxed text-text-secondary">{t("sharedTray.bannerBody")}</p>
          <p className="text-caption leading-relaxed text-text-tertiary">{t("sharedTray.assignNote")}</p>
        </aside>
      </div>
    </div>
  );
}

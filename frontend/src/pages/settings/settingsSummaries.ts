import { useQueries } from "@tanstack/react-query";
import { resolveCatLastScan, type Cat, type CatLastScan } from "@/entities/cat";
import { fetchScanHistory, historyKeys } from "@/features/history";
import { useReminders } from "@/features/reminder";

/**
 * Dữ liệu tóm tắt cho trang Cài đặt — thay cho `mockData.ts` cũ (danh sách "ngưỡng cảnh báo"
 * đặt cứng, đã xoá vì không có endpoint nào lưu ngưỡng: B11/B12 chỉ có tám field ở
 * `NotificationPreferencesForm`).
 *
 * Mọi giá trị ở đây đọc từ API thật, không có hằng số nghiệp vụ nào.
 */

/**
 * Lần quét gần nhất của từng bé — ngày/dải từ `GET /cats` (`lastScanAt`, `lastClassification`);
 * số pH chỉ có ở `GET /scans?catId=…&limit=1` (E2) nên chỉ bé đã có lần quét mới gọi thêm.
 * Khoá cache dùng chung với `/cats` (`CatsListPage`), nên hai màn không gọi lặp.
 *
 * Giá trị: `null` = bé chưa có lần quét nào.
 */
export function useLastScanByCat(cats: Cat[]): Record<string, CatLastScan | null | undefined> {
  const results = useQueries({
    queries: cats.map((cat) => ({
      enabled: cat.lastScanAt != null,
      queryKey: [...historyKeys.list(cat.id, "ALL" as const), "latest"],
      queryFn: () => fetchScanHistory({ catId: cat.id, filter: "ALL" as const, limit: 1 }),
      staleTime: 30_000,
    })),
  });

  const map: Record<string, CatLastScan | null | undefined> = {};
  cats.forEach((cat, index) => {
    map[cat.id] = resolveCatLastScan(cat, results[index]?.data?.items.at(0));
  });
  return map;
}

export interface ScanReminderSummary {
  status: "loading" | "unavailable" | "ready";
  /** Số lịch nhắc quét (`SCAN_ROUTINE`) đang bật. */
  activeCount: number;
  /** Mốc nhắc sớm nhất trong các lịch đang bật (ISO-8601), nếu server trả. */
  nextRunAt: string | null;
}

/**
 * Tóm tắt lịch nhắc quét — `GET /reminders?active=true` (I1).
 *
 * Chỉ đếm `SCAN_ROUTINE`: `CREDIT_EXPIRY`/`SURVEY_FOLLOWUP` là lịch hệ thống sinh, không phải
 * "lịch nhắc quét" người dùng đặt. Lỗi (kể cả gói chưa mở tính năng) ⇒ `unavailable`, UI rơi về
 * câu mô tả chung chứ không hiện số.
 */
export function useScanReminderSummary(): ScanReminderSummary {
  const { data, isPending, isError } = useReminders({ active: true });

  if (isPending) return { status: "loading", activeCount: 0, nextRunAt: null };
  if (isError) return { status: "unavailable", activeCount: 0, nextRunAt: null };

  const routines = data.items.filter((item) => item.type === "SCAN_ROUTINE" && item.active);
  const nextRunAt =
    routines
      .map((item) => item.nextRunAt)
      .filter((value): value is string => typeof value === "string")
      .sort()
      .at(0) ?? null;

  return { status: "ready", activeCount: routines.length, nextRunAt };
}

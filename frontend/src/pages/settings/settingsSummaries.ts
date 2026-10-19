import { useQueries } from "@tanstack/react-query";
import { resolveCatLastScan, type Cat, type CatLastScan } from "@/entities/cat";
import { fetchScanHistory, historyKeys } from "@/features/history";
import { useReminders } from "@/features/reminder";


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
  activeCount: number;
  nextRunAt: string | null;
}

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

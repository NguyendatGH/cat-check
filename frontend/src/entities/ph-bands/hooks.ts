import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { phBandsApiFetch } from "./api";
import type { ActiveColorChart, PhBand } from "./model";

/**
 * Query key factory — domain `phBands` thuộc `entities/ph-bands` (p9 §9.5.3).
 * `['phBands']` là khoá gốc đúng như bảng domain key của p9 quy định.
 */
export const phBandsKeys = {
  all: ["phBands"] as const,
  list: () => [...phBandsKeys.all, "list"] as const,
  activeChart: (params: { productLine?: string; productionBatch?: string } = {}) =>
    [...phBandsKeys.all, "activeChart", params] as const,
};

/**
 * F1 — `GET /reference/ph-bands`. Nguồn DUY NHẤT cho `PhGaugeBar`/`PhBadge` (p9 §9.2.4).
 * `staleTime` 24h (p9 §9.5.4): cấu hình admin, gần như tĩnh; response BE trả bare array
 * (không bọc `{items}}`) — xem `PublicColorChartController.listPhBands`.
 */
export function usePhBands(): UseQueryResult<PhBand[]> {
  return useQuery({
    queryKey: phBandsKeys.list(),
    queryFn: () => phBandsApiFetch<PhBand[]>("/reference/ph-bands"),
    staleTime: 24 * 60 * 60 * 1000,
  });
}

/** F5 — `GET /reference/color-charts/active`. Chỉ metadata, không toạ độ Lab. */
export function useActiveColorChart(
  params: {
    productLine?: string;
    productionBatch?: string;
  } = {},
): UseQueryResult<ActiveColorChart> {
  const query = new URLSearchParams();
  if (params.productLine) query.set("productLine", params.productLine);
  if (params.productionBatch) query.set("productionBatch", params.productionBatch);
  const qs = query.toString();

  return useQuery({
    queryKey: phBandsKeys.activeChart(params),
    queryFn: () => phBandsApiFetch<ActiveColorChart>(`/reference/color-charts/active${qs ? `?${qs}` : ""}`),
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Tra 1 dải theo giá trị pH — dùng ở nơi cần suy ra dải từ một con số (không tự phân loại lại).
 *
 * Bug thật đã sửa, hai lỗi cùng một gốc là "server bỏ key thay vì gửi null":
 *
 *  1. Bản trước so `band.phMin === null`. Nhưng `GET /reference/ph-bands` chạy với
 *     `default-property-inclusion: non_null`, nên dải mở KHÔNG có key đó: `LOW` chỉ có
 *     `phMax`, `HIGH` chỉ có `phMin`. `undefined === null` là `false`, rồi `ph >= undefined`
 *     cũng `false` ⇒ **hai dải biên không bao giờ khớp**. Triệu chứng thấy được: màn xu hướng
 *     in ra enum thô `SLIGHTLY_HIGH` thay vì nhãn đã dịch.
 *  2. `INCONCLUSIVE` không có CẢ HAI key. Nếu chỉ đổi sang `== null` thì nó thành dải vô hạn
 *     hai đầu và khớp MỌI giá trị pH — một pH nằm ngoài mọi dải sẽ bị gán nhầm nhãn
 *     "không kết luận được" thay vì trả `undefined`. Nên bỏ qua dải không có biên nào: đó là
 *     một trạng thái, không phải một khoảng.
 */
export function findBandForPh(bands: PhBand[] | undefined, ph: number | null | undefined): PhBand | undefined {
  if (!bands || ph === null || ph === undefined || Number.isNaN(ph)) return undefined;
  return bands.find((band) => {
    const min = band.phMin ?? null;
    const max = band.phMax ?? null;
    if (min === null && max === null) return false;
    const aboveMin = min === null || (band.minInclusive ? ph >= min : ph > min);
    const belowMax = max === null || (band.maxInclusive ? ph <= max : ph < max);
    return aboveMin && belowMax;
  });
}

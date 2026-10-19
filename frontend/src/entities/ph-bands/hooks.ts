import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { phBandsApiFetch } from "./api";
import type { ActiveColorChart, PhBand } from "./model";

export const phBandsKeys = {
  all: ["phBands"] as const,
  // `label`/`description` do server resolve theo `Accept-Language` → mỗi ngôn ngữ một cache.
  list: (language: string) => [...phBandsKeys.all, "list", language] as const,
  activeChart: (params: { productLine?: string; productionBatch?: string } = {}) =>
    [...phBandsKeys.all, "activeChart", params] as const,
};

export function usePhBands(): UseQueryResult<PhBand[]> {
  const { i18n } = useTranslation();
  return useQuery({
    queryKey: phBandsKeys.list(i18n.language),
    queryFn: () => phBandsApiFetch<PhBand[]>("/reference/ph-bands"),
    staleTime: 24 * 60 * 60 * 1000,
  });
}

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

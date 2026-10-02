import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import type { Cat } from "@/entities/cat";
import { getExportJob, listActiveCats, listExports, requestExport } from "./api";
import type { ExportJob, ExportJobPage, ExportRequestPayload } from "./types";

export const exportKeys = {
  all: ["export"] as const,
  list: () => [...exportKeys.all, "list"] as const,
  job: (jobId: string) => [...exportKeys.all, "job", jobId] as const,
  activeCats: () => [...exportKeys.all, "activeCats"] as const,
};

const ACTIVE_STATUSES = new Set(["QUEUED", "RUNNING"]);
const POLL_INTERVAL_MS = 2500;

/** D1 — danh sách mèo cho bước 1 wizard. */
export function useActiveCatsForExport(): UseQueryResult<Cat[]> {
  return useQuery({ queryKey: exportKeys.activeCats(), queryFn: listActiveCats, staleTime: 60 * 1000 });
}

/** J1 — tạo job xuất PDF. */
export function useRequestExport(): UseMutationResult<ExportJob, Error, ExportRequestPayload> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: requestExport,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: exportKeys.list() });
    },
  });
}

/** J2 — danh sách bản xuất của tôi. */
export function useExportJobs(): UseQueryResult<ExportJobPage> {
  return useQuery({ queryKey: exportKeys.list(), queryFn: () => listExports() });
}

/** J3 — poll trạng thái job tới khi `READY`/`FAILED`/`EXPIRED`. */
export function useExportJob(jobId: string | undefined): UseQueryResult<ExportJob> {
  return useQuery({
    queryKey: exportKeys.job(jobId ?? ""),
    queryFn: () => getExportJob(jobId ?? ""),
    enabled: Boolean(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && ACTIVE_STATUSES.has(status) ? POLL_INTERVAL_MS : false;
    },
  });
}

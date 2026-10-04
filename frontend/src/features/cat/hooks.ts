import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { isApiError } from "@/shared/api";
import { apiFetch, apiUploadAvatar } from "./api";
import type {
  Cat,
  CatBreed,
  CatHealthSurvey,
  CatHealthSurveyAnswers,
  CatNote,
  CatStatus,
  ClinicalSignReportResult,
  ClinicalSignSource,
  NoteType,
  PrimaryCatResult,
} from "@/entities/cat";
import type { CatMutationPayload, CatSummaryResponse } from "./types";

/**
 * Query key factory — domain `cat` thuộc `features/cat` (p9 §9.5.3). `catKeys.all` = `['cat']`.
 */
export const catKeys = {
  all: ["cat"] as const,
  lists: () => [...catKeys.all, "list"] as const,
  list: (status: CatStatus) => [...catKeys.lists(), status] as const,
  details: () => [...catKeys.all, "detail"] as const,
  detail: (catId: string) => [...catKeys.details(), catId] as const,
  notes: (catId: string) => [...catKeys.detail(catId), "notes"] as const,
  survey: (catId: string) => [...catKeys.detail(catId), "survey"] as const,
  summary: (catId: string) => [...catKeys.detail(catId), "summary"] as const,
  breeds: () => [...catKeys.all, "breeds"] as const,
};

// staleTime theo p9 §9.5.4: cat.list/cat.detail 5 phút, breeds gần như tĩnh (24h).
const CAT_STALE_TIME = 5 * 60 * 1000;
const BREEDS_STALE_TIME = 24 * 60 * 60 * 1000;

interface ListResponse<T> {
  items: T[];
  page: { limit: number; nextCursor: string | null; hasMore: boolean };
}

interface BreedListResponse {
  items: CatBreed[];
}

function buildBody(payload: CatMutationPayload): string {
  return JSON.stringify(payload);
}

/** Sau mọi mutation thay đổi hồ sơ mèo: `cat.lists()`, `cat.detail(id)`, `entitlement` (p9 §9.5.5). */
function invalidateAfterCatChange(queryClient: ReturnType<typeof useQueryClient>, catId?: string) {
  void queryClient.invalidateQueries({ queryKey: catKeys.lists() });
  if (catId) {
    void queryClient.invalidateQueries({ queryKey: catKeys.detail(catId) });
  }
  // `entitlement` thuộc `entities/user` (hạn mức hồ sơ đổi theo số mèo) — chỉ là 1 key
  // string dùng chung của QueryClient, không phải import chéo module.
  void queryClient.invalidateQueries({ queryKey: ["entitlement"] });
}

// ============================================================== D1/D2 — danh sách/tạo

/**
 * @param enabled đặt `false` cho khách chưa đăng nhập — `/cats` yêu cầu phiên, gọi khi chưa
 *                đăng nhập chắc chắn 403, không cần bắn request rồi nuốt lỗi.
 */
export function useCatList(status: CatStatus = "ACTIVE", enabled = true): UseQueryResult<ListResponse<Cat>> {
  return useQuery({
    queryKey: catKeys.list(status),
    queryFn: () => apiFetch<ListResponse<Cat>>(`/cats?status=${status}`),
    staleTime: CAT_STALE_TIME,
    enabled,
  });
}

export function useCreateCat(): UseMutationResult<Cat, Error, CatMutationPayload> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch<Cat>("/cats", { method: "POST", body: buildBody(payload) }),
    onSuccess: (cat) => {
      queryClient.setQueryData(catKeys.detail(cat.id), cat);
      invalidateAfterCatChange(queryClient, cat.id);
    },
  });
}

// ============================================================== D3/D4/D5 — chi tiết/sửa/xoá

export function useCat(catId: string | undefined): UseQueryResult<Cat> {
  return useQuery({
    queryKey: catKeys.detail(catId ?? ""),
    queryFn: () => apiFetch<Cat>(`/cats/${catId ?? ""}`),
    enabled: Boolean(catId),
    staleTime: CAT_STALE_TIME,
  });
}

/**
 * D12 — card tổng quan lâm sàng. Endpoint từng là stub 501, nay đã chạy thật.
 *
 * Không `retry` lỗi 4xx (queryClient đã cấu hình vậy) và KHÔNG `throwOnError`: màn hồ sơ
 * phải render được cả khi summary lỗi — mèo mới tạo chưa có lần quét nào là trạng thái hợp lệ.
 */
export function useCatSummary(catId: string | undefined): UseQueryResult<CatSummaryResponse> {
  return useQuery({
    queryKey: catKeys.summary(catId ?? ""),
    queryFn: () => apiFetch<CatSummaryResponse>(`/cats/${catId ?? ""}/summary`),
    enabled: Boolean(catId),
    staleTime: CAT_STALE_TIME,
  });
}

export function usePatchCat(catId: string): UseMutationResult<Cat, Error, CatMutationPayload> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch<Cat>(`/cats/${catId}`, { method: "PATCH", body: buildBody(payload) }),
    onSuccess: (cat) => {
      queryClient.setQueryData(catKeys.detail(catId), cat);
      invalidateAfterCatChange(queryClient, catId);
    },
  });
}

export function useSoftDeleteCat(catId: string): UseMutationResult<void, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>(`/cats/${catId}`, { method: "DELETE" }),
    onSuccess: () => {
      invalidateAfterCatChange(queryClient, catId);
    },
  });
}

// ============================================================== D6/D7 — lưu trữ

export function useArchiveCat(catId: string): UseMutationResult<Cat, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<Cat>(`/cats/${catId}/archive`, { method: "POST", body: "{}" }),
    onSuccess: (cat) => {
      queryClient.setQueryData(catKeys.detail(catId), cat);
      invalidateAfterCatChange(queryClient, catId);
    },
  });
}

export function useUnarchiveCat(catId: string): UseMutationResult<Cat, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<Cat>(`/cats/${catId}/unarchive`, { method: "POST", body: "{}" }),
    onSuccess: (cat) => {
      queryClient.setQueryData(catKeys.detail(catId), cat);
      invalidateAfterCatChange(queryClient, catId);
    },
  });
}

// ============================================================== D8 — mèo chính

export function useSetPrimaryCat(catId: string): UseMutationResult<PrimaryCatResult, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<PrimaryCatResult>(`/cats/${catId}/primary`, { method: "PUT", body: "{}" }),
    onSuccess: (result) => {
      invalidateAfterCatChange(queryClient, result.catId);
      if (result.previousPrimaryCatId) {
        void queryClient.invalidateQueries({ queryKey: catKeys.detail(result.previousPrimaryCatId) });
      }
    },
  });
}

// ============================================================== D9/D10/D11 — ảnh đại diện
//
// `catId` nhận qua biến mutate (không bind sẵn ở tham số hook) — cần thiết cho luồng
// "tạo mèo rồi upload avatar ngay" (`CatNewPage`): hook được khởi tạo LÚC RENDER (khi
// `catId` của mèo mới còn chưa tồn tại), còn lệnh upload chỉ thực thi SAU KHI tạo xong
// trong cùng handler — bind `catId` ở tham số hook sẽ đóng băng giá trị rỗng của lần
// render trước, gọi nhầm `/cats//avatar`.

export function useUploadCatAvatar(): UseMutationResult<{ avatarUrl: string }, Error, { catId: string; file: File }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ catId, file }) => apiUploadAvatar(`/cats/${catId}/avatar`, file),
    onSuccess: (_result, { catId }) => {
      invalidateAfterCatChange(queryClient, catId);
    },
  });
}

export function useRemoveCatAvatar(): UseMutationResult<void, Error, { catId: string }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ catId }) => apiFetch<undefined>(`/cats/${catId}/avatar`, { method: "DELETE" }),
    onSuccess: (_result, { catId }) => {
      invalidateAfterCatChange(queryClient, catId);
    },
  });
}

// ============================================================== D14/D15 — khảo sát sức khoẻ

export function useHealthSurvey(catId: string | undefined): UseQueryResult<CatHealthSurvey | null> {
  return useQuery({
    queryKey: catKeys.survey(catId ?? ""),
    queryFn: async () => {
      try {
        return await apiFetch<CatHealthSurvey>(`/cats/${catId ?? ""}/health-survey`);
      } catch (error) {
        // SURVEY_NOT_FOUND (404) = chưa từng khảo sát — không phải lỗi cần hiện ErrorState.
        if (isApiError(error) && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
    enabled: Boolean(catId),
    staleTime: CAT_STALE_TIME,
  });
}

export function useSubmitHealthSurvey(
  catId: string,
): UseMutationResult<CatHealthSurvey, Error, { answers: CatHealthSurveyAnswers; skipped: boolean }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ answers, skipped }) =>
      apiFetch<CatHealthSurvey>(`/cats/${catId}/health-survey`, {
        method: "POST",
        body: JSON.stringify({ questionnaireVersion: "v1", answers, skipped }),
      }),
    onSuccess: (survey) => {
      queryClient.setQueryData(catKeys.survey(catId), survey);
    },
  });
}

// ============================================================== D16-D19 — ghi chú

export function useCatNotes(catId: string | undefined): UseQueryResult<ListResponse<CatNote>> {
  return useQuery({
    queryKey: catKeys.notes(catId ?? ""),
    queryFn: () => apiFetch<ListResponse<CatNote>>(`/cats/${catId ?? ""}/notes`),
    enabled: Boolean(catId),
    staleTime: CAT_STALE_TIME,
  });
}

export function useCreateNote(
  catId: string,
): UseMutationResult<CatNote, Error, { noteType: NoteType; body: string; occurredOn: string | null }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input) => apiFetch<CatNote>(`/cats/${catId}/notes`, { method: "POST", body: JSON.stringify(input) }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: catKeys.notes(catId) });
    },
  });
}

export function usePatchNote(
  catId: string,
  noteId: string,
): UseMutationResult<CatNote, Error, { noteType?: NoteType; body?: string; occurredOn?: string | null }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input) => apiFetch<CatNote>(`/cat-notes/${noteId}`, { method: "PATCH", body: JSON.stringify(input) }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: catKeys.notes(catId) });
    },
  });
}

export function useDeleteNote(catId: string, noteId: string): UseMutationResult<void, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>(`/cat-notes/${noteId}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: catKeys.notes(catId) });
    },
  });
}

// ============================================================== D20 — dấu hiệu lâm sàng

export function useReportClinicalSigns(
  catId: string,
): UseMutationResult<ClinicalSignReportResult, Error, { signs: string[]; source: ClinicalSignSource; note?: string }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input) =>
      apiFetch<ClinicalSignReportResult>(`/cats/${catId}/clinical-signs`, {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: catKeys.detail(catId) });
    },
  });
}

// ============================================================== F2 — giống mèo

export function useBreeds(): UseQueryResult<BreedListResponse> {
  return useQuery({
    queryKey: catKeys.breeds(),
    queryFn: () => apiFetch<BreedListResponse>("/reference/cat-breeds"),
    staleTime: BREEDS_STALE_TIME,
  });
}

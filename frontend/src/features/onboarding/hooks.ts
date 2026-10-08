import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { apiFetch, apiUploadAvatar } from "./api";
import type {
  ActivationResult,
  BreedListResponse,
  CatProfileDraft,
  CatSummary,
  CreatedCat,
  CreditBalance,
  HealthSurveyDefinition,
  PhBandListResponse,
  SurveyAnswers,
} from "./types";
import { SURVEY_QUESTIONNAIRE_VERSION } from "./types";

/**
 * Hooks react-query cho onboarding — mọi call đi qua `apiFetch` (features/onboarding/api.ts)
 * theo hợp đồng p8. W3 nối API thật: thay `apiFetch` bằng `apiClient` + type OpenAPI,
 * hooks không đổi chữ ký.
 */

export function useBreeds(): UseQueryResult<BreedListResponse> {
  return useQuery({
    queryKey: ["onboarding", "breeds"],
    queryFn: () => apiFetch<BreedListResponse>("/reference/cat-breeds"),
    staleTime: 5 * 60 * 1000,
  });
}

export function usePhBands(): UseQueryResult<PhBandListResponse> {
  return useQuery({
    queryKey: ["onboarding", "ph-bands"],
    queryFn: () => apiFetch<PhBandListResponse>("/reference/ph-bands"),
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * F6 — định nghĩa bộ câu hỏi khảo sát. Cần phiên đăng nhập (SecurityConfig đặt matcher
 * `/reference/health-survey/**` TRƯỚC `/reference/**` permitAll), nội dung tĩnh theo version
 * nên cache dài. Đây là NGUỒN DUY NHẤT của danh sách câu hỏi/lựa chọn — màn khảo sát không
 * giữ mảng `QUESTION_NAMES` cứng nữa.
 */
export function useHealthSurveyDefinition(
  version: string = SURVEY_QUESTIONNAIRE_VERSION,
): UseQueryResult<HealthSurveyDefinition> {
  return useQuery({
    queryKey: ["onboarding", "health-survey-definition", version],
    queryFn: () => apiFetch<HealthSurveyDefinition>(`/reference/health-survey/${version}`),
    staleTime: 30 * 60 * 1000,
  });
}

export function useCreateCat(): UseMutationResult<CreatedCat, Error, CatProfileDraft> {
  return useMutation({
    mutationFn: (draft: CatProfileDraft) =>
      apiFetch<CreatedCat>("/cats", {
        method: "POST",
        body: JSON.stringify({
          name: draft.name,
          breedCode: draft.breedCode,
          sex: draft.sex,
          neutered: draft.neutered,
          birthDate: draft.birthDate || null,
          weightKg: draft.weightKg ? Number(draft.weightKg) : null,
          isPrimary: true,
        }),
      }),
  });
}

export function useUpdateCat(catId: string): UseMutationResult<CreatedCat, Error, CatProfileDraft> {
  return useMutation({
    mutationFn: (draft: CatProfileDraft) =>
      apiFetch<CreatedCat>(`/cats/${catId}`, {
        method: "PATCH",
        body: JSON.stringify({
          name: draft.name,
          breedCode: draft.breedCode,
          sex: draft.sex,
          neutered: draft.neutered,
          birthDate: draft.birthDate || null,
          weightKg: draft.weightKg ? Number(draft.weightKg) : null,
        }),
      }),
  });
}

export function useUploadAvatar(): UseMutationResult<{ avatarUrl: string }, Error, { catId: string; file: File }> {
  return useMutation({
    mutationFn: ({ catId, file }: { catId: string; file: File }) => apiUploadAvatar(`/cats/${catId}/avatar`, file),
  });
}

export function useSubmitSurvey(
  catId: string,
): UseMutationResult<unknown, Error, { answers: SurveyAnswers; skipped: boolean }> {
  return useMutation({
    mutationFn: ({ answers, skipped }: { answers: SurveyAnswers; skipped: boolean }) =>
      apiFetch<unknown>(`/cats/${catId}/health-survey`, {
        method: "POST",
        body: JSON.stringify({ questionnaireVersion: SURVEY_QUESTIONNAIRE_VERSION, answers, skipped }),
      }),
  });
}

export function useActivateCode(): UseMutationResult<ActivationResult, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) =>
      apiFetch<ActivationResult>("/activations", {
        method: "POST",
        body: JSON.stringify({ code }),
      }),
    onSuccess: () => {
      // Số dư đã đổi — màn Hoàn tất (và gợi ý lượt thử ở màn này) phải đọc lại, không dùng cache.
      void queryClient.invalidateQueries({ queryKey: ["onboarding", "credit-balance"] });
    },
  });
}

export function useCreditBalance(): UseQueryResult<CreditBalance> {
  return useQuery({
    queryKey: ["onboarding", "credit-balance"],
    queryFn: () => apiFetch<CreditBalance>("/credits/balance"),
  });
}

interface CatListItem {
  id: string;
  publicCode: string;
  name: string;
  breedCode?: string | null;
  breedName?: string | null;
  breedOther?: string | null;
  sex: CreatedCat["sex"];
  neutered: boolean;
  birthDate?: string | null;
  ageMonths?: number | null;
  weightKg?: number | null;
  avatarUrl?: string | null;
  isPrimary?: boolean;
}

/**
 * D3 `GET /cats` — dùng ở màn Hoàn tất khi store onboarding trống (tải lại trang, mở thẳng
 * `/onboarding/success`): lấy hồ sơ chính (`isPrimary`) thay vì báo "Đã có lỗi xảy ra".
 * Server bỏ key null (`non_null`) nên các field tuỳ chọn khai `?`.
 */
export function usePrimaryCat(enabled: boolean): UseQueryResult<CreatedCat | null> {
  return useQuery({
    queryKey: ["onboarding", "primary-cat"],
    queryFn: async () => {
      const page = await apiFetch<{ items: CatListItem[] }>("/cats");
      const item = page.items.find((c) => c.isPrimary) ?? page.items.at(0);
      if (!item) return null;
      return {
        id: item.id,
        publicCode: item.publicCode,
        name: item.name,
        breedCode: item.breedCode ?? "",
        breedName: item.breedName ?? item.breedOther ?? "",
        sex: item.sex,
        neutered: item.neutered,
        birthDate: item.birthDate ?? null,
        ageMonths: item.ageMonths ?? null,
        weightKg: item.weightKg ?? null,
        avatarUrl: item.avatarUrl ?? null,
      };
    },
    enabled,
  });
}

export function useCatSummary(catId: string | undefined): UseQueryResult<CatSummary> {
  return useQuery({
    queryKey: ["onboarding", "cat-summary", catId],
    queryFn: () => {
      if (!catId) throw new Error("catId is required");
      return apiFetch<CatSummary>(`/cats/${catId}/summary`);
    },
    enabled: Boolean(catId),
  });
}

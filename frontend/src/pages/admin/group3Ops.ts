import { useMutation, useQueryClient, type UseMutationResult } from "@tanstack/react-query";
import { ApiError } from "@/shared/api";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";
import { ADMIN_REASON_MIN_LENGTH, useAdminCan } from "@/features/admin";
import type { ROLES } from "@/shared/auth/roles";

export type Group3Capability = Extract<
  keyof typeof ROLES,
  "dashboard" | "jobs" | "jobRun" | "outbox" | "broadcast" | "broadcastIncident" | "auditLog"
>;

/** Narrowed view of {@link useAdminCan}; stable across renders while capabilities are unchanged. */
export function useGroup3Can(): (key: Group3Capability) => boolean {
  return useAdminCan();
}

export const BROADCAST_TEMPLATES = ["SYSTEM_MAINTENANCE", "PRIVACY_INCIDENT_NOTICE"] as const;
export type BroadcastTemplate = (typeof BROADCAST_TEMPLATES)[number];
export const INCIDENT_TEMPLATE: BroadcastTemplate = "PRIVACY_INCIDENT_NOTICE";
export const BROADCAST_TITLE_MAX = 200;
export const BROADCAST_BODY_MAX = 4000;

export function allowedBroadcastTemplates(can: (key: Group3Capability) => boolean): BroadcastTemplate[] {
  return BROADCAST_TEMPLATES.filter((code) => can(code === INCIDENT_TEMPLATE ? "broadcastIncident" : "broadcast"));
}

export interface BroadcastForm {
  templateCode: string;
  title: string;
  body: string;
  reason: string;
}

export type BroadcastFormError = "template" | "title" | "body" | "reason";

export function validateBroadcastForm(form: BroadcastForm): BroadcastFormError[] {
  const errors: BroadcastFormError[] = [];
  if (!(BROADCAST_TEMPLATES as readonly string[]).includes(form.templateCode)) errors.push("template");
  const title = form.title.trim();
  if (title.length === 0 || title.length > BROADCAST_TITLE_MAX) errors.push("title");
  const body = form.body.trim();
  if (body.length === 0 || body.length > BROADCAST_BODY_MAX) errors.push("body");
  if (form.reason.trim().length < ADMIN_REASON_MIN_LENGTH) errors.push("reason");
  return errors;
}

export interface BroadcastRequest {
  templateCode: string;
  title: string;
  body: string;
  reason: string;
  dryRun: boolean;
  confirmed: boolean;
}

export function buildBroadcastRequest(
  form: BroadcastForm,
  mode: "dry" | "send",
  confirmed: boolean,
): BroadcastRequest | null {
  if (validateBroadcastForm(form).length > 0) return null;
  if (mode === "send" && !confirmed) return null;
  return {
    templateCode: form.templateCode,
    title: form.title.trim(),
    body: form.body.trim(),
    reason: form.reason.trim(),
    dryRun: mode === "dry",
    confirmed: mode === "send",
  };
}

export function canResendOutbox(can: (key: Group3Capability) => boolean, status: string): boolean {
  return can("outbox") && status === "FAILED";
}

export function isJobRunValid(jobName: string, reason: string): boolean {
  return jobName.trim().length > 0 && reason.trim().length >= ADMIN_REASON_MIN_LENGTH;
}

function readCsrf(): string | null {
  const match = new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`).exec(document.cookie);
  const raw = match?.[1];
  return raw !== undefined && raw !== "" ? decodeURIComponent(raw) : null;
}

async function adminPost<T>(path: string, json?: unknown): Promise<T> {
  const headers = new Headers({
    Accept: "application/json",
    "Accept-Language": currentAcceptLanguage(),
    [IDEMPOTENCY_HEADER_NAME]: crypto.randomUUID(),
  });
  if (json !== undefined) headers.set("Content-Type", "application/json");
  const csrf = readCsrf();
  if (csrf !== null) headers.set(CSRF_HEADER_NAME, csrf);
  const response = await fetch(`/api/v1${path}`, {
    method: "POST",
    credentials: "include",
    headers,
    ...(json === undefined ? {} : { body: JSON.stringify(json) }),
  });
  if (!response.ok) {
    let problem: { detail?: string; errorCode?: string } | undefined;
    try {
      problem = (await response.json()) as { detail?: string; errorCode?: string };
    } catch {
      // Body lỗi không phải JSON: giữ thông điệp mặc định theo mã HTTP.
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  const text = await response.text();
  return (text.length > 0 ? JSON.parse(text) : undefined) as T;
}

export interface RunJobResult {
  jobName: string;
  status: string;
  dryRun: boolean | null;
  itemsProcessed: number | null;
  itemsDeleted: number | null;
  itemsFailed: number | null;
  errorSummary: string | null;
}

export function useRunAdminJob(): UseMutationResult<
  RunJobResult,
  Error,
  { jobName: string; dryRun: boolean; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ jobName, dryRun, reason }) =>
      adminPost<RunJobResult>(`/admin/jobs/${encodeURIComponent(jobName.trim())}/run`, { dryRun, reason }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "job-runs"] }),
  });
}

export function useResendAdminOutbox(): UseMutationResult<undefined, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => adminPost<undefined>(`/admin/notifications/outbox/${encodeURIComponent(id)}/resend`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "outbox"] }),
  });
}

export interface BroadcastResult {
  templateCode: string;
  dryRun: boolean;
  recipientsMatched: number;
  notificationsQueued: number;
  dedupeKeyPrefix: string | null;
}

export function useAdminBroadcast(): UseMutationResult<BroadcastResult, Error, BroadcastRequest> {
  return useMutation({
    mutationFn: (request) => adminPost<BroadcastResult>("/admin/system/broadcast", request),
  });
}

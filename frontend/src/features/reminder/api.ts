import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";
import type {
  CreateReminderPayload,
  PatchReminderPayload,
  Reminder,
  ReminderListFilter,
  ReminderListResponse,
} from "./types";

const BASE_URL = "/api/v1";

const MERGE_PATCH_CONTENT_TYPE = "application/merge-patch+json";

interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  errorCode?: string;
  code?: string;
}

function readCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  headers.set("Accept-Language", currentAcceptLanguage());
  if (options.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const csrf = readCsrfToken();
  if (csrf) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }
  const response = await fetch(`${BASE_URL}${path}`, { ...options, credentials: "include", headers });
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode ?? problem?.code ?? problem?.title,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}


export function apiTimeToInput(value: string | undefined): string {
  return value === undefined ? "" : value.slice(0, 5);
}

export function inputTimeToApi(value: string): string | undefined {
  return value === "" ? undefined : value.slice(0, 5);
}


export function listReminders(filter: ReminderListFilter = {}): Promise<ReminderListResponse> {
  const search = new URLSearchParams();
  if (filter.catId !== undefined) search.set("catId", filter.catId);
  if (filter.active !== undefined) search.set("active", String(filter.active));
  const query = search.toString();
  return apiFetch<ReminderListResponse>(`/reminders${query === "" ? "" : `?${query}`}`);
}

export function createReminder(payload: CreateReminderPayload): Promise<Reminder> {
  return apiFetch<Reminder>("/reminders", { method: "POST", body: JSON.stringify(payload) });
}

export function getReminder(reminderId: string): Promise<Reminder> {
  return apiFetch<Reminder>(`/reminders/${reminderId}`);
}

export function patchReminder(reminderId: string, payload: PatchReminderPayload): Promise<Reminder> {
  return apiFetch<Reminder>(`/reminders/${reminderId}`, {
    method: "PATCH",
    headers: { "Content-Type": MERGE_PATCH_CONTENT_TYPE },
    body: JSON.stringify(payload),
  });
}

export async function deleteReminder(reminderId: string): Promise<void> {
  await apiFetch<undefined>(`/reminders/${reminderId}`, { method: "DELETE" });
}

export function reminderCalendarUrl(reminderId: string): string {
  return `${BASE_URL}/reminders/${reminderId}/calendar.ics`;
}

import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

const BASE_URL = "/api/v1";

interface ProblemDetail {
  detail?: string;
  errorCode?: string;
}

function csrf(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrfCookie(): Promise<void> {
  if (csrf()) return;
  try {
    await fetch(`${BASE_URL}/auth/csrf`, { credentials: "include" });
  } catch {
    // The request below surfaces the real error if the CSRF cookie is still missing.
  }
}

async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method ?? "GET";
  if (method !== "GET") await ensureCsrfCookie();
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  headers.set("Accept-Language", currentAcceptLanguage());
  if (options.body !== undefined && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const token = csrf();
  if (token) headers.set(CSRF_HEADER_NAME, token);
  if (method !== "GET") headers.set(IDEMPOTENCY_HEADER_NAME, crypto.randomUUID());
  const response = await fetch(`${BASE_URL}${path}`, { ...options, credentials: "include", headers });
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Non-JSON error bodies fall back to the status-only message.
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const COMMUNITY_CATEGORIES = ["QA", "TIP", "EXPERIENCE"] as const;
export type CommunityCategory = (typeof COMMUNITY_CATEGORIES)[number];

export const COMMUNITY_REPORT_REASONS = ["SPAM", "MISLEADING", "HARASSMENT", "PRIVACY", "OTHER"] as const;
export type CommunityReportReason = (typeof COMMUNITY_REPORT_REASONS)[number];

export const COMMUNITY_LIMITS = {
  title: 180,
  body: 10_000,
  tags: 8,
  tag: 48,
  comment: 4000,
  reportDetails: 1000,
} as const;

export const COMMUNITY_IMAGE = {
  maxBytes: 8 * 1024 * 1024,
  types: ["image/jpeg", "image/png", "image/webp"] as readonly string[],
} as const;

export interface CommunityPostApi {
  id: string;
  authorName: string;
  category: string;
  title: string;
  body: string;
  tags: string[];
  imageUrl?: string | null;
  likeCount: number;
  commentCount: number;
  liked: boolean;
  bookmarked: boolean;
  createdAt: string;
}

export interface CommunityPageApi {
  items: CommunityPostApi[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

export interface CommunityCommentApi {
  id: string;
  postId: string;
  /** Top-level comment this reply belongs to; null/absent for top-level comments. */
  parentCommentId?: string | null;
  authorName: string;
  /** Set when the reply answers another reply ("trả lời <tên>"). */
  replyToName?: string | null;
  body: string;
  imageUrl?: string | null;
  /** Published replies of a top-level comment; 0 for replies. */
  replyCount?: number;
  /** First few replies of a top-level comment (see COMMUNITY_REPLY_PREVIEW); the rest are paged. */
  replies?: CommunityCommentApi[];
  createdAt: string;
}

export interface CommunityCommentRepliesApi {
  items: CommunityCommentApi[];
  offset: number;
  limit: number;
  totalElements: number;
  hasMore: boolean;
}

/** Replies embedded with each top-level comment in the post detail (CommunityService.REPLY_PREVIEW_SIZE). */
export const COMMUNITY_REPLY_PREVIEW = 3;
export const COMMUNITY_REPLY_PAGE_SIZE = 10;

export interface CreateCommunityCommentPayload {
  body: string;
  parentCommentId?: string;
  image?: File | null;
}

export interface CommunityPostDetailApi {
  post: CommunityPostApi;
  comments: CommunityCommentApi[];
}

export interface CreateCommunityPostPayload {
  category: CommunityCategory;
  title: string;
  body: string;
  tags: string[];
}

export type CommunityReaction = "LIKE" | "BOOKMARK";

export interface CommunityReportPayload {
  postId?: string;
  commentId?: string;
  reason: CommunityReportReason;
  details?: string;
}

export function listCommunityPosts(params: { category?: string; page?: number; size?: number } = {}) {
  const query = new URLSearchParams();
  if (params.category) query.set("category", params.category);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return apiFetch<CommunityPageApi>(`/community/posts${suffix}`);
}

export function createCommunityPost(payload: CreateCommunityPostPayload) {
  return apiFetch<CommunityPostApi>("/community/posts", { method: "POST", body: JSON.stringify(payload) });
}

export function getCommunityPost(postId: string) {
  return apiFetch<CommunityPostDetailApi>(`/community/posts/${encodeURIComponent(postId)}`);
}

export function createCommunityComment(postId: string, payload: CreateCommunityCommentPayload) {
  const path = `/community/posts/${encodeURIComponent(postId)}/comments`;
  if (!payload.image) {
    return apiFetch<CommunityCommentApi>(path, {
      method: "POST",
      body: JSON.stringify({ body: payload.body, parentCommentId: payload.parentCommentId }),
    });
  }
  const form = new FormData();
  form.append("body", payload.body);
  if (payload.parentCommentId) form.append("parentCommentId", payload.parentCommentId);
  form.append("file", payload.image);
  return apiFetch<CommunityCommentApi>(path, { method: "POST", body: form });
}

export function listCommunityCommentReplies(postId: string, commentId: string, offset: number, limit: number) {
  const query = new URLSearchParams({ offset: String(offset), limit: String(limit) });
  return apiFetch<CommunityCommentRepliesApi>(
    `/community/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}/replies?${query.toString()}`,
  );
}

export function setCommunityReaction(postId: string, reaction: CommunityReaction, active: boolean) {
  return apiFetch<{ reaction: string; active: boolean }>(`/community/posts/${encodeURIComponent(postId)}/reactions`, {
    method: "POST",
    body: JSON.stringify({ reaction, active }),
  });
}

export function reportCommunityContent(payload: CommunityReportPayload) {
  return apiFetch<undefined>("/community/reports", { method: "POST", body: JSON.stringify(payload) });
}

export async function uploadCommunityPostImage(
  postId: string,
  file: File,
  onProgress?: (fraction: number) => void,
): Promise<CommunityPostApi> {
  await ensureCsrfCookie();
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${BASE_URL}/community/posts/${encodeURIComponent(postId)}/image`);
    xhr.withCredentials = true;
    xhr.setRequestHeader("Accept", "application/json");
    xhr.setRequestHeader("Accept-Language", currentAcceptLanguage());
    xhr.setRequestHeader(IDEMPOTENCY_HEADER_NAME, crypto.randomUUID());
    const token = csrf();
    if (token) xhr.setRequestHeader(CSRF_HEADER_NAME, token);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(event.loaded / event.total);
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText) as CommunityPostApi);
      } else {
        reject(new ApiError(`Upload failed with status ${String(xhr.status)}`, xhr.status));
      }
    };
    xhr.onerror = () => {
      reject(new ApiError("Upload failed", 0));
    };
    const form = new FormData();
    form.append("file", file);
    xhr.send(form);
  });
}

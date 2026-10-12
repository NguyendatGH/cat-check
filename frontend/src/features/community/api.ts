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
  }
}

async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method ?? "GET";
  if (method !== "GET") await ensureCsrfCookie();
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  headers.set("Accept-Language", currentAcceptLanguage());
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  const token = csrf();
  if (token) headers.set(CSRF_HEADER_NAME, token);
  if (method !== "GET") headers.set(IDEMPOTENCY_HEADER_NAME, crypto.randomUUID());
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
  authorName: string;
  body: string;
  createdAt: string;
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

export function createCommunityComment(postId: string, body: string) {
  return apiFetch<CommunityCommentApi>(`/community/posts/${encodeURIComponent(postId)}/comments`, {
    method: "POST",
    body: JSON.stringify({ body }),
  });
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

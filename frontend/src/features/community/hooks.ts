import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type QueryClient,
} from "@tanstack/react-query";
import {
  COMMUNITY_REPLY_PAGE_SIZE,
  createCommunityComment,
  listCommunityCommentReplies,
  createCommunityPost,
  getCommunityPost,
  listCommunityPosts,
  reportCommunityContent,
  setCommunityReaction,
  type CommunityCategory,
  type CommunityPageApi,
  type CommunityPostApi,
  type CommunityPostDetailApi,
  type CommunityReaction,
  type CreateCommunityCommentPayload,
} from "./api";

export const communityKeys = {
  all: ["community"] as const,
  feeds: () => [...communityKeys.all, "posts"] as const,
  feed: (category: CommunityCategory | "ALL") => [...communityKeys.feeds(), category] as const,
  detail: (postId: string) => [...communityKeys.all, "post", postId] as const,
  replies: (postId: string, commentId: string) => [...communityKeys.detail(postId), "replies", commentId] as const,
};

const COMMUNITY_STALE_TIME = 30_000;
export const COMMUNITY_PAGE_SIZE = 10;

export function useCommunityFeed(category: CommunityCategory | "ALL") {
  return useInfiniteQuery({
    queryKey: communityKeys.feed(category),
    queryFn: ({ pageParam }) =>
      listCommunityPosts({
        category: category === "ALL" ? undefined : category,
        page: pageParam,
        size: COMMUNITY_PAGE_SIZE,
      }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasMore ? last.page + 1 : undefined),
    staleTime: COMMUNITY_STALE_TIME,
  });
}

export function useCommunityPost(postId: string) {
  return useQuery({
    queryKey: communityKeys.detail(postId),
    queryFn: () => getCommunityPost(postId),
    staleTime: COMMUNITY_STALE_TIME,
  });
}

export function useCreateCommunityPost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createCommunityPost,
    onSuccess: (post) => {
      queryClient.setQueryData<CommunityPostDetailApi>(communityKeys.detail(post.id), { post, comments: [] });
      void queryClient.invalidateQueries({ queryKey: communityKeys.feeds() });
    },
  });
}

export function useCreateCommunityComment(postId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCommunityCommentPayload) => createCommunityComment(postId, payload),
    onSuccess: (comment) => {
      queryClient.setQueryData<CommunityPostDetailApi>(communityKeys.detail(postId), (old) => {
        if (!old) return old;
        const post = { ...old.post, commentCount: old.post.commentCount + 1 };
        const parentId = comment.parentCommentId;
        if (!parentId) return { post, comments: [...old.comments, { ...comment, replyCount: 0, replies: [] }] };
        return {
          post,
          comments: old.comments.map((item) =>
            item.id === parentId ? { ...item, replyCount: (item.replyCount ?? 0) + 1 } : item,
          ),
        };
      });
      void queryClient.invalidateQueries({ queryKey: communityKeys.detail(postId), exact: true });
      void queryClient.invalidateQueries({ queryKey: communityKeys.feeds() });
    },
  });
}

/**
 * Replies of one top-level comment beyond the preview embedded in the post detail. Disabled until
 * the reader expands the thread; pages continue from {@code startOffset}.
 */
export function useCommunityReplies(postId: string, commentId: string, startOffset: number, enabled: boolean) {
  return useInfiniteQuery({
    queryKey: [...communityKeys.replies(postId, commentId), startOffset] as const,
    queryFn: ({ pageParam }) => listCommunityCommentReplies(postId, commentId, pageParam, COMMUNITY_REPLY_PAGE_SIZE),
    initialPageParam: startOffset,
    getNextPageParam: (last) => (last.hasMore ? last.offset + last.items.length : undefined),
    enabled,
    staleTime: COMMUNITY_STALE_TIME,
  });
}

function patchPost(post: CommunityPostApi, reaction: CommunityReaction, active: boolean): CommunityPostApi {
  if (reaction === "BOOKMARK") return { ...post, bookmarked: active };
  if (post.liked === active) return post;
  return { ...post, liked: active, likeCount: Math.max(0, post.likeCount + (active ? 1 : -1)) };
}

type FeedCache = InfiniteData<CommunityPageApi, number>;

function applyEverywhere(
  queryClient: QueryClient,
  postId: string,
  update: (post: CommunityPostApi) => CommunityPostApi,
): void {
  queryClient.setQueryData<CommunityPostDetailApi>(communityKeys.detail(postId), (old) =>
    old ? { ...old, post: update(old.post) } : old,
  );
  queryClient.setQueriesData<FeedCache>({ queryKey: communityKeys.feeds() }, (old) =>
    old
      ? {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            items: page.items.map((item) => (item.id === postId ? update(item) : item)),
          })),
        }
      : old,
  );
}

export function useCommunityReaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { postId: string; reaction: CommunityReaction; active: boolean }) =>
      setCommunityReaction(vars.postId, vars.reaction, vars.active),
    onMutate: async ({ postId, reaction, active }) => {
      await queryClient.cancelQueries({ queryKey: communityKeys.all });
      const detail = queryClient.getQueryData<CommunityPostDetailApi>(communityKeys.detail(postId));
      const feeds = queryClient.getQueriesData<FeedCache>({ queryKey: communityKeys.feeds() });
      applyEverywhere(queryClient, postId, (post) => patchPost(post, reaction, active));
      return { detail, feeds };
    },
    onError: (_error, { postId }, context) => {
      if (!context) return;
      queryClient.setQueryData(communityKeys.detail(postId), context.detail);
      for (const [key, data] of context.feeds) queryClient.setQueryData(key, data);
    },
    onSettled: (_data, _error, { postId }) => {
      void queryClient.invalidateQueries({ queryKey: communityKeys.detail(postId) });
      void queryClient.invalidateQueries({ queryKey: communityKeys.feeds() });
    },
  });
}

export function useReportCommunityContent() {
  return useMutation({ mutationFn: reportCommunityContent });
}

export function useCommunityTagSuggestions(limit = 12): string[] {
  const queryClient = useQueryClient();
  const counts = new Map<string, number>();
  for (const [, data] of queryClient.getQueriesData<InfiniteData<CommunityPageApi>>({ queryKey: communityKeys.feeds() })) {
    data?.pages.forEach((page) => {
      page.items.forEach((post) => {
        post.tags.forEach((tag) => {
          const key = tag.replace(/^#+/, "").trim().toLowerCase();
          if (key) counts.set(key, (counts.get(key) ?? 0) + 1);
        });
      });
    });
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([tag]) => tag);
}

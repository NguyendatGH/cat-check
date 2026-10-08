import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type QueryClient,
} from "@tanstack/react-query";
import {
  createCommunityComment,
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
} from "./api";

/**
 * Query-key factory cho Community API. Gốc `["community"]`, nhánh `posts` (bảng tin, theo
 * chuyên mục) và `post` (chi tiết + bình luận) để invalidate theo cụm.
 */
export const communityKeys = {
  all: ["community"] as const,
  feeds: () => [...communityKeys.all, "posts"] as const,
  feed: (category: CommunityCategory | "ALL") => [...communityKeys.feeds(), category] as const,
  detail: (postId: string) => [...communityKeys.all, "post", postId] as const,
};

/** Bảng tin đổi theo hoạt động người dùng — 30s đủ tươi mà không gọi lại liên tục. */
const COMMUNITY_STALE_TIME = 30_000;
export const COMMUNITY_PAGE_SIZE = 10;

/** Bảng tin phân trang offset (`page`/`size`/`hasMore`) — lọc chuyên mục ở SERVER (`?category=`). */
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
    // 404 (bài không tồn tại / đã bị ẩn) không bị thử lại: `queryClient` mặc định bỏ retry với 4xx.
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
    mutationFn: (body: string) => createCommunityComment(postId, body),
    onSuccess: (comment) => {
      queryClient.setQueryData<CommunityPostDetailApi>(communityKeys.detail(postId), (old) =>
        old
          ? {
              post: { ...old.post, commentCount: old.post.commentCount + 1 },
              comments: [...old.comments, comment],
            }
          : old,
      );
      void queryClient.invalidateQueries({ queryKey: communityKeys.detail(postId) });
      void queryClient.invalidateQueries({ queryKey: communityKeys.feeds() });
    },
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

/**
 * Thích / lưu bài. Cập nhật lạc quan ở MỌI cache đang chứa bài (chi tiết + mọi trang bảng
 * tin), rồi luôn refetch khi xong để giao diện khớp đúng trạng thái server trả về.
 */
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

// features/community — client cho Community API (/api/v1/community/**).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
export {
  COMMUNITY_CATEGORIES,
  COMMUNITY_LIMITS,
  COMMUNITY_REPORT_REASONS,
  createCommunityComment,
  createCommunityPost,
  getCommunityPost,
  listCommunityPosts,
  reportCommunityContent,
  setCommunityReaction,
  type CommunityCategory,
  type CommunityCommentApi,
  type CommunityPageApi,
  type CommunityPostApi,
  type CommunityPostDetailApi,
  type CommunityReaction,
  type CommunityReportPayload,
  type CommunityReportReason,
  type CreateCommunityPostPayload,
} from "./api";
export { communityReportErrorKey, type CommunityReportErrorKey } from "./reportError";
export {
  COMMUNITY_PAGE_SIZE,
  communityKeys,
  useCommunityFeed,
  useCommunityPost,
  useCommunityReaction,
  useCreateCommunityComment,
  useCreateCommunityPost,
  useReportCommunityContent,
} from "./hooks";

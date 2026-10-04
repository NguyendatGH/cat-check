// features/community — khung M0. Chưa có business logic thật.
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
export {
  createCommunityComment,
  createCommunityPost,
  getCommunityPost,
  listCommunityPosts,
  setCommunityReaction,
  type CommunityPageApi,
  type CommunityCommentApi,
  type CommunityPostDetailApi,
  type CommunityPostApi,
} from "./api";
